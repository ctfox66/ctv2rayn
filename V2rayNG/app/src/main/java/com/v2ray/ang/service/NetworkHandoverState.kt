package com.v2ray.ang.service

/** Pure event ordering owned by NetworkMonitor; callers serialize access. */
internal class NetworkHandoverState<N> {
    data class Event(val generation: Long, val isWifi: Boolean?, val recoverConnection: Boolean)

    private var network: N? = null
    private var generation = 0L
    private var seenNetwork = false
    private var isWifi: Boolean? = null
    private var recoverConnection = false

    fun available(value: N): Event? {
        if (network == value) return null
        network = value
        isWifi = null
        recoverConnection = seenNetwork
        seenNetwork = true
        return Event(++generation, null, recoverConnection)
    }

    fun capabilities(value: N, wifi: Boolean?): Event? {
        if (!isCurrent(value) || isWifi == wifi) return null
        isWifi = wifi
        return Event(++generation, wifi, recoverConnection)
    }

    fun isCurrent(value: N): Boolean = network == value
    fun isLatest(event: Event): Boolean = network != null && event.generation == generation

    fun lost(value: N): Boolean {
        if (!isCurrent(value)) return false
        network = null
        generation++
        return true
    }

    fun clear() {
        network = null
        seenNetwork = false
        isWifi = null
        generation++
    }
}
