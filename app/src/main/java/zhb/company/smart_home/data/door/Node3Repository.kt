package zhb.company.smart_home.data.door

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
import zhb.company.smart_home.domain.door.AccessEvent
import zhb.company.smart_home.domain.door.Node3Json
import zhb.company.smart_home.domain.door.Node3State
import zhb.company.smart_home.domain.node.NodeJson

data class Node3Snapshot(val state: Node3State? = null, val lastAccess: AccessEvent? = null)

/** Node 3 topics (`bozaSub/kuca/node3/...`). */
object Node3Topics {
    const val BASE = "bozaSub/kuca/node3/"
    const val DOOR_OPEN = "${BASE}door/open"
    const val CONFIG_GET = "${BASE}config/get"
    const val STATE = "state"
    const val ACCESS = "access"
    const val RESULT = "result"
}

/** Front door (Node 3): lock state, last access attempt, and the open command answered on `node3/result`. */
class Node3Repository(
    private val mqtt: SmartHomeRepository,
    scope: CoroutineScope,
    commandTimeoutMs: Long = 10_000,
    newId: () -> String = NodeCommandClient.defaultIdGenerator(),
) {
    private val _snapshot = MutableStateFlow(Node3Snapshot())
    val snapshot: StateFlow<Node3Snapshot> = _snapshot.asStateFlow()

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
        mqtt.publish(Node3Topics.CONFIG_GET, "{}", qos = MqttQualityOfService.AT_LEAST_ONCE)
    }

    /** Opens the lock for 3 s. */
    suspend fun openDoor(): CommandOutcome = commands.send(Node3Topics.DOOR_OPEN) { id -> NodeJson.idOnly(id) }

    private fun onMessage(topic: String, payload: String, @Suppress("UNUSED_PARAMETER") retained: Boolean) {
        if (!topic.startsWith(Node3Topics.BASE)) return
        when (topic.removePrefix(Node3Topics.BASE)) {
            Node3Topics.RESULT -> commands.onResult(payload)
            Node3Topics.STATE -> Node3Json.parseState(payload)?.let { s -> _snapshot.update { it.copy(state = s) } }
            Node3Topics.ACCESS -> Node3Json.parseAccess(payload)?.let { a -> _snapshot.update { it.copy(lastAccess = a) } }
        }
    }
}
