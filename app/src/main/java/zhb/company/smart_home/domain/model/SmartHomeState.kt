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
) {
    fun isNodeOnline(node: Int): Boolean = nodeStatus("node$node") == NodeStatus.ONLINE

    fun nodeStatus(nodeId: String): NodeStatus = nodeHealth[nodeId]?.status ?: NodeStatus.UNKNOWN
}
