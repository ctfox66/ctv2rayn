package com.v2ray.ang.service

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owns debounced underlying-network events, never native-core work. */
class NetworkMonitor(
    private val connectivity: ConnectivityManager,
    private val onUnderlyingNetworksChanged: (Array<Network>?) -> Unit,
    private val onHandover: (Boolean?, Boolean) -> Unit,
) {
    private companion object {
        const val HANDOVER_DEBOUNCE_MS = 1000L
    }

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = NetworkHandoverState<Network>()
    private var handoverJob: Job? = null
    private var registered = false

    private val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .build()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = synchronized(lock) {
            if (!registered) return@synchronized
            onUnderlyingNetworksChanged(arrayOf(network))
            schedule(state.available(network))
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = synchronized(lock) {
            if (!registered || !state.isCurrent(network)) return@synchronized
            val isWifi = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> false
                else -> null
            }
            schedule(state.capabilities(network, isWifi))
        }

        override fun onLost(network: Network) = synchronized(lock) {
            if (!registered || !state.lost(network)) return@synchronized
            handoverJob?.cancel()
            onUnderlyingNetworksChanged(null)
        }
    }

    fun register() = synchronized(lock) {
        if (registered) return@synchronized
        try {
            registered = true
            // Android 7+ default-network callbacks include this app's VPN. A non-VPN request
            // selects the carrying network instead; remove when Android offers a default
            // underlying-network callback on every supported API.
            connectivity.requestNetwork(request, callback)
        } catch (e: Exception) {
            registered = false
            LogUtil.e(AppConfig.TAG, "NetworkMonitor: request underlying network failed", e)
        }
    }

    /** The lock only guards event dispatch; the handler must enqueue rather than block. */
    fun unregister() = synchronized(lock) {
        val wasRegistered = registered
        registered = false
        state.clear()
        scope.cancel()
        handoverJob = null
        if (!wasRegistered) return@synchronized
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "NetworkMonitor: unregister underlying network failed", e)
        }
    }

    private fun schedule(event: NetworkHandoverState.Event?) {
        if (event == null) return
        handoverJob?.cancel()
        handoverJob = scope.launch {
            delay(HANDOVER_DEBOUNCE_MS)
            synchronized(lock) {
                if (registered && state.isLatest(event)) {
                    onHandover(event.isWifi, event.recoverConnection)
                }
            }
        }
    }
}
