package zhb.company.smart_home.data.door

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.data.node.NodeChannel
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
    const val STATE = "state"
    const val ACCESS = "access"
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

    private val channel = NodeChannel(mqtt, scope, Node3Topics.BASE, commandTimeoutMs, newId, ::onMessage)
    private val commands = channel.commands

    suspend fun requestState() = channel.requestState()

    /** Opens the lock for 3 s. */
    suspend fun openDoor(): CommandOutcome = commands.send(Node3Topics.DOOR_OPEN) { id -> NodeJson.idOnly(id) }

    private fun onMessage(path: String, payload: String) {
        when (path) {
            Node3Topics.STATE -> Node3Json.parseState(payload)?.let { s -> _snapshot.update { it.copy(state = s) } }
            Node3Topics.ACCESS -> Node3Json.parseAccess(payload)?.let { a -> _snapshot.update { it.copy(lastAccess = a) } }
        }
    }
}
