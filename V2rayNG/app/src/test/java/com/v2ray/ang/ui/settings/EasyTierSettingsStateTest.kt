package com.v2ray.ang.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EasyTierSettingsStateTest {

    @Test
    fun testInitialState() {
        val state = EasyTierSettingsState()
        assertFalse(state.enabled)
        assertEquals("", state.networkName)
        assertEquals("", state.networkSecret)
        assertEquals("", state.peers)
        assertEquals("", state.socksPort)
        assertEquals("", state.routeCidrs)
    }

    @Test
    fun testApplyEnable() {
        val state = EasyTierSettingsState()
        val enabled = state.apply(EasyTierSettingsAction.Enable(true))
        assertTrue(enabled.enabled)
        assertFalse(state.enabled)
    }

    @Test
    fun testApplyAllFieldUpdates() {
        var state = EasyTierSettingsState()
        state = state.apply(EasyTierSettingsAction.UpdateNetworkName("net"))
        state = state.apply(EasyTierSettingsAction.UpdateNetworkSecret("secret"))
        state = state.apply(EasyTierSettingsAction.UpdatePeers("tcp://1.2.3.4:11010"))
        state = state.apply(EasyTierSettingsAction.UpdateSocksPort("10833"))
        state = state.apply(EasyTierSettingsAction.UpdateRouteCidrs("10.0.0.0/8"))

        assertEquals("net", state.networkName)
        assertEquals("secret", state.networkSecret)
        assertEquals("tcp://1.2.3.4:11010", state.peers)
        assertEquals("10833", state.socksPort)
        assertEquals("10.0.0.0/8", state.routeCidrs)
        assertFalse(state.enabled)
    }

    @Test
    fun testApplyKeepsOtherFields() {
        val state = EasyTierSettingsState(enabled = true, networkName = "keep")
        val updated = state.apply(EasyTierSettingsAction.UpdatePeers("peer"))
        assertTrue(updated.enabled)
        assertEquals("keep", updated.networkName)
        assertEquals("peer", updated.peers)
    }

    @Test
    fun testApplyEmptyValues() {
        val state = EasyTierSettingsState(networkName = "x", socksPort = "10833")
        val cleared = state.apply(EasyTierSettingsAction.UpdateNetworkName(""))
        assertEquals("", cleared.networkName)
        assertEquals("10833", cleared.socksPort)
    }
}
