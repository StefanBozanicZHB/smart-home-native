package zhb.company.smart_home.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import zhb.company.smart_home.R
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.NodeStatus
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.CompactRow
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.StatusChip
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.components.animatedItem
import zhb.company.smart_home.ui.theme.AppTheme
import zhb.company.smart_home.ui.settings.ConnectionsList
import zhb.company.smart_home.ui.settings.ConnectionsState
import zhb.company.smart_home.ui.settings.ConnectionsViewModel
import zhb.company.smart_home.ui.settings.NodeRow
import zhb.company.smart_home.ui.settings.SystemSettingsDialog
import zhb.company.smart_home.data.history.HistoryPeriod
import zhb.company.smart_home.ui.history.HistoryAutoLoad
import zhb.company.smart_home.ui.history.HistoryState
import zhb.company.smart_home.ui.history.HistoryViewModel
import zhb.company.smart_home.ui.history.AvailabilityCard
import zhb.company.smart_home.ui.history.loggerStatusText

/** Nodes (status, signal, firmware), broker connection and the entry to settings. */
@Composable
fun SystemScreen(
    onOpenNode: (String) -> Unit,
    viewModel: ConnectionsViewModel = koinViewModel(),
    repository: SmartHomeRepository = koinInject(),
    historyViewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by historyViewModel.state.collectAsStateWithLifecycle()
    HistoryAutoLoad(historyViewModel, setOf(HistoryPeriod.MONTH), withMeta = true)
    val connection by repository.connectionState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    SystemContent(
        state = state,
        connected = connection is MqttConnectionState.Connected,
        onOpenSettings = { showSettings = true },
        onOpenNode = onOpenNode,
        history = history,
    )
    if (showSettings) SystemSettingsDialog(onDismiss = { showSettings = false })
}

@Composable
private fun SystemContent(
    state: ConnectionsState,
    connected: Boolean,
    onOpenSettings: () -> Unit,
    onOpenNode: (String) -> Unit = {},
    history: HistoryState = HistoryState(),
) {
    val nodes = state.nodes
    val online = nodes.count { it.status == NodeStatus.ONLINE }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        animatedItem("system-1") {
            ScreenHeader(title = stringResource(R.string.system_title), horizontalPadding = 0.dp) {
                StatusChip(
                    stringResource(R.string.system_online_count, online, nodes.size),
                    if (online == nodes.size) Tone.ACTIVE else Tone.WARNING,
                )
            }
        }
        animatedItem("system-2") { CompactRow(Icons.Outlined.Settings, stringResource(R.string.system_settings), onClick = onOpenSettings) }
        animatedItem("system-3") {
            DashboardCard {
                CardHeader(Icons.Outlined.Dns, stringResource(if (connected) R.string.system_broker_connected else R.string.system_broker_disconnected)) {
                    StatusChip(
                        stringResource(if (connected) R.string.home_connected else R.string.home_disconnected),
                        if (connected) Tone.ACTIVE else Tone.DANGER,
                    )
                }
                loggerStatusText(history.meta)?.let { (text, ok) ->
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else AppTheme.status.warning,
                    )
                }
            }
        }
        animatedItem("system-4") {
            DashboardCard {
                CardHeader(Icons.Outlined.Memory, stringResource(R.string.system_nodes))
                ConnectionsList(ConnectionsState(nodes), onNodeClick = onOpenNode)
            }
        }
        // Nodes the RPi logger has no availability history for are left out by the card.
        animatedItem("system-5") { AvailabilityCard(history, nodes.map { it.nodeId to NodeLabels.of(it.nodeId) }) }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "System", heightDp = 900)
@androidx.compose.ui.tooling.preview.Preview(name = "System - font 200%", heightDp = 2000, fontScale = 2f)
@Composable
private fun SystemPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface {
            SystemContent(
                ConnectionsState(
                    listOf(
                        NodeRow("node1", NodeLabels.of("node1"), NodeStatus.ONLINE, null),
                        NodeRow("node2", NodeLabels.of("node2"), NodeStatus.ONLINE, null),
                        NodeRow("node3", NodeLabels.of("node3"), NodeStatus.UNKNOWN, null),
                        NodeRow("node4", NodeLabels.of("node4"), NodeStatus.ONLINE, null),
                    ),
                ),
                connected = true,
                onOpenSettings = {},
            )
        }
    }
}
