package zhb.company.smart_home.domain.model

import zhb.company.smart_home.data.mqtt.NodeHealth
import zhb.company.smart_home.data.mqtt.NodeStatus

/**
 * Single source of truth for every sensor reading / device state in the house,
 * mirroring the Flutter app's `Parameters` ChangeNotifier. Updated exclusively
 * by [zhb.company.smart_home.data.mqtt.SmartHomeRepository] as MQTT messages
 * arrive, and read by every screen ViewModel.
 */
data class SmartHomeState(
    // node1 (fountain, sprinklers): data/garden/Node1Repository; node5 (heating): data/heating/Node5Repository.

    // node2 - yard: outdoor temperature ("" while the node has withdrawn it); lights in data/garden/Node2Repository
    val temperatureOutdoor: String = "--",

    // node3 - main / front door
    val mainDoor: Boolean = false,

    // node4 - ground floor living room
    val temperatureLivingRoomDown: String = "--",
    val humidityLivingRoomDown: String = "--",

    // node5 - heating: see zhb.company.smart_home.data.heating.Node5Repository

    // node6 - pool
    val poolPump: Boolean = false,
    val poolHeating: Boolean = false,
    val temperaturePool: String = "--",

    // node7 - shed
    val doorSmallHome: Boolean = false,
    val bulbSmallHome1: Boolean = false,
    val bulbSmallHome2: Boolean = false,
    val bulbSmallHome3: Boolean = false,
    val fingerSensorStatus: String = "",

    // node8 - first floor living room + AC
    val temperatureLivingRoomUp: String = "--",
    val humidityLivingRoomUp: String = "--",
    val gasLivingRoomUp: String = "--",
    val acMode: AcMode = AcMode.COOL,
    val acFanSpeed: AcFanSpeed = AcFanSpeed.AUTO,
    val acTemp: Int = 22,
    val acOn: Boolean = false,

    // legacy rule: true once a live (non-retained) message arrived from node N since connecting
    val nodeOnline: Map<Int, Boolean> = emptyMap(),

    // nodes with the new firmware (they publish <node>/status + heartbeat), keyed by node id, e.g. "node4"
    val nodeHealth: Map<String, NodeHealth> = emptyMap(),

    // devices currently on, human-readable, used for the Home "devices on" count + GoodNight
    val listPowerDevice: List<String> = emptyList(),
) {
    /** New-firmware status if the node has one, else the legacy rule. */
    fun isNodeOnline(node: Int): Boolean = nodeStatus("node$node") == NodeStatus.ONLINE

    fun nodeStatus(nodeId: String): NodeStatus {
        nodeHealth[nodeId]?.let { return it.status }
        val legacyNumber = LEGACY_NODE_ID.matchEntire(nodeId)?.groupValues?.get(1)?.toInt() ?: return NodeStatus.UNKNOWN
        return if (nodeOnline[legacyNumber] == true) NodeStatus.ONLINE else NodeStatus.UNKNOWN
    }

    private companion object {
        val LEGACY_NODE_ID = Regex("""node(\d)""")
    }
}

enum class AcMode(val code: String) {
    COOL("0"),
    AUTO("8"),
    FAN("4"),
    HEAT("C");

    companion object {
        fun fromCode(code: String): AcMode = entries.firstOrNull { it.code == code } ?: COOL
    }
}

enum class AcFanSpeed(val code: String) {
    AUTO("5"),
    MIN("4"),
    MED("2"),
    MAX("1");

    companion object {
        fun fromCode(code: String): AcFanSpeed = entries.firstOrNull { it.code == code } ?: AUTO
    }
}
