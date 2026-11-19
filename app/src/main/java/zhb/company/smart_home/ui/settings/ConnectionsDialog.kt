package zhb.company.smart_home.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.data.mqtt.NodeStatus
import zhb.company.smart_home.data.mqtt.OfflineReason
import zhb.company.smart_home.ui.theme.DisableRed
import zhb.company.smart_home.ui.theme.EnableGreen

@Composable
fun ConnectionsDialog(onDismiss: () -> Unit, viewModel: ConnectionsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConnectionsDialogContent(state, onDismiss)
}

@Composable
private fun ConnectionsDialogContent(state: ConnectionsState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text(stringResource(R.string.connections_title)) },
        text = { ConnectionsList(state) },
    )
}

@Composable
private fun ConnectionsList(state: ConnectionsState) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        state.nodes.forEach { NodeStatusRow(it) }
        Text(
            stringResource(R.string.connections_legend),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun NodeStatusRow(row: NodeRow) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = nodeDetail(row),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val (icon, tint, description) = when (row.status) {
            NodeStatus.ONLINE -> Triple(Icons.Filled.CheckCircle, EnableGreen, stringResource(R.string.connections_online))
            NodeStatus.OFFLINE -> Triple(Icons.Filled.Error, DisableRed, stringResource(R.string.connections_offline))
            NodeStatus.UNKNOWN -> Triple(
                Icons.AutoMirrored.Filled.HelpOutline,
                MaterialTheme.colorScheme.outline,
                stringResource(R.string.connections_unknown),
            )
        }
        Icon(icon, contentDescription = description, tint = tint)
    }
}

/** Second line: firmware and last heartbeat for new nodes, why it is offline, or "old firmware". */
@Composable
private fun nodeDetail(row: NodeRow): String {
    val health = row.health ?: return if (row.status == NodeStatus.ONLINE) {
        "${row.nodeId} · ${stringResource(R.string.connections_legacy)}"
    } else {
        "${row.nodeId} · ${stringResource(R.string.connections_legacy_no_message)}"
    }
    val parts = mutableListOf(row.nodeId)
    (health.heartbeat?.firmware ?: health.info?.firmware)?.let { parts += it }
    when (health.offlineReason) {
        OfflineReason.LAST_WILL -> parts += stringResource(R.string.connections_reason_last_will)
        OfflineReason.NO_HEARTBEAT -> parts += stringResource(R.string.connections_reason_no_heartbeat)
        null -> health.heartbeat?.rssi?.let { parts += "$it dBm" }
    }
    health.lastSeenAtMs?.let {
        parts += stringResource(R.string.connections_last_seen, SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(it)))
    }
    return parts.joinToString(" · ")
}

// ---- preview -------------------------------------------------------------------------------------

@androidx.compose.ui.tooling.preview.Preview(name = "Connections")
@Composable
private fun ConnectionsDialogPreview() {
    val now = 1_763_500_000_000L
    fun health(id: String, status: NodeStatus, reason: OfflineReason?, fw: String, rssi: Int?) =
        zhb.company.smart_home.data.mqtt.NodeHealth(
            nodeId = id, status = status, offlineReason = reason, lastSeenAtMs = now - 20_000,
            heartbeat = zhb.company.smart_home.data.mqtt.NodeHeartbeat(fw, 3600, rssi, 27_000, 1), info = null,
        )
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        androidx.compose.material3.Surface(modifier = Modifier.padding(16.dp)) {
        ConnectionsList(
            ConnectionsState(
                listOf(
                    NodeRow("node0", "Prskalice (tajmer)", NodeStatus.ONLINE, null),
                    NodeRow("node3", "Ulazna vrata", NodeStatus.UNKNOWN, null),
                    NodeRow("node4", "Dnevni boravak (prizemlje)", NodeStatus.ONLINE, health("node4", NodeStatus.ONLINE, null, "node4-2.1.1", -58)),
                    NodeRow("node5", "Grejanje", NodeStatus.OFFLINE, health("node5", NodeStatus.OFFLINE, OfflineReason.NO_HEARTBEAT, "node5-2.0.0", -70)),
                    NodeRow("testnode5", "Grejanje – test ploča", NodeStatus.OFFLINE, health("testnode5", NodeStatus.OFFLINE, OfflineReason.LAST_WILL, "node5-2.0.0", null)),
                ),
            ),
        )
        }
    }
}
