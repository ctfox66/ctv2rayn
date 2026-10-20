package com.v2ray.ang.service

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.google.gson.JsonObject

/**
 * Loads and controls the embedded EasyTier mesh VPN dataplane.
 *
 * EasyTier runs in no-tun mode with a local SOCKS5 server; Xray routes mesh
 * network CIDRs to it through the "easytier" socks outbound.
 */
object EasyTierManager {

    private const val MESH_IPV4_WAIT_INTERVAL_MS = 200L
    private const val MESH_IPV4_WAIT_TIMEOUT_MS = 4000L

    private var libraryLoaded = false

    /** Mesh IPv4 addresses (own node plus connected peers) seen after the last start. */
    @Volatile
    private var cachedMeshIpv4s: List<String> = emptyList()

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
        cachedMeshIpv4s = emptyList()
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

    /**
     * Waits a bounded interval for the mesh to assign this node an IPv4 address
     * after [start], then caches the node and peer addresses for
     * [effectiveRouteCidrs]. Skipped for static-IP configs (address already
     * known) and peer-less configs (no DHCP server can answer), so those starts
     * never block. Runs on the daemon IO thread before the Xray config is built.
     */
    fun awaitMeshIpv4s(timeoutMs: Long = MESH_IPV4_WAIT_TIMEOUT_MS) {
        cachedMeshIpv4s = emptyList()
        if (!libraryLoaded || !SettingsManager.isEasyTierEnabled()) {
            return
        }
        if (!SettingsManager.isEasyTierDhcpEnabled()) {
            return
        }
        if (SettingsManager.getEasyTierPeers().isEmpty()) {
            return
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val meshIpv4s = collectMeshIpv4s(getStatusJsonSafe())
            if (meshIpv4s.isNotEmpty()) {
                cachedMeshIpv4s = meshIpv4s
                return
            }
            Thread.sleep(MESH_IPV4_WAIT_INTERVAL_MS)
        }
        LogUtil.w(AppConfig.TAG, "EasyTier mesh IPv4 wait timed out after ${timeoutMs}ms, using configured route CIDRs only")
    }

    /**
     * Route CIDRs effective for the next core start: user-entered CIDRs merged
     * with the /24 of the node's mesh address. DHCP configs use addresses
     * discovered by [awaitMeshIpv4s]; static-IP configs derive it from the
     * persisted address, so no runtime wait is required.
     */
    fun effectiveRouteCidrs(): List<String> {
        val manual = SettingsManager.getEasyTierRouteCidrs()
        val autoIpv4s = if (SettingsManager.isEasyTierDhcpEnabled()) {
            cachedMeshIpv4s
        } else {
            SettingsManager.getEasyTierStaticIpv4().takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
        }
        return mergeRouteCidrs(manual, autoIpv4s)
    }

    /** Extracts the node IPv4 and connected peer IPv4 addresses from a status JSON. */
    private fun collectMeshIpv4s(statusJson: String): List<String> {
        return try {
            val root = JsonUtil.parseString(statusJson) ?: return emptyList()
            val own = root.get("node")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("ipv4")?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf { it.isNotBlank() }
                ?: return emptyList()
            val peerIps = root.get("peers")?.takeIf { it.isJsonArray }?.asJsonArray?.flatMap { element ->
                (element as? JsonObject)?.get("ipv4")?.takeIf { it.isJsonPrimitive }?.asString
                    ?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
            }.orEmpty()
            listOf(own) + peerIps
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "EasyTier mesh IPv4 parse failed", e)
            emptyList()
        }
    }

    /** Derives the /24 network of a dotted IPv4 address; invalid input yields null. */
    fun ipv4ToSlash24(ip: String): String? {
        val parts = ip.trim().substringBefore('/').split('.')
        if (parts.size != 4) {
            return null
        }
        val octets = parts.map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) {
            return null
        }
        return "${octets[0]}.${octets[1]}.${octets[2]}.0/24"
    }

    /** Merges manual CIDRs with /24 networks derived from mesh addresses, deduplicated. */
    fun mergeRouteCidrs(manual: List<String>, meshIpv4s: List<String>): List<String> =
        (manual + meshIpv4s.mapNotNull(::ipv4ToSlash24)).distinct()

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
        appendLine("listeners = []")
        appendLine("socks5_proxy = \"socks5://${AppConfig.LOOPBACK}:$socksPort\"")
        appendLine()
        appendLine("[network_identity]")
        appendLine("network_name = \"${escapeToml(networkName)}\"")
        appendLine("network_secret = \"${escapeToml(networkSecret)}\"")
        peers.forEach { peer ->
            appendLine()
            appendLine("[[peer]]")
            appendLine("uri = \"${escapeToml(peer)}\"")
        }
        appendLine()
        appendLine("[flags]")
        appendLine("no_tun = true")
        appendLine("default_protocol = \"${escapeToml(defaultProtocol)}\"")
        appendLine("enable_ipv6 = $ipv6Enabled")
        appendLine("bind_device = false")
    }
}
