package com.v2ray.ang.ui.easytier

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.service.EasyTierManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EasyTierViewModel(application: Application) : BaseViewModel(application) {

    private val _config = MutableStateFlow(EasyTierConfigState())
    val config = _config.asStateFlow()

    private val _status = MutableStateFlow(EasyTierStatus())
    val status = _status.asStateFlow()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getIntExtra("key", 0) != AppConfig.MSG_EASYTIER_STATUS) return
            val json = intent.getStringExtra("content") ?: return
            _status.value = EasyTierStatus.fromJson(json)
        }
    }

    fun registerStatusReceiver(context: Context) {
        ContextCompat.registerReceiver(
            context,
            statusReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            com.v2ray.ang.util.Utils.receiverFlags()
        )
    }

    fun unregisterStatusReceiver(context: Context) {
        runCatching { context.unregisterReceiver(statusReceiver) }
            .onFailure { LogUtil.e(AppConfig.TAG, "Failed to unregister easytier status receiver", it) }
    }

    /** Asks the running daemon for an immediate status broadcast. */
    fun requestStatus() {
        MessageHelperSender.requestStatus(app)
    }

    suspend fun refreshConfig() = withContext(Dispatchers.IO) {
        _config.value = EasyTierConfigState(
            enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_EASYTIER_ENABLED, false),
            networkName = MmkvManager.decodeSettingsString(AppConfig.PREF_EASYTIER_NETWORK_NAME).orEmpty(),
            networkSecret = MmkvManager.decodeSettingsString(AppConfig.PREF_EASYTIER_NETWORK_SECRET).orEmpty(),
            peers = EasyTierManager.decodePeers(MmkvManager.decodeSettingsString(AppConfig.PREF_EASYTIER_PEERS)),
            socksPort = MmkvManager.decodeSettingsString(AppConfig.PREF_EASYTIER_SOCKS_PORT).orEmpty(),
            routeCidrs = MmkvManager.decodeSettingsString(AppConfig.PREF_EASYTIER_ROUTE_CIDRS).orEmpty(),
            hostname = MmkvManager.decodeSettingsString(AppConfig.PREF_EASYTIER_HOSTNAME).orEmpty(),
            dhcpEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_EASYTIER_DHCP, true),
            staticIpv4 = MmkvManager.decodeSettingsString(AppConfig.PREF_EASYTIER_STATIC_IPV4).orEmpty(),
            defaultProtocol = MmkvManager.decodeSettingsString(AppConfig.PREF_EASYTIER_DEFAULT_PROTOCOL)
                ?.takeIf { it.isNotBlank() } ?: AppConfig.EASYTIER_DEFAULT_PROTOCOL
        )
    }

    fun onConfigAction(action: EasyTierConfigAction) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (action is EasyTierConfigAction.UpdateSocksPort &&
                    !EasyTierConfigState.isValidPortInput(action.value)
                ) {
                    toastError(R.string.toast_invalid_easytier_socks_port)
                    return@withContext
                }
                if (action is EasyTierConfigAction.UpdateStaticIpv4 &&
                    !EasyTierConfigState.isValidIpv4Input(action.value)
                ) {
                    toastError(R.string.toast_invalid_easytier_ipv4)
                    return@withContext
                }
                val updated = _config.value.apply(action)
                val saved = when (action) {
                    is EasyTierConfigAction.Enable ->
                        MmkvManager.encodeSettings(AppConfig.PREF_EASYTIER_ENABLED, action.enabled)
                    is EasyTierConfigAction.UpdateNetworkName ->
                        MmkvManager.encodeSettings(AppConfig.PREF_EASYTIER_NETWORK_NAME, action.value)
                    is EasyTierConfigAction.UpdateNetworkSecret ->
                        MmkvManager.encodeSettings(AppConfig.PREF_EASYTIER_NETWORK_SECRET, action.value)
                    is EasyTierConfigAction.UpdatePeers ->
                        MmkvManager.encodeSettings(AppConfig.PREF_EASYTIER_PEERS, EasyTierManager.encodePeers(action.peers))
                    is EasyTierConfigAction.UpdateSocksPort ->
                        MmkvManager.encodeSettings(AppConfig.PREF_EASYTIER_SOCKS_PORT, action.value)
                    is EasyTierConfigAction.UpdateRouteCidrs ->
                        MmkvManager.encodeSettings(AppConfig.PREF_EASYTIER_ROUTE_CIDRS, action.value)
                    is EasyTierConfigAction.UpdateHostname ->
                        MmkvManager.encodeSettings(AppConfig.PREF_EASYTIER_HOSTNAME, action.value)
                    is EasyTierConfigAction.UpdateDhcp ->
                        MmkvManager.encodeSettings(AppConfig.PREF_EASYTIER_DHCP, action.enabled)
                    is EasyTierConfigAction.UpdateStaticIpv4 ->
                        MmkvManager.encodeSettings(AppConfig.PREF_EASYTIER_STATIC_IPV4, action.value)
                    is EasyTierConfigAction.UpdateDefaultProtocol ->
                        MmkvManager.encodeSettings(AppConfig.PREF_EASYTIER_DEFAULT_PROTOCOL, action.value)
                }
                if (saved) {
                    _config.value = updated
                } else {
                    LogUtil.e(AppConfig.TAG, "EasyTier: save setting failed action=$action")
                    toastError(R.string.toast_easytier_settings_failed)
                }
            }
        }
    }
}

/** Isolated so tests do not need the Android broadcast stack. */
internal object MessageHelperSender {
    fun requestStatus(context: Context) {
        com.v2ray.ang.helper.MessageHelper.sendMsg2Service(
            context,
            AppConfig.MSG_EASYTIER_STATUS_REQUEST,
            ""
        )
    }
}
