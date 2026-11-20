package zhb.company.smart_home.data.garden

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.MqttQualityOfService
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.data.node.NodeCommandClient
import zhb.company.smart_home.domain.garden.Node1Config
import zhb.company.smart_home.domain.garden.Node1ConfigChange
import zhb.company.smart_home.domain.garden.Node1Json
import zhb.company.smart_home.domain.garden.Node1State
import zhb.company.smart_home.domain.garden.RunEvent

/** Everything the app knows about Node 1, built from its retained topics. */
data class Node1Snapshot(
    val config: Node1Config? = null,
    val state: Node1State? = null,
    /** Last run event (`node1/run`). */
    val lastRun: RunEvent? = null,
)

/** Node 1 topics (`bozaSub/kuca/node1/...`). */
object Node1Topics {
    const val BASE = "bozaSub/kuca/node1/"
    const val CONFIG_SET = "${BASE}config/set"
    const val CONFIG_GET = "${BASE}config/get"
    const val RUN_START = "${BASE}run/start"
    const val RUN_STOP = "${BASE}run/stop"
    const val PUMP_SET = "${BASE}fountain/pump/set"
    const val LIGHT_SET = "${BASE}fountain/light/set"

    // relative state/answer paths
    const val CONFIG = "config"
    const val STATE = "state"
    const val RUN = "run"
    const val RESULT = "result"
}

/**
 * Node 1 (fountain and sprinklers) on top of the shared MQTT connection: keeps [snapshot] up to
 * date and sends commands, each answered on `node1/result`.
 */
class Node1Repository(
    private val mqtt: SmartHomeRepository,
    scope: CoroutineScope,
    commandTimeoutMs: Long = 10_000,
    newId: () -> String = NodeCommandClient.defaultIdGenerator(),
) {
    private val _snapshot = MutableStateFlow(Node1Snapshot())
    val snapshot: StateFlow<Node1Snapshot> = _snapshot.asStateFlow()

    private val commands = NodeCommandClient(mqtt, commandTimeoutMs, newId)

    init {
        mqtt.addMessageListener(::onMessage)
        scope.launch {
            mqtt.connectionState
                .map { it is MqttConnectionState.Connected }
                .distinctUntilChanged()
                .collect { connected -> if (connected) requestState() }
        }
    }

    suspend fun requestState() {
        mqtt.publish(Node1Topics.CONFIG_GET, "{}", qos = MqttQualityOfService.AT_LEAST_ONCE)
    }

    suspend fun updateConfig(change: Node1ConfigChange): CommandOutcome =
        commands.send(Node1Topics.CONFIG_SET) { id -> Node1Json.configSetCommand(id, change) }

    /** Manual run; replaces an active run. */
    suspend fun startRun(zones: List<Int>? = null, minutes: Int? = null): CommandOutcome =
        commands.send(Node1Topics.RUN_START) { id -> Node1Json.runStartCommand(id, zones, minutes) }

    suspend fun stopRun(): CommandOutcome = commands.send(Node1Topics.RUN_STOP) { id -> Node1Json.runStopCommand(id) }

    suspend fun setPump(on: Boolean, minutes: Int? = null): CommandOutcome =
        commands.send(Node1Topics.PUMP_SET) { id -> Node1Json.fountainCommand(id, on, minutes) }

    suspend fun setLight(on: Boolean, minutes: Int? = null): CommandOutcome =
        commands.send(Node1Topics.LIGHT_SET) { id -> Node1Json.fountainCommand(id, on, minutes) }

    /** Called on the MQTT client's thread for every message on the shared subscription. */
    private fun onMessage(topic: String, payload: String, @Suppress("UNUSED_PARAMETER") retained: Boolean) {
        if (!topic.startsWith(Node1Topics.BASE)) return
        when (topic.removePrefix(Node1Topics.BASE)) {
            Node1Topics.RESULT -> commands.onResult(payload)
            Node1Topics.CONFIG -> Node1Json.parseConfig(payload)?.let { c -> _snapshot.update { it.copy(config = c) } }
            Node1Topics.STATE -> Node1Json.parseState(payload)?.let { s -> _snapshot.update { it.copy(state = s) } }
            Node1Topics.RUN -> Node1Json.parseRunEvent(payload)?.let { r -> _snapshot.update { it.copy(lastRun = r) } }
        }
    }
}
