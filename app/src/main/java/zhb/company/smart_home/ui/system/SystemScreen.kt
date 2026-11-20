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
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material3.Surface
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
import zhb.company.smart_home.ui.settings.ConnectionsList
import zhb.company.smart_home.ui.settings.ConnectionsState
import zhb.company.smart_home.ui.settings.ConnectionsViewModel
import zhb.company.smart_home.ui.settings.NodeRow
import zhb.company.smart_home.ui.settings.SystemSettingsDialog

/** Nodes (status, signal, firmware), broker connection and the entry to settings. */
@Composable
fun SystemScreen(
    onOpenShed: () -> Unit,
    onOpenNode: (String) -> Unit,
    viewModel: ConnectionsViewModel = koinViewModel(),
    repository: SmartHomeRepository = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection by repository.connectionState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    SystemContent(
        state = state,
        connected = connection is MqttConnectionState.Connected,
        onOpenSettings = { showSettings = true },
        onOpenShed = onOpenShed,
        onOpenNode = onOpenNode,
    )
    if (showSettings) SystemSettingsDialog(onDismiss = { showSettings = false })
}

@Composable
private fun SystemContent(
    state: ConnectionsState,
    connected: Boolean,
    onOpenSettings: () -> Unit,
    onOpenShed: () -> Unit,
    onOpenNode: (String) -> Unit = {},
) {
    // Nodes not in use (the shed) are left out of the count and the list; the shed has its own row.
    val nodes = state.nodes.filter { it.nodeId !in NodeLabels.NOT_IN_USE }
    val online = nodes.count { it.status == NodeStatus.ONLINE }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(title = stringResource(R.string.system_title), horizontalPadding = 0.dp) {
                StatusChip(
                    stringResource(R.string.system_online_count, online, nodes.size),
                    if (online == nodes.size) Tone.ACTIVE else Tone.WARNING,
                )
            }
        }
        item { CompactRow(Icons.Outlined.Settings, stringResource(R.string.system_settings), onClick = onOpenSettings) }
        item {
            DashboardCard {
                CardHeader(Icons.Outlined.Dns, stringResource(if (connected) R.string.system_broker_connected else R.string.system_broker_disconnected)) {
                    StatusChip(
                        stringResource(if (connected) R.string.home_connected else R.string.home_disconnected),
                        if (connected) Tone.ACTIVE else Tone.DANGER,
                    )
                }
            }
        }
        item {
            DashboardCard {
                CardHeader(Icons.Outlined.Memory, stringResource(R.string.system_nodes))
                ConnectionsList(ConnectionsState(nodes), onNodeClick = onOpenNode)
            }
        }
        item { CompactRow(Icons.Outlined.Warehouse, stringResource(R.string.system_shed), onClick = onOpenShed) }
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
                        NodeRow("node0", NodeLabels.of("node0"), NodeStatus.ONLINE, null),
                        NodeRow("node1", NodeLabels.of("node1"), NodeStatus.ONLINE, null),
                        NodeRow("node2", NodeLabels.of("node2"), NodeStatus.ONLINE, null),
                        NodeRow("node3", NodeLabels.of("node3"), NodeStatus.UNKNOWN, null),
                        NodeRow("node4", NodeLabels.of("node4"), NodeStatus.ONLINE, null),
                        NodeRow("node7", NodeLabels.of("node7"), NodeStatus.UNKNOWN, null),
                    ),
                ),
                connected = true,
                onOpenSettings = {},
                onOpenShed = {},
            )
        }
    }
}
