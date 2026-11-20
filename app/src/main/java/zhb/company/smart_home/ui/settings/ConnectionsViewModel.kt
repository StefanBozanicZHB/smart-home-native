package zhb.company.smart_home.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import zhb.company.smart_home.data.mqtt.NodeHealth
import zhb.company.smart_home.data.mqtt.NodeStatus
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.domain.model.SmartHomeState
import zhb.company.smart_home.ui.system.NodeLabels

data class NodeRow(
    val nodeId: String,
    val label: String,
    val status: NodeStatus,
    /** Null for legacy nodes (no status topic, no heartbeat). */
    val health: NodeHealth?,
)

data class ConnectionsState(val nodes: List<NodeRow> = emptyList())

class ConnectionsViewModel(repository: SmartHomeRepository) : ViewModel() {
    val state: StateFlow<ConnectionsState> = repository.state
        .map(::rows)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionsState())

    private fun rows(home: SmartHomeState): ConnectionsState {
        // node0..node8 always, plus any other node that announced itself (e.g. the bench board).
        val ids = ((0..8).map { "node$it" } + home.nodeHealth.keys.sorted()).distinct()
        return ConnectionsState(ids.map { id -> NodeRow(id, NodeLabels.of(id), home.nodeStatus(id), home.nodeHealth[id]) })
    }
}
