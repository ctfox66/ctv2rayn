package com.v2ray.ang.ui.settings

/** GUID-based settings decisions; persistence belongs to SettingsViewModel. */
data class NetworkSwitchSettingsState(
    val enabled: Boolean = false,
    val wifiGuid: String = "",
    val cellularGuid: String = "",
    val profiles: List<Profile> = emptyList()
) {
    data class Profile(val guid: String, val name: String)

    fun apply(action: NetworkSwitchSettingsAction): NetworkSwitchSettingsState? = when (action) {
        is NetworkSwitchSettingsAction.Enable -> copy(enabled = action.enabled)
        is NetworkSwitchSettingsAction.Select -> {
            if (action.guid.isNotEmpty() && profiles.none { it.guid == action.guid }) null
            else if (action.wifi) copy(wifiGuid = action.guid) else copy(cellularGuid = action.guid)
        }
    }
}

sealed interface NetworkSwitchSettingsAction {
    data class Enable(val enabled: Boolean) : NetworkSwitchSettingsAction
    data class Select(val wifi: Boolean, val guid: String) : NetworkSwitchSettingsAction
}
