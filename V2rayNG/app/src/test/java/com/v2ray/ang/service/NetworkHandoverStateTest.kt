package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NetworkHandoverStateTest {
    @Test
    fun firstNetworkSelectsWithoutRecoveryAndHandoverRecovers() {
        val state = NetworkHandoverState<String>()
        assertFalse(state.available("wifi")!!.recoverConnection)
        val wifi = state.capabilities("wifi", true)!!
        assertEquals(true, wifi.isWifi)
        assertTrue(state.isLatest(wifi))
        assertNull(state.capabilities("wifi", true))
        val mobile = state.available("mobile")!!
        assertTrue(mobile.recoverConnection)
        assertFalse(state.isLatest(wifi))
        assertEquals(false, state.capabilities("mobile", false)!!.isWifi)
    }

    @Test
    fun oldNetworkLossAndCapabilitiesDoNotOverrideNewNetwork() {
        val state = NetworkHandoverState<String>()
        state.available("wifi")
        val current = state.available("mobile")!!
        assertFalse(state.lost("wifi"))
        assertNull(state.capabilities("wifi", true))
        assertTrue(state.isLatest(current))
    }

    @Test
    fun disconnectAndStopInvalidatePendingWork() {
        val state = NetworkHandoverState<String>()
        val event = state.available("wifi")!!
        assertTrue(state.lost("wifi"))
        assertFalse(state.isLatest(event))
        val recovery = state.available("mobile")!!
        assertTrue(recovery.recoverConnection)
        state.clear()
        assertFalse(state.isLatest(recovery))
        assertFalse(state.isCurrent("mobile"))
        assertNull(state.capabilities("mobile", false))
    }

    @Test
    fun unknownTransportDoesNotSelectWifiOrMobile() {
        val state = NetworkHandoverState<String>()
        val event = state.available("ethernet")!!
        assertNull(event.isWifi)
        assertNull(state.capabilities("ethernet", null))
        assertNull(state.available("ethernet"))
    }
}
