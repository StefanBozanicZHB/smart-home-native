package zhb.company.smart_home.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import zhb.company.smart_home.data.mqtt.SmartHomeRepository

data class ConnectionsState(val nodeOnline: Map<Int, Boolean> = emptyMap())

private val NODE_LABELS = mapOf(
    0 to "Prskalice (tajmer)", 1 to "Prskalice / fontana", 2 to "Dvorište", 3 to "Ulazna vrata",
    4 to "Dnevni boravak (prizemlje)", 5 to "Grejanje", 6 to "Bazen", 7 to "Šupa", 8 to "Dnevni boravak (sprat)",
)

class ConnectionsViewModel(repository: SmartHomeRepository) : ViewModel() {
    val state: StateFlow<ConnectionsState> = repository.state
        .map { ConnectionsState(nodeOnline = (0..8).associateWith { n -> it.isNodeOnline(n) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionsState())

    fun labelFor(node: Int): String = NODE_LABELS[node] ?: "Node $node"
}
