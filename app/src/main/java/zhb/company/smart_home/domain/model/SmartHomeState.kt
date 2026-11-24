package zhb.company.smart_home.domain.model

import zhb.company.smart_home.data.mqtt.NodeHealth
import zhb.company.smart_home.data.mqtt.NodeStatus

/**
 * Live node health and the plain measurements, updated by
 * [zhb.company.smart_home.data.mqtt.SmartHomeRepository]. Nodes with a JSON API keep their state
 * in their own repositories (data/garden, data/door, data/heating).
 * A measurement is "--" until it arrives and "" while the node has withdrawn it.
 */
data class SmartHomeState(
    // node2 - yard
    val temperatureOutdoor: String = "--",

    // node4 - living room, ground floor
    val temperatureLivingRoomDown: String = "--",
    val humidityLivingRoomDown: String = "--",

    // node6 - pool
    val temperaturePool: String = "--",

    // node8 - living room, upper floor
    val temperatureLivingRoomUp: String = "--",
    val humidityLivingRoomUp: String = "--",

    // keyed by node id, e.g. "node4"
    val nodeHealth: Map<String, NodeHealth> = emptyMap(),

    /** When each measurement last arrived (phone clock, ms). */
    val readingAt: Map<Reading, Long> = emptyMap(),

    /**
     * Measurements on screen that are no longer current: nothing new for [Reading.STALE_AFTER_MS]
     * (e.g. a sensor stopped and its withdrawal was missed while the app was paused) or their node
     * is offline. They stay visible, marked with the time of their last update.
     */
    val staleReadings: Set<Reading> = emptySet(),
) {
    fun isNodeOnline(node: Int): Boolean = nodeStatus("node$node") == NodeStatus.ONLINE

    fun nodeStatus(nodeId: String): NodeStatus = nodeHealth[nodeId]?.status ?: NodeStatus.UNKNOWN

    /** Last update of [reading] when it is stale, else null. */
    fun staleSince(reading: Reading): Long? = if (reading in staleReadings) readingAt[reading] else null

    /** [staleReadings] as of [nowMs]; only readings that have arrived can be stale. */
    fun computeStale(nowMs: Long): Set<Reading> = readingAt.filter { (reading, at) ->
        nowMs - at > Reading.STALE_AFTER_MS || nodeStatus(reading.node) == NodeStatus.OFFLINE
    }.keys
}

/** The plain measurements in [SmartHomeState] and the node that sends each. */
enum class Reading(val node: String) {
    OUTDOOR_TEMPERATURE("node2"),
    GROUND_TEMPERATURE("node4"),
    GROUND_HUMIDITY("node4"),
    POOL_TEMPERATURE("node6"),
    UPPER_TEMPERATURE("node8"),
    UPPER_HUMIDITY("node8"),
    ;

    companion object {
        /**
         * The nodes publish every 10-30 s (`docs/mqtt_topics.md`); three minutes without a new
         * value is the same limit after which a node counts as offline.
         */
        const val STALE_AFTER_MS = 3 * 60_000L
    }
}
