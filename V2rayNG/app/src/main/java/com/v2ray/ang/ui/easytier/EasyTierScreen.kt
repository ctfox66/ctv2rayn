package com.v2ray.ang.ui.easytier

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.CollapsiblePreferenceGroupHeader
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsEditItem
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.verticalScrollbar

private val easytierProtocols = listOf("tcp", "udp", "ws", "wss")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyTierScreen(
    viewModel: EasyTierViewModel,
    onBackClick: () -> Unit
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val statusReceived by viewModel.statusReceived.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_easytier_settings),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.easytier_tab_config)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.easytier_tab_status)) }
                )
            }
            when (selectedTab) {
                0 -> ConfigTab(config = config, onAction = viewModel::onConfigAction)
                else -> StatusTab(status = status, statusReceived = statusReceived)
            }
        }
    }
}

@Composable
private fun ConfigTab(
    config: EasyTierConfigState,
    onAction: (EasyTierConfigAction) -> Unit
) {
    val scrollState = rememberScrollState()
    var identityExpanded by rememberSaveable { mutableStateOf(true) }
    var addressExpanded by rememberSaveable { mutableStateOf(true) }
    var peersExpanded by rememberSaveable { mutableStateOf(true) }
    var integrationExpanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
            .imePadding()
    ) {
        CollapsiblePreferenceGroupHeader(
            title = stringResource(R.string.easytier_group_network_identity),
            expanded = identityExpanded,
            onExpandedChange = { identityExpanded = it }
        )
        if (identityExpanded) {
            SettingsSwitchItem(
                title = stringResource(R.string.title_pref_easytier_enabled),
                summary = stringResource(R.string.summary_pref_easytier_enabled),
                checked = config.enabled,
                onCheckedChange = { onAction(EasyTierConfigAction.Enable(it)) }
            )
            SettingsEditItem(
                title = stringResource(R.string.title_pref_easytier_network_name),
                value = config.networkName,
                enabled = config.enabled,
                onValueChanged = { onAction(EasyTierConfigAction.UpdateNetworkName(it)) }
            )
            SettingsEditItem(
                title = stringResource(R.string.title_pref_easytier_network_secret),
                value = config.networkSecret,
                enabled = config.enabled,
                isPassword = true,
                onValueChanged = { onAction(EasyTierConfigAction.UpdateNetworkSecret(it)) }
            )
        }

        CollapsiblePreferenceGroupHeader(
            title = stringResource(R.string.easytier_group_virtual_ip),
            expanded = addressExpanded,
            onExpandedChange = { addressExpanded = it }
        )
        if (addressExpanded) {
            SettingsSwitchItem(
                title = stringResource(R.string.title_pref_easytier_dhcp),
                summary = stringResource(R.string.summary_pref_easytier_dhcp),
                checked = config.dhcpEnabled,
                enabled = config.enabled,
                onCheckedChange = { onAction(EasyTierConfigAction.UpdateDhcp(it)) }
            )
            SettingsEditItem(
                title = stringResource(R.string.title_pref_easytier_static_ipv4),
                value = config.staticIpv4,
                enabled = config.enabled && !config.dhcpEnabled,
                onValueChanged = { onAction(EasyTierConfigAction.UpdateStaticIpv4(it)) }
            )
            SettingsEditItem(
                title = stringResource(R.string.title_pref_easytier_hostname),
                value = config.hostname,
                enabled = config.enabled,
                onValueChanged = { onAction(EasyTierConfigAction.UpdateHostname(it)) }
            )
            SettingsListItem(
                title = stringResource(R.string.title_pref_easytier_default_protocol),
                entries = easytierProtocols,
                values = easytierProtocols,
                selectedValue = config.defaultProtocol,
                enabled = config.enabled,
                onSelected = { onAction(EasyTierConfigAction.UpdateDefaultProtocol(it)) }
            )
        }

        CollapsiblePreferenceGroupHeader(
            title = stringResource(R.string.easytier_group_peers),
            expanded = peersExpanded,
            onExpandedChange = { peersExpanded = it }
        )
        if (peersExpanded) {
            PeerListSection(peers = config.peers, enabled = config.enabled, onAction = onAction)
        }

        CollapsiblePreferenceGroupHeader(
            title = stringResource(R.string.easytier_group_integration),
            expanded = integrationExpanded,
            onExpandedChange = { integrationExpanded = it }
        )
        if (integrationExpanded) {
            SettingsEditItem(
                title = stringResource(R.string.title_pref_easytier_socks_port),
                value = config.socksPort,
                enabled = config.enabled,
                keyboardNumber = true,
                onValueChanged = { onAction(EasyTierConfigAction.UpdateSocksPort(it)) }
            )
            SettingsEditItem(
                title = stringResource(R.string.title_pref_easytier_route_cidrs),
                value = config.routeCidrs,
                enabled = config.enabled,
                onValueChanged = { onAction(EasyTierConfigAction.UpdateRouteCidrs(it)) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        NavigationBarsSpacer()
    }
}

@Composable
private fun PeerListSection(
    peers: List<String>,
    enabled: Boolean,
    onAction: (EasyTierConfigAction) -> Unit
) {
    val context = LocalContext.current
    var newPeer by rememberSaveable { mutableStateOf("") }

    peers.forEach { peer ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = peer,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onAction(EasyTierConfigAction.UpdatePeers(peers - peer)) },
                enabled = enabled
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24dp),
                    contentDescription = stringResource(R.string.easytier_remove_peer)
                )
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = newPeer,
            onValueChange = { newPeer = it },
            placeholder = { Text(stringResource(R.string.easytier_add_peer_hint)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = {
                val candidate = newPeer.trim()
                if (EasyTierConfigState.isValidPeerInput(candidate)) {
                    onAction(EasyTierConfigAction.UpdatePeers(peers + candidate))
                    newPeer = ""
                } else {
                    context.toastError(R.string.toast_invalid_easytier_peer)
                }
            },
            enabled = enabled
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add_24dp),
                contentDescription = stringResource(R.string.easytier_add_peer)
            )
        }
    }
}

