package zhb.company.smart_home.data.garden

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.data.node.NodeChannel
import zhb.company.smart_home.data.node.NodeCommandClient
import zhb.company.smart_home.domain.garden.Node6Json
import zhb.company.smart_home.domain.garden.Node6State
import zhb.company.smart_home.domain.node.NodeJson

/** Node 6 topics (`bozaSub/kuca/node6/...`). The water temperature is read by [SmartHomeRepository]. */
object Node6Topics {
    const val BASE = "bozaSub/kuca/node6/"
    const val PUMP_SET = "${BASE}pump/set"
    const val HEATING_SET = "${BASE}heating/set"
    const val STATE = "state"
}

/** Pool (Node 6): pump and heating with their switch-off timers; commands are answered on `node6/result`. */
class Node6Repository(
    private val mqtt: SmartHomeRepository,
    scope: CoroutineScope,
    commandTimeoutMs: Long = 10_000,
    newId: () -> String = NodeCommandClient.defaultIdGenerator(),
) {
    private val _state = MutableStateFlow<Node6State?>(null)
    val state: StateFlow<Node6State?> = _state.asStateFlow()

    private val channel = NodeChannel(mqtt, scope, Node6Topics.BASE, commandTimeoutMs, newId, ::onMessage)
    private val commands = channel.commands

    suspend fun requestState() = channel.requestState()

    /** [minutes] (1-1440) switches the pump off by itself after that time. */
    suspend fun setPump(on: Boolean, minutes: Int? = null): CommandOutcome =
        commands.send(Node6Topics.PUMP_SET) { id -> NodeJson.switchCommand(id, on, minutes) }

    /** Pool heating (a second relay, independent of the pump); [minutes] (1-1440) as for the pump. */
    suspend fun setHeating(on: Boolean, minutes: Int? = null): CommandOutcome =
        commands.send(Node6Topics.HEATING_SET) { id -> NodeJson.switchCommand(id, on, minutes) }

    private fun onMessage(path: String, payload: String) {
        when (path) {
            Node6Topics.STATE -> Node6Json.parseState(payload)?.let { _state.value = it }
        }
    }
}
