package zhb.company.smart_home.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.ui.settings.NodeRow

/** One node's health (status, signal, uptime, firmware, network) for the node detail screen. */
class NodeDetailViewModel(private val nodeId: String, repository: SmartHomeRepository) : ViewModel() {
    val state: StateFlow<NodeRow> = repository.state
        .map { NodeRow(nodeId, NodeLabels.of(nodeId), it.nodeStatus(nodeId), it.nodeHealth[nodeId]) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NodeRow(nodeId, NodeLabels.of(nodeId), repository.state.value.nodeStatus(nodeId), repository.state.value.nodeHealth[nodeId]))
}
