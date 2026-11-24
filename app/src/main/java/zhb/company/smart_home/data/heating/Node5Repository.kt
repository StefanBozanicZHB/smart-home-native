package zhb.company.smart_home.data.heating

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.data.node.NodeChannel
import zhb.company.smart_home.data.node.NodeCommandClient
import zhb.company.smart_home.domain.heating.Hysteresis
import zhb.company.smart_home.domain.heating.Node5Config
import zhb.company.smart_home.domain.heating.Node5Json
import zhb.company.smart_home.domain.heating.Node5LiveState
import zhb.company.smart_home.domain.heating.Pipe
import zhb.company.smart_home.domain.heating.PipeAssignment
import zhb.company.smart_home.domain.heating.PipeSensors
import zhb.company.smart_home.domain.heating.PumpEvent
import zhb.company.smart_home.domain.heating.SafeModeState
import zhb.company.smart_home.domain.heating.ScheduleSegment
import zhb.company.smart_home.domain.heating.ZoneSettings

/** Everything the app knows about Node 5, built from its retained state topics. */
data class Node5Snapshot(
    val nodeId: String = Node5Topics.REAL,
    val config: Node5Config? = null,
    val zones: Map<String, ZoneSettings> = emptyMap(),
    val live: Node5LiveState? = null,
    val pump: PumpEvent? = null,
    val safeMode: SafeModeState? = null,
    /** `sensors`: pipe sensor assignment and sensors on the bus (firmware with pipe sensors). */
    val sensors: PipeSensors? = null,
)

/**
 * Node 5 heating API on top of the shared MQTT connection: keeps [snapshot] up to date from
 * the node's retained topics and sends commands, matching each answer on `result` by `id`.
 */
class Node5Repository(
    private val mqtt: SmartHomeRepository,
    scope: CoroutineScope,
    commandTimeoutMs: Long = 10_000,
    newId: () -> String = NodeCommandClient.defaultIdGenerator(),
) {
    private val _snapshot = MutableStateFlow(Node5Snapshot())
    val snapshot: StateFlow<Node5Snapshot> = _snapshot.asStateFlow()

    private val topics = Node5Topics()

    private val channel = NodeChannel(mqtt, scope, topics.base, commandTimeoutMs, newId, ::onMessage)
    private val commands = channel.commands

    suspend fun requestState() = channel.requestState()

    suspend fun setMainSwitch(on: Boolean): CommandOutcome =
        commands.send(topics.mainSwitchSet) { id -> Node5Json.mainSwitchCommand(on, id) }

    /** Sends only the parts that are not null; the node keeps the rest. */
    suspend fun updateZone(
        zone: String,
        hysteresis: Hysteresis? = null,
        workday: List<ScheduleSegment>? = null,
        weekend: List<ScheduleSegment>? = null,
    ): CommandOutcome = commands.send(topics.zoneSet(zone)) { id -> Node5Json.zoneSetCommand(id, hysteresis, workday, weekend) }

    /** Assigns, moves or removes pipe sensors; pipes not in [changes] keep their sensor. */
    suspend fun updateSensors(changes: Map<Pipe, PipeAssignment?>): CommandOutcome =
        commands.send(topics.sensorsSet) { id -> Node5Json.sensorsSetCommand(id, changes) }

    /** Called on the MQTT client's thread with each Node 5 message ([path] relative to its base). */
    private fun onMessage(path: String, payload: String) {
        when {
            path == Node5Topics.CONFIG -> Node5Json.parseConfig(payload)?.let { c -> _snapshot.update { it.copy(config = c) } }
            path == Node5Topics.STATE -> Node5Json.parseState(payload)?.let { s -> _snapshot.update { it.copy(live = s) } }
            path == Node5Topics.PUMP -> Node5Json.parsePump(payload)?.let { p -> _snapshot.update { it.copy(pump = p) } }
            path == Node5Topics.SAFE_MODE -> Node5Json.parseSafeMode(payload)?.let { m -> _snapshot.update { it.copy(safeMode = m) } }
            path == Node5Topics.SENSORS -> Node5Json.parseSensors(payload)?.let { s -> _snapshot.update { it.copy(sensors = s) } }
            path.startsWith(Node5Topics.ZONES_PREFIX) && !path.endsWith("/set") -> {
                val zone = path.removePrefix(Node5Topics.ZONES_PREFIX)
                if (zone.isNotEmpty() && '/' !in zone) {
                    Node5Json.parseZone(payload)?.let { z -> _snapshot.update { it.copy(zones = it.zones + (zone to z)) } }
                }
            }
        }
    }
}
