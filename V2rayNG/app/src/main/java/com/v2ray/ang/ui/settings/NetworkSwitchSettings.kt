package com.v2ray.ang.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

@Composable
fun NetworkSwitchSettings(state: NetworkSwitchSettingsState, onAction: (NetworkSwitchSettingsAction) -> Unit) {
    Text(stringResource(R.string.title_network_switch), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = state.enabled,
            role = Role.Switch,
            onValueChange = { onAction(NetworkSwitchSettingsAction.Enable(it)) }
        ).padding(16.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.title_network_switch))
            Text(stringResource(R.string.summary_network_switch), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = state.enabled, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
    }
    NetworkNodeSetting(true, state.wifiGuid, state, onAction)
    NetworkNodeSetting(false, state.cellularGuid, state, onAction)
}

@Composable
private fun NetworkNodeSetting(wifi: Boolean, selected: String, state: NetworkSwitchSettingsState, onAction: (NetworkSwitchSettingsAction) -> Unit) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(if (wifi) R.string.title_wifi_node else R.string.title_cellular_node)
    val unset = stringResource(R.string.network_node_unset)
    val name = if (selected.isEmpty()) unset else state.profiles.find { it.guid == selected }?.name
        ?: stringResource(R.string.network_node_missing)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(name) },
        modifier = Modifier.clickable(role = Role.Button) { showDialog = true }
    )
    if (showDialog) {
        val options = listOf(NetworkSwitchSettingsState.Profile("", unset)) + state.profiles
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                LazyColumn {
                    items(options, key = { it.guid }) { profile ->
                        Text(
                            profile.name,
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = selected == profile.guid,
                                role = Role.RadioButton,
                                onClick = {
                                    onAction(NetworkSwitchSettingsAction.Select(wifi, profile.guid))
                                    showDialog = false
                                }
                            ).padding(16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}
