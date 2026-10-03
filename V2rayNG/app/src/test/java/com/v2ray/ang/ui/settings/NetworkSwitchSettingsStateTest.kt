package com.v2ray.ang.ui.settings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NetworkSwitchSettingsStateTest {
    @Test
    fun initialStateIsDisabledAndUnbound() {
        val state = NetworkSwitchSettingsState()
        assertFalse(state.enabled)
        assertEquals("", state.wifiGuid)
        assertEquals("", state.cellularGuid)
        assertTrue(state.profiles.isEmpty())
    }

    @Test
    fun selectionsUseGuidAndLeaveOtherBindingUntouched() {
        val state = NetworkSwitchSettingsState(profiles = listOf(
            NetworkSwitchSettingsState.Profile("a", "same name"),
            NetworkSwitchSettingsState.Profile("b", "same name")
        ))
        val wifi = state.apply(NetworkSwitchSettingsAction.Select(true, "a"))!!
        val both = wifi.apply(NetworkSwitchSettingsAction.Select(false, "b"))!!
        assertEquals("a", both.wifiGuid)
        assertEquals("b", both.cellularGuid)
        assertTrue(both.apply(NetworkSwitchSettingsAction.Enable(true))!!.enabled)
        assertFalse(both.apply(NetworkSwitchSettingsAction.Enable(false))!!.enabled)
        assertEquals("", both.apply(NetworkSwitchSettingsAction.Select(true, ""))!!.wifiGuid)
        assertEquals("b", both.apply(NetworkSwitchSettingsAction.Select(true, ""))!!.cellularGuid)
    }

    @Test
    fun missingProfilesAreRejectedWithoutChangingState() {
        val state = NetworkSwitchSettingsState(wifiGuid = "deleted")
        assertNull(state.apply(NetworkSwitchSettingsAction.Select(true, "missing")))
        assertNull(state.apply(NetworkSwitchSettingsAction.Select(false, "missing")))
        assertEquals("deleted", state.wifiGuid)
        assertEquals("", state.apply(NetworkSwitchSettingsAction.Select(true, ""))!!.wifiGuid)
    }
}
