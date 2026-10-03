package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NetworkSwitchSettingsTest {
    @Test
    fun ordinaryProfilesPreserveSharedRouting() {
        EConfigType.entries.forEach { type ->
            val expected = type !in setOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN)
            assertEquals(expected, SettingsManager.isNetworkSwitchProfile(ProfileItem(configType = type)))
        }
    }

    @Test
    fun browserDialerProfilesAreNotEligibleForInPlaceSwitching() {
        assertFalse(SettingsManager.isNetworkSwitchProfile(ProfileItem(configType = EConfigType.VLESS, browserDialerMode = "webview")))
        assertTrue(SettingsManager.isNetworkSwitchProfile(ProfileItem(configType = EConfigType.VLESS, browserDialerMode = "")))
    }
}
