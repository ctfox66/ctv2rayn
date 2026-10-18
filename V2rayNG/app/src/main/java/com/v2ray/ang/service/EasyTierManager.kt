package com.v2ray.ang.service

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil

/**
 * Loads and controls the embedded EasyTier mesh VPN dataplane.
 *
 * EasyTier runs in no-tun mode with a local SOCKS5 server; Xray routes mesh
 * network CIDRs to it through the "easytier" socks outbound.
 */
object EasyTierManager {

    private var libraryLoaded = false

    @JvmStatic
    external fun easyTierStart(toml: String): Boolean

    @JvmStatic
    external fun easyTierStop(): Boolean

    @JvmStatic
    external fun easyTierIsRunning(): Boolean

    @JvmStatic
    external fun easyTierGetVersion(): String

    @JvmStatic
    external fun easyTierGetStatusJson(): String

    @JvmStatic
    fun protectSocket(fd: Int): Boolean {
        return try {
            CoreServiceManager.serviceControl?.get()?.vpnProtect(fd) ?: false
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "EasyTier protect socket failed fd=$fd", e)
            false
        }
    }

    /**
     * Starts the EasyTier dataplane when enabled and configured. Must be called
     * from the daemon process after the active service has registered its
     * [com.v2ray.ang.contracts.ServiceControl].
     */
    fun start() {
        if (!SettingsManager.isEasyTierEnabled()) {
            return
        }
        val networkName = SettingsManager.getEasyTierNetworkName()
        if (networkName.isEmpty()) {
            LogUtil.w(AppConfig.TAG, "EasyTier start skipped: network name is empty")
            return
        }
        val toml = buildConfigToml(
            networkName = networkName,
            networkSecret = SettingsManager.getEasyTierNetworkSecret(),
            peers = SettingsManager.getEasyTierPeers(),
            socksPort = SettingsManager.getEasyTierSocksPort(),
            ipv6Enabled = SettingsManager.isEasyTierIpv6Enabled(),
            hostname = SettingsManager.getEasyTierHostname(),
            dhcpEnabled = SettingsManager.isEasyTierDhcpEnabled(),
            staticIpv4 = SettingsManager.getEasyTierStaticIpv4(),
            defaultProtocol = SettingsManager.getEasyTierDefaultProtocol()
        )
        LogUtil.i(AppConfig.TAG, "EasyTier start: peers=${SettingsManager.getEasyTierPeers().size} socksPort=${SettingsManager.getEasyTierSocksPort()}")
        try {
            ensureLoaded()
            if (!easyTierStart(toml)) {
                LogUtil.e(AppConfig.TAG, "EasyTier start failed")
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "EasyTier start exception", e)
        }
    }

    /** Stops the EasyTier dataplane. Safe to call when it is not running. */
    fun stop() {
        if (!libraryLoaded) {
            return
        }
        try {
            easyTierStop()
            LogUtil.i(AppConfig.TAG, "EasyTier stopped")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "EasyTier stop exception", e)
        }
    }

    /**
     * Returns the EasyTier status JSON from the daemon process. The native
     * library is never loaded here, so a call from the UI process degrades to
     * a not-running payload instead of loading the core into that process.
     */
    fun getStatusJsonSafe(): String = try {
        if (!libraryLoaded) {
            "{\"running\":false}"
        } else {
            easyTierGetStatusJson()
        }
    } catch (e: Throwable) {
        LogUtil.e(AppConfig.TAG, "EasyTier status query failed", e)
        "{\"running\":false}"
    }

    private fun ensureLoaded() {
        if (!libraryLoaded) {
            System.loadLibrary("easytier")
            libraryLoaded = true
        }
    }

    fun escapeToml(value: String): String = buildString {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code >= 0x20 && ch.code != 0x7f) append(ch) else append(' ')
            }
        }
    }

    /**
     * Encodes the peer list as a JSON array for persistence. The stored format
     * used to be comma/newline separated text; [decodePeers] still reads it.
     */
    fun encodePeers(peers: List<String>): String =
        JsonUtil.toJson(peers.map { it.trim() }.filter { it.isNotEmpty() })

    /**
     * Decodes persisted peers. JSON arrays are the current format; a leading
     * '['-less value is the legacy comma/newline/semicolon separated format.
     */
    fun decodePeers(raw: String?): List<String> {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        if (trimmed.startsWith("[")) {
            val parsed = runCatching {
                JsonUtil.fromJson(trimmed, Array<String>::class.java)?.toList()
            }.getOrNull()
            return parsed.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        }
        return trimmed.split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun buildConfigToml(
        networkName: String,
        networkSecret: String,
        peers: List<String>,
        socksPort: Int,
        ipv6Enabled: Boolean,
        hostname: String = "",
        dhcpEnabled: Boolean = true,
        staticIpv4: String = "",
        defaultProtocol: String = AppConfig.EASYTIER_DEFAULT_PROTOCOL
    ): String = buildString {
        appendLine("instance_name = \"v2rayNG\"")
        if (hostname.isNotBlank()) {
            appendLine("hostname = \"${escapeToml(hostname)}\"")
        }
        appendLine("dhcp = $dhcpEnabled")
        if (!dhcpEnabled && staticIpv4.isNotBlank()) {
            appendLine("ipv4 = \"${escapeToml(staticIpv4)}\"")
        }
        appendLine()
        appendLine("[network_identity]")
        appendLine("network_name = \"${escapeToml(networkName)}\"")
        appendLine("network_secret = \"${escapeToml(networkSecret)}\"")
        appendLine()
        appendLine("listeners = []")
        peers.forEach { peer ->
            appendLine()
            appendLine("[[peer]]")
            appendLine("uri = \"${escapeToml(peer)}\"")
        }
        appendLine()
        appendLine("socks5_proxy = \"socks5://${AppConfig.LOOPBACK}:$socksPort\"")
        appendLine()
        appendLine("[flags]")
        appendLine("no_tun = true")
        appendLine("default_protocol = \"${escapeToml(defaultProtocol)}\"")
        appendLine("enable_ipv6 = $ipv6Enabled")
        appendLine("bind_device = false")
    }
}
