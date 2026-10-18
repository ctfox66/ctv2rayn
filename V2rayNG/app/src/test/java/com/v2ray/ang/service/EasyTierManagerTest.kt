package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EasyTierManagerTest {

    @Test
    fun testEscapeTomlPlainValue() {
        assertEquals("abc123", EasyTierManager.escapeToml("abc123"))
    }

    @Test
    fun testEscapeTomlQuotesAndBackslash() {
        assertEquals("a\\\\b\\\"c", EasyTierManager.escapeToml("a\\b\"c"))
    }

    @Test
    fun testEscapeTomlControlCharactersReplaced() {
        assertEquals("a b", EasyTierManager.escapeToml("a\u0001b"))
        assertEquals("a\\nb", EasyTierManager.escapeToml("a\nb"))
        assertEquals("a\\tb", EasyTierManager.escapeToml("a\tb"))
    }

    @Test
    fun testEscapeTomlKeepsUnicode() {
        assertEquals("节点", EasyTierManager.escapeToml("节点"))
    }

    @Test
    fun testBuildConfigTomlBasic() {
        val toml = EasyTierManager.buildConfigToml(
            networkName = "net1",
            networkSecret = "secret1",
            peers = listOf("tcp://1.2.3.4:11010"),
            socksPort = 10833,
            ipv6Enabled = false
        )
        assertTrue(toml.contains("instance_name = \"v2rayNG\""))
        assertTrue(toml.contains("network_name = \"net1\""))
        assertTrue(toml.contains("network_secret = \"secret1\""))
        assertTrue(toml.contains("listeners = []"))
        assertTrue(toml.contains("uri = \"tcp://1.2.3.4:11010\""))
        assertTrue(toml.contains("socks5_proxy = \"socks5://127.0.0.1:10833\""))
        assertTrue(toml.contains("no_tun = true"))
        assertTrue(toml.contains("enable_ipv6 = false"))
        assertTrue(toml.contains("bind_device = false"))
        assertFalse(toml.contains("[[peer]]\nuri = \"tcp://1.2.3.4:11010\"\nuri"))
    }

    @Test
    fun testBuildConfigTomlNoPeers() {
        val toml = EasyTierManager.buildConfigToml(
            networkName = "net",
            networkSecret = "",
            peers = emptyList(),
            socksPort = 10833,
            ipv6Enabled = true
        )
        assertFalse(toml.contains("[[peer]]"))
        assertTrue(toml.contains("enable_ipv6 = true"))
        assertTrue(toml.contains("network_secret = \"\""))
    }

    @Test
    fun testBuildConfigTomlEscapesUserInput() {
        val toml = EasyTierManager.buildConfigToml(
            networkName = "ne\"t",
            networkSecret = "se\\cret",
            peers = listOf("tcp://host:11010"),
            socksPort = 12345,
            ipv6Enabled = false
        )
        assertTrue(toml.contains("network_name = \"ne\\\"t\""))
        assertTrue(toml.contains("network_secret = \"se\\\\cret\""))
    }

    @Test
    fun testBuildConfigTomlDefaultsKeepLegacyShape() {
        val toml = EasyTierManager.buildConfigToml(
            networkName = "net",
            networkSecret = "",
            peers = emptyList(),
            socksPort = 10833,
            ipv6Enabled = false
        )
        assertTrue(toml.contains("dhcp = true"))
        assertFalse(toml.contains("hostname ="))
        assertFalse(toml.contains("ipv4 ="))
        assertTrue(toml.contains("default_protocol = \"tcp\""))
    }

    @Test
    fun testBuildConfigTomlHostnameAndStaticAddress() {
        val toml = EasyTierManager.buildConfigToml(
            networkName = "net",
            networkSecret = "",
            peers = emptyList(),
            socksPort = 10833,
            ipv6Enabled = false,
            hostname = "my-phone",
            dhcpEnabled = false,
            staticIpv4 = "10.144.144.1/24",
            defaultProtocol = "udp"
        )
        assertTrue(toml.contains("hostname = \"my-phone\""))
        assertTrue(toml.contains("dhcp = false"))
        assertTrue(toml.contains("ipv4 = \"10.144.144.1/24\""))
        assertTrue(toml.contains("default_protocol = \"udp\""))
    }

    @Test
    fun testBuildConfigTomlStaticAddressIgnoredWithDhcp() {
        val toml = EasyTierManager.buildConfigToml(
            networkName = "net",
            networkSecret = "",
            peers = emptyList(),
            socksPort = 10833,
            ipv6Enabled = false,
            dhcpEnabled = true,
            staticIpv4 = "10.144.144.1/24"
        )
        assertFalse(toml.contains("ipv4 ="))
    }

    @Test
    fun testEncodePeersTrimsAndFilters() {
        val encoded = EasyTierManager.encodePeers(listOf(" tcp://a:1 ", "", "  "))
        assertEquals("[\"tcp://a:1\"]", encoded)
    }

    @Test
    fun testDecodePeersRoundTrip() {
        val peers = listOf("tcp://a:1", "udp://b:2")
        val decoded = EasyTierManager.decodePeers(EasyTierManager.encodePeers(peers))
        assertEquals(peers, decoded)
    }

    @Test
    fun testDecodePeersLegacySeparatedFormat() {
        val legacy = "tcp://a:1,udp://b:2\ntcp://c:3;udp://d:4"
        assertEquals(
            listOf("tcp://a:1", "udp://b:2", "tcp://c:3", "udp://d:4"),
            EasyTierManager.decodePeers(legacy)
        )
    }

    @Test
    fun testDecodePeersEmpty() {
        assertTrue(EasyTierManager.decodePeers(null).isEmpty())
        assertTrue(EasyTierManager.decodePeers("").isEmpty())
        assertTrue(EasyTierManager.decodePeers("   ").isEmpty())
    }
}