@Composable
private fun StatusTab(
    status: EasyTierStatus,
    statusReceived: Boolean
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = stringResource(
                if (status.running) R.string.easytier_node_status_running
                else R.string.easytier_node_status_stopped
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (status.running) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
        )
        if (!status.running) {
            Text(
                text = stringResource(R.string.easytier_not_running_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
            )
        }
        StatusRow(
            label = stringResource(R.string.easytier_status_ipv4),
            value = status.ipv4 ?: "-"
        )
        StatusRow(
            label = stringResource(R.string.easytier_status_hostname),
            value = status.hostname.ifBlank { "-" }
        )
        StatusRow(
            label = stringResource(R.string.easytier_status_network),
            value = status.networkName.ifBlank { "-" }
        )
        StatusRow(
            label = stringResource(R.string.easytier_status_version),
            value = status.version.ifBlank { "-" }
        )

        if (status.running) {
            AppDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.easytier_group_peers),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
            if (status.peers.isEmpty()) {
                Text(
                    text = stringResource(R.string.easytier_no_peers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
                )
            } else {
                status.peers.forEach { peer ->
                    PeerStatusRow(peer = peer)
                    AppDivider()
                }
            }
        }

        if (!statusReceived) {
            Text(
                text = stringResource(R.string.easytier_not_running_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        NavigationBarsSpacer()
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun PeerStatusRow(peer: EasyTierPeerItem) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = peer.hostname.ifBlank { "#${peer.peerId}" },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    if (peer.cost <= 1) R.string.easytier_peer_p2p
                    else R.string.easytier_peer_relay
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row {
            Text(
                text = peer.ipv4 ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (peer.latencyMs >= 0) "${peer.latencyMs} ms" else "-",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row {
            Text(
                text = stringResource(R.string.easytier_status_rx) + " " +
                    android.text.format.Formatter.formatFileSize(context, peer.rxBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.easytier_status_tx) + " " +
                    android.text.format.Formatter.formatFileSize(context, peer.txBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!peer.tunnelType.isNullOrEmpty()) {
            Text(
                text = peer.tunnelType,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
