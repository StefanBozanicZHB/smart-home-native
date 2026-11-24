package zhb.company.smart_home.data.garden

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.MqttQualityOfService
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.data.node.NodeCommandClient
import zhb.company.smart_home.domain.garden.Node2Config
import zhb.company.smart_home.domain.garden.Node2Json
import zhb.company.smart_home.domain.garden.Node2State
import zhb.company.smart_home.domain.node.NodeJson
import zhb.company.smart_home.domain.schedule.OutputSchedule
import zhb.company.smart_home.domain.schedule.ScheduleJson

/** Node 2 topics (`bozaSub/kuca/node2/...`). The outdoor temperature is read by [SmartHomeRepository]. */
object Node2Topics {
    const val BASE = "bozaSub/kuca/node2/"
    const val NEON_SET = "${BASE}neon/set"
    const val LAMP_SET = "${BASE}lamp/set"
    const val CONFIG_GET = "${BASE}config/get"
    const val CONFIG = "config"
    const val STATE = "state"
    const val RESULT = "result"

    /** `<output>/schedule/set`, relative to [BASE] (also the `cmd` in the answer). */
    fun scheduleCommand(output: String) = "$output/schedule/set"
}

/**
 * Node 2 (yard lights) on top of the shared MQTT connection: the retained `state` and `config`
 * (schedules, since `node2-2.1.0`) and the light commands, each answered on `node2/result`.
 */
class Node2Repository(
    private val mqtt: SmartHomeRepository,
    scope: CoroutineScope,
    commandTimeoutMs: Long = 10_000,
    newId: () -> String = NodeCommandClient.defaultIdGenerator(),
) {
    private val _state = MutableStateFlow<Node2State?>(null)
    val state: StateFlow<Node2State?> = _state.asStateFlow()

    /** Null until the node publishes `node2/config`; older firmware never does (no schedules). */
    private val _config = MutableStateFlow<Node2Config?>(null)
    val config: StateFlow<Node2Config?> = _config.asStateFlow()

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
        mqtt.publish(Node2Topics.CONFIG_GET, "{}", qos = MqttQualityOfService.AT_LEAST_ONCE)
    }

    /** The LED strip on the terrace (`neon` in the firmware). */
    suspend fun setNeon(on: Boolean, minutes: Int? = null): CommandOutcome =
        commands.send(Node2Topics.NEON_SET) { id -> NodeJson.switchCommand(id, on, minutes) }

    suspend fun setLamp(on: Boolean, minutes: Int? = null): CommandOutcome =
        commands.send(Node2Topics.LAMP_SET) { id -> NodeJson.switchCommand(id, on, minutes) }

    /** Daily schedule of a light ([output] as in `node2/config`); null removes it (manual only). */
    suspend fun setSchedule(output: String, schedule: OutputSchedule?): CommandOutcome {
        val command = Node2Topics.scheduleCommand(output)
        val topic = Node2Topics.BASE + command
        return if (schedule == null) {
            commands.sendWithoutId(topic, command, ScheduleJson.REMOVE)
        } else {
            commands.send(topic) { id -> ScheduleJson.setCommand(id, schedule) }
        }
    }

    private fun onMessage(topic: String, payload: String, @Suppress("UNUSED_PARAMETER") retained: Boolean) {
        if (!topic.startsWith(Node2Topics.BASE)) return
        when (topic.removePrefix(Node2Topics.BASE)) {
            Node2Topics.RESULT -> commands.onResult(payload)
            Node2Topics.STATE -> Node2Json.parseState(payload)?.let { _state.value = it }
            Node2Topics.CONFIG -> Node2Json.parseConfig(payload)?.let { _config.value = it }
        }
    }
}
