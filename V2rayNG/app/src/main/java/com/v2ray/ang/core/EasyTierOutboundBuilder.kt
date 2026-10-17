package com.v2ray.ang.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig

/**
 * Builds the Xray outbound and routing entries that send mesh network CIDRs
 * to the embedded EasyTier SOCKS5 dataplane.
 */
object EasyTierOutboundBuilder {

    fun buildOutboundTag(): String = AppConfig.EASYTIER_OUTBOUND_TAG

    fun injectIntoV2rayConfig(v2rayConfig: V2rayConfig, socksPort: Int, routeCidrs: List<String>) {
        if (routeCidrs.isEmpty()) {
            return
        }
        val outbound = V2rayConfig.OutboundBean(
            tag = AppConfig.EASYTIER_OUTBOUND_TAG,
            protocol = "socks",
            settings = V2rayConfig.OutboundBean.OutSettingsBean(
                address = AppConfig.LOOPBACK,
                port = socksPort,
                level = AppConfig.DEFAULT_LEVEL
            ),
            mux = null
        )
        v2rayConfig.outbounds.add(outbound)

        val rule = V2rayConfig.RoutingBean.RulesBean(
            ip = routeCidrs,
            outboundTag = AppConfig.EASYTIER_OUTBOUND_TAG
        )
        v2rayConfig.routing.rules.add(0, rule)
    }

    fun injectIntoJson(json: JsonObject, socksPort: Int, routeCidrs: List<String>) {
        if (routeCidrs.isEmpty()) {
            return
        }

        val outbounds = json.get("outbounds")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: return

        val tagExists = outbounds.any { element ->
            element.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get("tag")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString == AppConfig.EASYTIER_OUTBOUND_TAG
        }
        if (!tagExists) {
            outbounds.add(buildOutboundJson(socksPort))
        }

        val routing = json.get("routing")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: JsonObject().also { json.add("routing", it) }
        val rules = routing.get("rules")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: JsonArray().also { routing.add("rules", it) }

        val ruleExists = rules.any { element ->
            element.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get("outboundTag")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString == AppConfig.EASYTIER_OUTBOUND_TAG
        }
        if (!ruleExists) {
            val rule = JsonObject()
            rule.addProperty("type", "field")
            rule.add("ip", JsonArray().apply { routeCidrs.forEach { add(it) } })
            rule.addProperty("outboundTag", AppConfig.EASYTIER_OUTBOUND_TAG)
            rules.add(rule)
        }
    }

    private fun buildOutboundJson(socksPort: Int): JsonObject {
        val settings = JsonObject()
        val servers = JsonArray()
        val server = JsonObject()
        server.addProperty("address", AppConfig.LOOPBACK)
        server.addProperty("ota", false)
        server.addProperty("port", socksPort)
        servers.add(server)
        settings.add("servers", servers)

        val outbound = JsonObject()
        outbound.addProperty("tag", AppConfig.EASYTIER_OUTBOUND_TAG)
        outbound.addProperty("protocol", "socks")
        outbound.add("settings", settings)
        outbound.add("streamSettings", JsonObject())
        return outbound
    }
}
