use std::io;
use std::sync::{Arc, Mutex, OnceLock};

use easytier::common::config::TomlConfigLoader;
use easytier::instance::factory::{create_native_instance, NativeCoreInstance};
use easytier::socket_protector::{set_native_socket_protector, NativeSocketProtector};
use easytier_core::instance::CoreInstanceState;
use jni::objects::{JClass, JString, JValue};
use jni::sys::{jboolean, jstring, JNI_FALSE, JNI_TRUE};
use jni::{JNIEnv, JavaVM};

mod logger;

const CLASS_PATH: &str = "com/v2ray/ang/service/EasyTierManager";
const RUNNER_INSTANCE_NAME: &str = "v2rayNG";

static RUNTIME: OnceLock<tokio::runtime::Runtime> = OnceLock::new();
static INSTANCE: Mutex<Option<Arc<NativeCoreInstance>>> = Mutex::new(None);

struct JniSocketProtector {
    java_vm: JavaVM,
}

#[async_trait::async_trait]
impl NativeSocketProtector for JniSocketProtector {
    async fn protect(&self, socket_handle: u64) -> io::Result<()> {
        let fd = i32::try_from(socket_handle).map_err(|_| {
            io::Error::new(io::ErrorKind::InvalidInput, "socket handle out of range")
        })?;
        let mut env = self
            .java_vm
            .attach_current_thread()
            .map_err(|e| io::Error::other(format!("attach jvm thread failed: {e}")))?;
        let class = env
            .find_class(CLASS_PATH)
            .map_err(|e| io::Error::other(format!("find {CLASS_PATH} failed: {e}")))?;
        let result = env
            .call_static_method(class, "protectSocket", "(I)Z", &[JValue::from(fd)])
            .map_err(|e| io::Error::other(format!("call protectSocket failed: {e}")))?;
        let ok = result
            .z()
            .map_err(|e| io::Error::other(format!("read protectSocket result failed: {e}")))?;
        if ok {
            Ok(())
        } else {
            Err(io::Error::new(
                io::ErrorKind::PermissionDenied,
                "vpn protect returned false",
            ))
        }
    }
}

fn install_socket_protector(env: &mut JNIEnv) -> anyhow::Result<()> {
    let java_vm = env.get_java_vm()?;
    set_native_socket_protector(Some(Arc::new(JniSocketProtector { java_vm })));
    Ok(())
}

fn stop_locked(guard: &mut Option<Arc<NativeCoreInstance>>) {
    if let Some(instance) = guard.take() {
        if let Some(runtime) = RUNTIME.get() {
            runtime.block_on(async move {
                instance.stop().await;
            });
        }
    }
}

fn throw_runtime_exception(env: &mut JNIEnv, message: &str) {
    let _ = env.throw_new("java/lang/RuntimeException", message);
}

#[no_mangle]
pub extern "system" fn Java_com_v2ray_ang_service_EasyTierManager_easyTierStart(
    mut env: JNIEnv,
    _class: JClass,
    toml: JString,
) -> jboolean {
    logger::init();
    let result = (|| -> anyhow::Result<()> {
        let toml_str: String = env
            .get_string(&toml)
            .map_err(|e| anyhow::anyhow!("read config string failed: {e}"))?
            .into();
        let config = TomlConfigLoader::new_from_str(&toml_str)?;
        install_socket_protector(&mut env)?;
        let runtime = RUNTIME.get_or_init(|| {
            tokio::runtime::Builder::new_multi_thread()
                .worker_threads(2)
                .enable_all()
                .build()
                .expect("tokio runtime for easytier-jni")
        });
        let mut guard = INSTANCE
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        stop_locked(&mut guard);
        let instance = create_native_instance(config)?;
        runtime.block_on(instance.start())?;
        *guard = Some(instance);
        Ok(())
    })();
    match result {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            log::error!("easytier start failed: {e:#}");
            throw_runtime_exception(&mut env, &format!("easytier start failed: {e:#}"));
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_v2ray_ang_service_EasyTierManager_easyTierStop(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    logger::init();
    let mut guard = INSTANCE
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    stop_locked(&mut guard);
    set_native_socket_protector(None);
    JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_com_v2ray_ang_service_EasyTierManager_easyTierIsRunning(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let guard = INSTANCE
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    match guard.as_ref() {
        Some(instance) => (instance.state() == CoreInstanceState::Running) as jboolean,
        None => JNI_FALSE,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_v2ray_ang_service_EasyTierManager_easyTierGetVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    logger::init();
    let version = easytier::VERSION;
    match env.new_string(version) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_v2ray_ang_service_EasyTierManager_easyTierGetInstanceName(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    match env.new_string(RUNNER_INSTANCE_NAME) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn build_status_json(instance: &Arc<NativeCoreInstance>) -> String {
    let runtime = match RUNTIME.get() {
        Some(runtime) => runtime,
        None => return serde_json::json!({ "running": false }).to_string(),
    };
    runtime.block_on(async {
        let peer_manager = instance.peer_manager();
        let my_peer_id = peer_manager.my_peer_id();
        let routes = peer_manager.list_route_snapshots().await;
        let peers = peer_manager.list_peer_snapshots().await;

        let mut node = serde_json::json!({ "peerId": my_peer_id });
        let mut peer_entries = Vec::new();
        for route in &routes {
            let ipv4 = route
                .ipv4_addr
                .as_ref()
                .and_then(|inet| inet.address.as_ref())
                .map(|addr| addr.to_string());
            if route.peer_id == my_peer_id {
                node = serde_json::json!({
                    "peerId": my_peer_id,
                    "hostname": route.hostname,
                    "ipv4": ipv4,
                    "version": route.version,
                });
                continue;
            }
            let mut rx_bytes = 0u64;
            let mut tx_bytes = 0u64;
            let mut tunnel_type: Option<String> = None;
            if let Some(snapshot) = peers.iter().find(|p| p.peer_id == route.peer_id) {
                for conn in &snapshot.conns {
                    if conn.is_closed {
                        continue;
                    }
                    if let Some(stats) = conn.stats.as_ref() {
                        rx_bytes += stats.rx_bytes;
                        tx_bytes += stats.tx_bytes;
                    }
                    if tunnel_type.is_none() {
                        tunnel_type = conn.tunnel.as_ref().map(|t| t.tunnel_type.clone());
                    }
                }
            }
            peer_entries.push(serde_json::json!({
                "peerId": route.peer_id,
                "hostname": route.hostname,
                "ipv4": ipv4,
                "cost": route.cost,
                "latencyMs": route.path_latency,
                "rxBytes": rx_bytes,
                "txBytes": tx_bytes,
                "tunnelType": tunnel_type,
            }));
        }
        serde_json::json!({
            "running": instance.state() == CoreInstanceState::Running,
            "version": easytier::VERSION,
            "networkName": peer_manager.network_name(),
            "node": node,
            "peers": peer_entries,
        })
        .to_string()
    })
}

#[no_mangle]
pub extern "system" fn Java_com_v2ray_ang_service_EasyTierManager_easyTierGetStatusJson(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    logger::init();
    let guard = INSTANCE
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let status = match guard.as_ref() {
        Some(instance) => build_status_json(instance),
        None => serde_json::json!({ "running": false }).to_string(),
    };
    match env.new_string(status) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
