package com.v2ray.ang.ui.settings

/** EasyTier mesh VPN settings state; persistence belongs to SettingsViewModel. */
data class EasyTierSettingsState(
    val enabled: Boolean = false,
    val networkName: String = "",
    val networkSecret: String = "",
    val peers: String = "",
    val socksPort: String = "",
    val routeCidrs: String = ""
) {
    fun apply(action: EasyTierSettingsAction): EasyTierSettingsState = when (action) {
        is EasyTierSettingsAction.Enable -> copy(enabled = action.enabled)
        is EasyTierSettingsAction.UpdateNetworkName -> copy(networkName = action.value)
        is EasyTierSettingsAction.UpdateNetworkSecret -> copy(networkSecret = action.value)
        is EasyTierSettingsAction.UpdatePeers -> copy(peers = action.value)
        is EasyTierSettingsAction.UpdateSocksPort -> copy(socksPort = action.value)
        is EasyTierSettingsAction.UpdateRouteCidrs -> copy(routeCidrs = action.value)
    }
}

sealed interface EasyTierSettingsAction {
    data class Enable(val enabled: Boolean) : EasyTierSettingsAction
    data class UpdateNetworkName(val value: String) : EasyTierSettingsAction
    data class UpdateNetworkSecret(val value: String) : EasyTierSettingsAction
    data class UpdatePeers(val value: String) : EasyTierSettingsAction
    data class UpdateSocksPort(val value: String) : EasyTierSettingsAction
    data class UpdateRouteCidrs(val value: String) : EasyTierSettingsAction
}
