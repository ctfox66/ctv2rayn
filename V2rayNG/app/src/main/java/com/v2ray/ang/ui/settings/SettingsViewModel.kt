package com.v2ray.ang.ui.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.launch
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : BaseViewModel(application) {

    private val _networkSettings = MutableStateFlow(NetworkSwitchSettingsState())
    val networkSettings = _networkSettings.asStateFlow()

    private val _easyTierSettings = MutableStateFlow(EasyTierSettingsState())
    val easyTierSettings = _easyTierSettings.asStateFlow()

    suspend fun refreshEasyTierSettings() = withContext(Dispatchers.IO) {
        _easyTierSettings.value = EasyTierSettingsState(
            enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_EASYTIER_ENABLED, false),
            networkName = MmkvManager.decodeSettingsString(AppConfig.PREF_EASYTIER_NETWORK_NAME).orEmpty(),
            networkSecret = MmkvManager.decodeSettingsString(AppConfig.PREF_EASYTIER_NETWORK_SECRET).orEmpty(),
            peers = MmkvManager.decodeSettingsString(AppConfig.PREF_EASYTIER_PEERS).orEmpty(),
            socksPort = MmkvManager.decodeSettingsString(AppConfig.PREF_EASYTIER_SOCKS_PORT).orEmpty(),
            routeCidrs = MmkvManager.decodeSettingsString(AppConfig.PREF_EASYTIER_ROUTE_CIDRS).orEmpty()
        )
    }

    fun onEasyTierSettingsAction(action: EasyTierSettingsAction) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (action is EasyTierSettingsAction.UpdateSocksPort &&
                    !isValidEasyTierPortInput(action.value)
                ) {
                    toastError(R.string.toast_invalid_easytier_socks_port)
                    return@withContext
                }
                val updated = _easyTierSettings.value.apply(action)
                val (key, value) = when (action) {
                    is EasyTierSettingsAction.Enable -> AppConfig.PREF_EASYTIER_ENABLED to action.enabled.toString()
                    is EasyTierSettingsAction.UpdateNetworkName -> AppConfig.PREF_EASYTIER_NETWORK_NAME to action.value
                    is EasyTierSettingsAction.UpdateNetworkSecret -> AppConfig.PREF_EASYTIER_NETWORK_SECRET to action.value
                    is EasyTierSettingsAction.UpdatePeers -> AppConfig.PREF_EASYTIER_PEERS to action.value
                    is EasyTierSettingsAction.UpdateSocksPort -> AppConfig.PREF_EASYTIER_SOCKS_PORT to action.value
                    is EasyTierSettingsAction.UpdateRouteCidrs -> AppConfig.PREF_EASYTIER_ROUTE_CIDRS to action.value
                }
                try {
                    val saved = if (action is EasyTierSettingsAction.Enable) {
                        MmkvManager.encodeSettings(key, action.enabled)
                    } else {
                        MmkvManager.encodeSettings(key, value)
                    }
                    check(saved) { "MMKV write failed" }
                    _easyTierSettings.value = updated
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Settings: save easytier setting failed", e)
                    toastError(R.string.toast_easytier_settings_failed)
                }
            }
        }
    }

    private fun isValidEasyTierPortInput(value: String): Boolean {
        if (value.isBlank()) return true
        val port = value.trim().toIntOrNull() ?: return false
        return port in 1..65535
    }

    suspend fun refreshNetworkSettings() = withContext(Dispatchers.IO) {
        try {
            val profiles = MmkvManager.decodeAllServerList().mapNotNull { guid ->
                MmkvManager.decodeServerConfig(guid)?.takeIf(SettingsManager::isNetworkSwitchProfile)
                    ?.let { NetworkSwitchSettingsState.Profile(guid, it.remarks) }
            }
            _networkSettings.value = NetworkSwitchSettingsState(
                enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_NETWORK_AUTO_SWITCH),
                wifiGuid = MmkvManager.decodeSettingsString(AppConfig.PREF_WIFI_SERVER).orEmpty(),
                cellularGuid = MmkvManager.decodeSettingsString(AppConfig.PREF_CELLULAR_SERVER).orEmpty(),
                profiles = profiles
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Settings: load network bindings failed", e)
            toastError(R.string.toast_network_settings_failed)
        }
    }

    fun onNetworkSettingsAction(action: NetworkSwitchSettingsAction) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val state = _networkSettings.value
                val updated = state.apply(action) ?: return@withContext
                val (key, value) = when (action) {
                    is NetworkSwitchSettingsAction.Enable -> AppConfig.PREF_NETWORK_AUTO_SWITCH to action.enabled.toString()
                    is NetworkSwitchSettingsAction.Select ->
                        (if (action.wifi) AppConfig.PREF_WIFI_SERVER else AppConfig.PREF_CELLULAR_SERVER) to action.guid
                }
                try {
                    val saved = if (action is NetworkSwitchSettingsAction.Enable) {
                        MmkvManager.encodeSettings(key, action.enabled)
                    } else {
                        MmkvManager.encodeSettings(key, value)
                    }
                    check(saved) { "MMKV write failed" }
                    _networkSettings.value = updated
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Settings: save network binding failed", e)
                    toastError(R.string.toast_network_settings_failed)
                }
            }
        }
    }

    private val _systemVpnSettingsAvailable = MutableStateFlow(false)
    val systemVpnSettingsAvailable = _systemVpnSettingsAvailable.asStateFlow()

    suspend fun refreshSystemVpnSettingsAvailability() {
        _systemVpnSettingsAvailable.value = withContext(Dispatchers.IO) {
            // Android exposes the VPN page, not a direct link to the Always-on VPN switch.
            Intent(Settings.ACTION_VPN_SETTINGS).resolveActivity(getApplication<Application>().packageManager) != null
        }
    }

    /**
     * Checks for root access and requests it if necessary.
     * Updates [isLoading] during the process.
     */
    fun checkAndRequestRoot(onSuccess: () -> Unit) {
        launchLoading {
            val hasRoot = withContext(Dispatchers.IO) {
                RootManager.refresh()
            }
            if (hasRoot) {
                onSuccess()
            } else {
                toastError(R.string.toast_root_required)
            }
        }
    }

    /**
     * Validates if the given string is a valid observatory duration.
     * Shows error toast if invalid.
     * @return The trimmed value if valid, null otherwise.
     */
    fun validateObservatoryDuration(value: String): String? {
        val duration = value.trim()
        return if (AppConfig.OBSERVATORY_DURATION_PATTERN.matches(duration)) {
            duration
        } else {
            toastError(R.string.toast_invalid_observatory_duration)
            null
        }
    }

    /**
     * Validates if the given string is a valid observatory sampling value.
     * Shows error toast if invalid.
     * @return The value if valid, null otherwise.
     */
    fun validateObservatorySampling(value: String): String? {
        val sampling = value.trim().toIntOrNull()?.takeIf { it > 0 }
        return if (sampling != null) {
            sampling.toString()
        } else {
            toastError(R.string.toast_invalid_observatory_sampling)
            null
        }
    }
}
