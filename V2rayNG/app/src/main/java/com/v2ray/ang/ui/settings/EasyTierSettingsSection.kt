package com.v2ray.ang.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.CollapsiblePreferenceGroupHeader
import com.v2ray.ang.ui.compose.SettingsEditItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem

@Composable
fun EasyTierSettingsSection(
    state: EasyTierSettingsState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAction: (EasyTierSettingsAction) -> Unit
) {
    CollapsiblePreferenceGroupHeader(
        title = stringResource(R.string.title_easytier_settings),
        expanded = expanded,
        onExpandedChange = onExpandedChange
    )
    if (expanded) {
        SettingsSwitchItem(
            title = stringResource(R.string.title_pref_easytier_enabled),
            summary = stringResource(R.string.summary_pref_easytier_enabled),
            checked = state.enabled,
            onCheckedChange = { onAction(EasyTierSettingsAction.Enable(it)) }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_easytier_network_name),
            value = state.networkName,
            enabled = state.enabled,
            onValueChanged = { onAction(EasyTierSettingsAction.UpdateNetworkName(it)) }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_easytier_network_secret),
            value = state.networkSecret,
            enabled = state.enabled,
            isPassword = true,
            onValueChanged = { onAction(EasyTierSettingsAction.UpdateNetworkSecret(it)) }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_easytier_peers),
            value = state.peers,
            enabled = state.enabled,
            onValueChanged = { onAction(EasyTierSettingsAction.UpdatePeers(it)) }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_easytier_socks_port),
            value = state.socksPort,
            enabled = state.enabled,
            keyboardNumber = true,
            onValueChanged = { onAction(EasyTierSettingsAction.UpdateSocksPort(it)) }
        )
        SettingsEditItem(
            title = stringResource(R.string.title_pref_easytier_route_cidrs),
            value = state.routeCidrs,
            enabled = state.enabled,
            onValueChanged = { onAction(EasyTierSettingsAction.UpdateRouteCidrs(it)) }
        )
    }
}
