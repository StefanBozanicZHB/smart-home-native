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
    /** Null until the node has published `status`, `heartbeat`, `info` or `boot`. */
    val health: NodeHealth?,
)

data class ConnectionsState(val nodes: List<NodeRow> = emptyList())

class ConnectionsViewModel(repository: SmartHomeRepository) : ViewModel() {
    val state: StateFlow<ConnectionsState> = repository.state
        .map(::rows)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionsState())

    private fun rows(home: SmartHomeState): ConnectionsState {
        return ConnectionsState(NodeLabels.ACTIVE.map { id -> NodeRow(id, NodeLabels.of(id), home.nodeStatus(id), home.nodeHealth[id]) })
    }
}
