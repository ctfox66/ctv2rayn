package com.v2ray.ang.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EasyTierOutboundBuilderTest {

    private fun buildV2rayConfig(): V2rayConfig {
        val routing = V2rayConfig.RoutingBean(
            domainStrategy = "AsIs",
            rules = arrayListOf(
                V2rayConfig.RoutingBean.RulesBean(
                    ip = listOf("geoip:private"),
                    outboundTag = AppConfig.TAG_DIRECT
                )
            )
        )
        return V2rayConfig(
            log = V2rayConfig.LogBean(),
            inbounds = arrayListOf(),
            outbounds = arrayListOf(
                V2rayConfig.OutboundBean(tag = "proxy", protocol = "vmess")
            ),
            routing = routing
        )
    }

    @Test
    fun testInjectIntoV2rayConfigAddsOutboundAndRule() {
        val config = buildV2rayConfig()
        EasyTierOutboundBuilder.injectIntoV2rayConfig(config, 10833, listOf("10.0.0.0/8"))

        assertEquals(2, config.outbounds.size)
        val outbound = config.outbounds.last()
        assertEquals(AppConfig.EASYTIER_OUTBOUND_TAG, outbound.tag)
        assertEquals("socks", outbound.protocol)
        assertEquals(AppConfig.LOOPBACK, outbound.settings?.address)
        assertEquals(10833, outbound.settings?.port)

        assertEquals(2, config.routing.rules.size)
        val rule = config.routing.rules.first()
        assertEquals(AppConfig.EASYTIER_OUTBOUND_TAG, rule.outboundTag)
        assertEquals(listOf("10.0.0.0/8"), rule.ip)
    }

    @Test
    fun testInjectIntoV2rayConfigRuleTakesPrecedenceOverDirect() {
        val config = buildV2rayConfig()
        EasyTierOutboundBuilder.injectIntoV2rayConfig(config, 10833, listOf("10.0.0.0/8"))
        val first = config.routing.rules.first()
        val second = config.routing.rules[1]
        assertEquals(AppConfig.EASYTIER_OUTBOUND_TAG, first.outboundTag)
        assertEquals(AppConfig.TAG_DIRECT, second.outboundTag)
    }

    @Test
    fun testInjectIntoV2rayConfigSkipsEmptyCidrs() {
        val config = buildV2rayConfig()
        EasyTierOutboundBuilder.injectIntoV2rayConfig(config, 10833, emptyList())
        assertEquals(1, config.outbounds.size)
        assertEquals(1, config.routing.rules.size)
    }

    @Test
    fun testInjectIntoJsonAddsOutboundAndRule() {
        val json = JsonObject().apply {
            add("outbounds", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("tag", "proxy")
                    addProperty("protocol", "vmess")
                })
            })
            add("routing", JsonObject().apply {
                add("rules", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("type", "field")
                        addProperty("outboundTag", "direct")
                    })
                })
            })
        }
        EasyTierOutboundBuilder.injectIntoJson(json, 10833, listOf("10.0.0.0/8"))

        val outbounds = json.getAsJsonArray("outbounds")
        assertEquals(2, outbounds.size())
        val outbound = outbounds.get(1).asJsonObject
        assertEquals(AppConfig.EASYTIER_OUTBOUND_TAG, outbound.get("tag").asString)
        assertEquals("socks", outbound.get("protocol").asString)
        val servers = outbound.getAsJsonObject("settings").getAsJsonArray("servers")
        assertEquals(1, servers.size())
        assertEquals(AppConfig.LOOPBACK, servers.get(0).asJsonObject.get("address").asString)
        assertEquals(10833, servers.get(0).asJsonObject.get("port").asInt)

        val rules = json.getAsJsonObject("routing").getAsJsonArray("rules")
        assertEquals(2, rules.size())
        assertEquals(AppConfig.EASYTIER_OUTBOUND_TAG, rules.get(1).asJsonObject.get("outboundTag").asString)
    }

    @Test
    fun testInjectIntoJsonIdempotent() {
        val json = JsonObject().apply {
            add("outbounds", JsonArray())
        }
        EasyTierOutboundBuilder.injectIntoJson(json, 10833, listOf("10.0.0.0/8"))
        EasyTierOutboundBuilder.injectIntoJson(json, 10833, listOf("10.0.0.0/8"))

        assertEquals(1, json.getAsJsonArray("outbounds").size())
        assertEquals(1, json.getAsJsonObject("routing").getAsJsonArray("rules").size())
    }

    @Test
    fun testInjectIntoJsonWithoutRoutingCreatesIt() {
        val json = JsonObject().apply {
            add("outbounds", JsonArray().apply {
                add(JsonObject().apply { addProperty("tag", "proxy") })
            })
        }
        EasyTierOutboundBuilder.injectIntoJson(json, 10833, listOf("10.0.0.0/8"))

        val routing = json.getAsJsonObject("routing")
        assertNotNull(routing)
        val rules = routing.getAsJsonArray("rules")
        assertEquals(1, rules.size())
    }

    @Test
    fun testInjectIntoJsonSkipsEmptyCidrsAndMissingOutbounds() {
        val json = JsonObject()
        EasyTierOutboundBuilder.injectIntoJson(json, 10833, emptyList())
        assertFalse(json.has("outbounds"))
        assertFalse(json.has("routing"))

        val json2 = JsonObject()
        EasyTierOutboundBuilder.injectIntoJson(json2, 10833, listOf("10.0.0.0/8"))
        assertFalse(json2.has("routing"))
    }

    @Test
    fun testInjectIntoJsonParsesRealisticCustomConfig() {
        val raw = """
            {
              "outbounds": [{"tag": "proxy", "protocol": "vmess"}],
              "routing": {"domainStrategy": "AsIs", "rules": []}
            }
        """.trimIndent()
        val json = JsonParser.parseString(raw).asJsonObject
        EasyTierOutboundBuilder.injectIntoJson(json, 10833, listOf("10.0.0.0/8", "172.16.0.0/12"))
        val rule = json.getAsJsonObject("routing").getAsJsonArray("rules").get(0).asJsonObject
        assertEquals("field", rule.get("type").asString)
        val ipArray = rule.getAsJsonArray("ip")
        assertEquals(2, ipArray.size())
        assertEquals("10.0.0.0/8", ipArray.get(0).asString)
        assertEquals("172.16.0.0/12", ipArray.get(1).asString)
    }
}
