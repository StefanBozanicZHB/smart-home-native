package zhb.company.smart_home.data.heating

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.MqttQualityOfService
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.domain.heating.Hysteresis
import zhb.company.smart_home.domain.heating.Node5CommandResult
import zhb.company.smart_home.domain.heating.Node5Config
import zhb.company.smart_home.domain.heating.Node5Json
import zhb.company.smart_home.domain.heating.Node5LiveState
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
)

sealed interface CommandOutcome {
    data class Accepted(val rev: Long?) : CommandOutcome
    data class Rejected(val error: String) : CommandOutcome
    data object Timeout : CommandOutcome
    data object NotConnected : CommandOutcome
}

/**
 * Node 5 heating API on top of the shared MQTT connection: keeps [snapshot] up to date from
 * the node's retained topics and sends commands, matching each answer on `result` by `id`.
 *
 * The node id ([nodeIds]: `node5` or `testnode5`) can change at runtime; the snapshot is then
 * cleared and the new node is asked to republish its state (`config/get`).
 */
class Node5Repository(
    private val mqtt: SmartHomeRepository,
    nodeIds: Flow<String>,
    scope: CoroutineScope,
    private val commandTimeoutMs: Long = 10_000,
    private val newId: () -> String = defaultIdGenerator(),
) {
    private val _snapshot = MutableStateFlow(Node5Snapshot())
    val snapshot: StateFlow<Node5Snapshot> = _snapshot.asStateFlow()

    @Volatile
    private var topics = Node5Topics(Node5Topics.REAL)

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Node5CommandResult>>()

    init {
        mqtt.addMessageListener(::onMessage)
        scope.launch {
            combine(
                nodeIds.distinctUntilChanged(),
                mqtt.connectionState,
            ) { nodeId, connection -> nodeId to (connection is MqttConnectionState.Connected) }
                .distinctUntilChanged()
                .collect { (nodeId, connected) ->
                    if (nodeId != topics.nodeId) {
                        topics = Node5Topics(nodeId)
                        _snapshot.value = Node5Snapshot(nodeId = nodeId)
                    }
                    // Retained topics arrive on subscribe anyway; this also covers a node id
                    // switch while connected and a node that restarted without retained data.
                    if (connected) requestState()
                }
        }
    }

    suspend fun requestState() {
        mqtt.publish(topics.configGet, "{}", qos = MqttQualityOfService.AT_LEAST_ONCE)
    }

    suspend fun setMainSwitch(on: Boolean): CommandOutcome =
        send(topics.mainSwitchSet) { id -> Node5Json.mainSwitchCommand(on, id) }

    /** Sends only the parts that are not null; the node keeps the rest. */
    suspend fun updateZone(
        zone: String,
        hysteresis: Hysteresis? = null,
        workday: List<ScheduleSegment>? = null,
        weekend: List<ScheduleSegment>? = null,
    ): CommandOutcome = send(topics.zoneSet(zone)) { id -> Node5Json.zoneSetCommand(id, hysteresis, workday, weekend) }

    private suspend fun send(topic: String, payload: (id: String) -> String): CommandOutcome {
        if (mqtt.connectionState.value !is MqttConnectionState.Connected) return CommandOutcome.NotConnected
        val id = newId()
        val answer = CompletableDeferred<Node5CommandResult>()
        pending[id] = answer
        return try {
            mqtt.publish(topic, payload(id), qos = MqttQualityOfService.AT_LEAST_ONCE)
            val result = withTimeoutOrNull(commandTimeoutMs) { answer.await() }
            when {
                result == null -> CommandOutcome.Timeout
                result.ok -> CommandOutcome.Accepted(result.rev)
                else -> CommandOutcome.Rejected(result.error.orEmpty())
            }
        } finally {
            pending.remove(id)
        }
    }

    /** Called on the MQTT client's thread for every message on the shared subscription. */
    private fun onMessage(topic: String, payload: String, @Suppress("UNUSED_PARAMETER") retained: Boolean) {
        val path = topics.relative(topic) ?: return
        when {
            path == Node5Topics.RESULT -> Node5Json.parseResult(payload)?.let { result ->
                result.id?.let { pending[it]?.complete(result) }
            }
            path == Node5Topics.CONFIG -> Node5Json.parseConfig(payload)?.let { c -> _snapshot.update { it.copy(config = c) } }
            path == Node5Topics.STATE -> Node5Json.parseState(payload)?.let { s -> _snapshot.update { it.copy(live = s) } }
            path == Node5Topics.PUMP -> Node5Json.parsePump(payload)?.let { p -> _snapshot.update { it.copy(pump = p) } }
            path == Node5Topics.SAFE_MODE -> Node5Json.parseSafeMode(payload)?.let { m -> _snapshot.update { it.copy(safeMode = m) } }
            path.startsWith(Node5Topics.ZONES_PREFIX) && !path.endsWith("/set") -> {
                val zone = path.removePrefix(Node5Topics.ZONES_PREFIX)
                if (zone.isNotEmpty() && '/' !in zone) {
                    Node5Json.parseZone(payload)?.let { z -> _snapshot.update { it.copy(zones = it.zones + (zone to z)) } }
                }
            }
        }
    }

    companion object {
        /** Ids unique per app run and readable in broker logs: `app-<start time>-<counter>`. */
        fun defaultIdGenerator(): () -> String {
            val session = System.currentTimeMillis() % 1_000_000
            val counter = AtomicLong()
            return { "app-$session-${counter.incrementAndGet()}" }
        }
    }
}
