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
import zhb.company.smart_home.domain.garden.Node6Json
import zhb.company.smart_home.domain.garden.Node6State
import zhb.company.smart_home.domain.node.NodeJson

/** Node 6 topics (`bozaSub/kuca/node6/...`). The water temperature is read by [SmartHomeRepository]. */
object Node6Topics {
    const val BASE = "bozaSub/kuca/node6/"
    const val PUMP_SET = "${BASE}pump/set"
    const val CONFIG_GET = "${BASE}config/get"
    const val STATE = "state"
    const val RESULT = "result"
}

/** Pool (Node 6): pump state with its switch-off timer, and the pump command answered on `node6/result`. */
class Node6Repository(
    private val mqtt: SmartHomeRepository,
    scope: CoroutineScope,
    commandTimeoutMs: Long = 10_000,
    newId: () -> String = NodeCommandClient.defaultIdGenerator(),
) {
    private val _state = MutableStateFlow<Node6State?>(null)
    val state: StateFlow<Node6State?> = _state.asStateFlow()

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
        mqtt.publish(Node6Topics.CONFIG_GET, "{}", qos = MqttQualityOfService.AT_LEAST_ONCE)
    }

    /** [minutes] (1-1440) switches the pump off by itself after that time. */
    suspend fun setPump(on: Boolean, minutes: Int? = null): CommandOutcome =
        commands.send(Node6Topics.PUMP_SET) { id -> NodeJson.switchCommand(id, on, minutes) }

    private fun onMessage(topic: String, payload: String, @Suppress("UNUSED_PARAMETER") retained: Boolean) {
        if (!topic.startsWith(Node6Topics.BASE)) return
        when (topic.removePrefix(Node6Topics.BASE)) {
            Node6Topics.RESULT -> commands.onResult(payload)
            Node6Topics.STATE -> Node6Json.parseState(payload)?.let { _state.value = it }
        }
    }
}
