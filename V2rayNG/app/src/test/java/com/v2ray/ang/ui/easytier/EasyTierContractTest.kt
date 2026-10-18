package com.v2ray.ang.ui.easytier

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EasyTierContractTest {

    @Test
    fun testInitialState() {
        val state = EasyTierConfigState()
        assertFalse(state.enabled)
        assertEquals("", state.networkName)
        assertEquals("", state.networkSecret)
        assertTrue(state.peers.isEmpty())
        assertEquals("", state.socksPort)
        assertEquals("", state.routeCidrs)
        assertEquals("", state.hostname)
        assertTrue(state.dhcpEnabled)
        assertEquals("", state.staticIpv4)
        assertEquals("tcp", state.defaultProtocol)
    }

    @Test
    fun testApplyAllActions() {
        var state = EasyTierConfigState()
        state = state.apply(EasyTierConfigAction.Enable(true))
        state = state.apply(EasyTierConfigAction.UpdateNetworkName("net"))
        state = state.apply(EasyTierConfigAction.UpdateNetworkSecret("secret"))
        state = state.apply(EasyTierConfigAction.UpdatePeers(listOf("tcp://1.2.3.4:11010")))
        state = state.apply(EasyTierConfigAction.UpdateSocksPort("10833"))
        state = state.apply(EasyTierConfigAction.UpdateRouteCidrs("10.0.0.0/8"))
        state = state.apply(EasyTierConfigAction.UpdateHostname("phone"))
        state = state.apply(EasyTierConfigAction.UpdateDhcp(false))
        state = state.apply(EasyTierConfigAction.UpdateStaticIpv4("10.144.144.1/24"))
        state = state.apply(EasyTierConfigAction.UpdateDefaultProtocol("udp"))

        assertTrue(state.enabled)
        assertEquals("net", state.networkName)
        assertEquals("secret", state.networkSecret)
        assertEquals(listOf("tcp://1.2.3.4:11010"), state.peers)
        assertEquals("10833", state.socksPort)
        assertEquals("10.0.0.0/8", state.routeCidrs)
        assertEquals("phone", state.hostname)
        assertFalse(state.dhcpEnabled)
        assertEquals("10.144.144.1/24", state.staticIpv4)
        assertEquals("udp", state.defaultProtocol)
    }

    @Test
    fun testApplyKeepsOtherFields() {
        val state = EasyTierConfigState(enabled = true, networkName = "keep")
        val updated = state.apply(EasyTierConfigAction.UpdateDhcp(false))
        assertTrue(updated.enabled)
        assertEquals("keep", updated.networkName)
        assertFalse(updated.dhcpEnabled)
    }

    @Test
    fun testIsValidPortInput() {
        assertTrue(EasyTierConfigState.isValidPortInput(""))
        assertTrue(EasyTierConfigState.isValidPortInput("10833"))
        assertFalse(EasyTierConfigState.isValidPortInput("0"))
        assertFalse(EasyTierConfigState.isValidPortInput("65536"))
        assertFalse(EasyTierConfigState.isValidPortInput("abc"))
    }

    @Test
    fun testIsValidPeerInput() {
        assertTrue(EasyTierConfigState.isValidPeerInput("tcp://1.2.3.4:11010"))
        assertTrue(EasyTierConfigState.isValidPeerInput("udp://example.com:11010"))
        assertFalse(EasyTierConfigState.isValidPeerInput("1.2.3.4:11010"))
        assertFalse(EasyTierConfigState.isValidPeerInput("tcp://a b:11010"))
        assertFalse(EasyTierConfigState.isValidPeerInput("  "))
    }

    @Test
    fun testIsValidIpv4Input() {
        assertTrue(EasyTierConfigState.isValidIpv4Input(""))
        assertTrue(EasyTierConfigState.isValidIpv4Input("10.144.144.1"))
        assertTrue(EasyTierConfigState.isValidIpv4Input("10.144.144.1/24"))
        assertFalse(EasyTierConfigState.isValidIpv4Input("256.1.1.1"))
        assertFalse(EasyTierConfigState.isValidIpv4Input("10.0.0.0/33"))
        assertFalse(EasyTierConfigState.isValidIpv4Input("abc"))
        assertFalse(EasyTierConfigState.isValidIpv4Input("10.0.0"))
    }

    @Test
    fun testStatusFromJsonFull() {
        val json = """
            {"running":true,"version":"2.7.0","networkName":"net",
             "node":{"peerId":1,"hostname":"phone","ipv4":"10.126.126.1","version":"2.7.0"},
             "peers":[{"peerId":2,"hostname":"pc","ipv4":"10.126.126.2","cost":1,"latencyMs":3,
                       "rxBytes":100,"txBytes":200,"tunnelType":"udp"}]}
        """.trimIndent()
        val status = EasyTierStatus.fromJson(json)
        assertTrue(status.running)
        assertEquals("2.7.0", status.version)
        assertEquals("net", status.networkName)
        assertEquals("phone", status.hostname)
        assertEquals("10.126.126.1", status.ipv4)
        assertEquals(1, status.peers.size)
        val peer = status.peers[0]
        assertEquals("pc", peer.hostname)
        assertEquals("10.126.126.2", peer.ipv4)
        assertEquals(1, peer.cost)
        assertEquals(3, peer.latencyMs)
        assertEquals(100L, peer.rxBytes)
        assertEquals(200L, peer.txBytes)
        assertEquals("udp", peer.tunnelType)
    }

    @Test
    fun testStatusFromJsonNotRunning() {
        val status = EasyTierStatus.fromJson("{\"running\":false}")
        assertFalse(status.running)
        assertEquals("", status.version)
        assertTrue(status.peers.isEmpty())
    }

    @Test
    fun testStatusFromJsonNullFields() {
        val json = """
            {"running":true,"node":{"hostname":"h","ipv4":null},
             "peers":[{"peerId":5,"hostname":"","ipv4":null,"cost":2,"latencyMs":-1,
                       "rxBytes":0,"txBytes":0,"tunnelType":null}]}
        """.trimIndent()
        val status = EasyTierStatus.fromJson(json)
        assertTrue(status.running)
        assertNull(status.ipv4)
        assertEquals("h", status.hostname)
        assertEquals(1, status.peers.size)
        assertNull(status.peers[0].ipv4)
        assertNull(status.peers[0].tunnelType)
        assertEquals(-1, status.peers[0].latencyMs)
    }

    @Test
    fun testStatusFromJsonInvalid() {
        val status = EasyTierStatus.fromJson("not json")
        assertNotNull(status)
        assertFalse(status.running)
        assertTrue(status.peers.isEmpty())
    }
}
