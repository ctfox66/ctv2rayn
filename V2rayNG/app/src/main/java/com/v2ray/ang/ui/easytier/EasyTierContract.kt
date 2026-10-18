package com.v2ray.ang.ui.easytier

import com.google.gson.JsonObject
import com.v2ray.ang.util.JsonUtil

/** One mesh member shown on the EasyTier status tab; fields are display-ready. */
data class EasyTierPeerItem(
    val peerId: Long = 0,
    val hostname: String = "",
    val ipv4: String? = null,
    val cost: Int = 0,
    val latencyMs: Int = 0,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val tunnelType: String? = null
)

/** Snapshot of the EasyTier dataplane reported by the daemon as JSON. */
data class EasyTierStatus(
    val running: Boolean = false,
    val version: String = "",
    val networkName: String = "",
    val hostname: String = "",
    val ipv4: String? = null,
    val peers: List<EasyTierPeerItem> = emptyList()
) {
    companion object {
        fun fromJson(json: String): EasyTierStatus = runCatching {
            val root = JsonUtil.parseString(json) ?: return EasyTierStatus()
            val node = root.getAsJsonObject("node")
            val peers = root.getAsJsonArray("peers")?.mapNotNull { element ->
                (element as? JsonObject)?.let { peer ->
                    EasyTierPeerItem(
                        peerId = peer.get("peerId")?.asLong ?: 0,
                        hostname = peer.get("hostname")?.asString.orEmpty(),
                        ipv4 = peer.get("ipv4")?.takeIf { !it.isJsonNull }?.asString,
                        cost = peer.get("cost")?.asInt ?: 0,
                        latencyMs = peer.get("latencyMs")?.asInt ?: 0,
                        rxBytes = peer.get("rxBytes")?.asLong ?: 0,
                        txBytes = peer.get("txBytes")?.asLong ?: 0,
                        tunnelType = peer.get("tunnelType")?.takeIf { !it.isJsonNull }?.asString
                    )
                }
            }.orEmpty()
            EasyTierStatus(
                running = root.get("running")?.asBoolean == true,
                version = root.get("version")?.asString.orEmpty(),
                networkName = root.get("networkName")?.asString.orEmpty(),
                hostname = node?.get("hostname")?.asString.orEmpty(),
                ipv4 = node?.get("ipv4")?.takeIf { !it.isJsonNull }?.asString,
                peers = peers
            )
        }.getOrDefault(EasyTierStatus())
    }
}

/** Durable EasyTier configuration edited on the config tab. */
data class EasyTierConfigState(
    val enabled: Boolean = false,
    val networkName: String = "",
    val networkSecret: String = "",
    val peers: List<String> = emptyList(),
    val socksPort: String = "",
    val routeCidrs: String = "",
    val hostname: String = "",
    val dhcpEnabled: Boolean = true,
    val staticIpv4: String = "",
    val defaultProtocol: String = "tcp"
) {
    fun apply(action: EasyTierConfigAction): EasyTierConfigState = when (action) {
        is EasyTierConfigAction.Enable -> copy(enabled = action.enabled)
        is EasyTierConfigAction.UpdateNetworkName -> copy(networkName = action.value)
        is EasyTierConfigAction.UpdateNetworkSecret -> copy(networkSecret = action.value)
        is EasyTierConfigAction.UpdatePeers -> copy(peers = action.peers)
        is EasyTierConfigAction.UpdateSocksPort -> copy(socksPort = action.value)
        is EasyTierConfigAction.UpdateRouteCidrs -> copy(routeCidrs = action.value)
        is EasyTierConfigAction.UpdateHostname -> copy(hostname = action.value)
        is EasyTierConfigAction.UpdateDhcp -> copy(dhcpEnabled = action.enabled)
        is EasyTierConfigAction.UpdateStaticIpv4 -> copy(staticIpv4 = action.value)
        is EasyTierConfigAction.UpdateDefaultProtocol -> copy(defaultProtocol = action.value)
    }

    companion object {
        /** A port entry is valid when blank or a 1..65535 number. */
        fun isValidPortInput(value: String): Boolean {
            if (value.isBlank()) return true
            val port = value.trim().toIntOrNull() ?: return false
            return port in 1..65535
        }

        /** A peer entry needs a scheme such as tcp://host:port; bare hosts are rejected. */
        fun isValidPeerInput(value: String): Boolean {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return false
            return trimmed.contains("://") && !trimmed.contains(' ')
        }

        /** Static IPv4 accepts blank or an address optionally in CIDR notation. */
        fun isValidIpv4Input(value: String): Boolean {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return true
            val cidr = Regex("""^(\d{1,3}(?:\.\d{1,3}){3})(?:/(\d{1,2}))?$""")
            val match = cidr.matchEntire(trimmed) ?: return false
            val octets = match.groupValues[1].split('.').map { it.toInt() }
            if (octets.any { it > 255 }) return false
            val prefix = match.groupValues[2]
            return prefix.isEmpty() || prefix.toInt() <= 32
        }
    }
}

/** User operations on the EasyTier config tab. */
sealed interface EasyTierConfigAction {
    data class Enable(val enabled: Boolean) : EasyTierConfigAction
    data class UpdateNetworkName(val value: String) : EasyTierConfigAction
    data class UpdateNetworkSecret(val value: String) : EasyTierConfigAction
    data class UpdatePeers(val peers: List<String>) : EasyTierConfigAction
    data class UpdateSocksPort(val value: String) : EasyTierConfigAction
    data class UpdateRouteCidrs(val value: String) : EasyTierConfigAction
    data class UpdateHostname(val value: String) : EasyTierConfigAction
    data class UpdateDhcp(val enabled: Boolean) : EasyTierConfigAction
    data class UpdateStaticIpv4(val value: String) : EasyTierConfigAction
    data class UpdateDefaultProtocol(val value: String) : EasyTierConfigAction
}
