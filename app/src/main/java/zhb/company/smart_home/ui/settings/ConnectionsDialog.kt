package zhb.company.smart_home.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.data.mqtt.NodeStatus
import zhb.company.smart_home.data.mqtt.OfflineReason
import zhb.company.smart_home.data.mqtt.SignalQuality
import zhb.company.smart_home.ui.theme.AppTheme

/** Node list with status, signal and firmware; used on the system screen. */
@Composable
internal fun ConnectionsList(state: ConnectionsState, onNodeClick: ((String) -> Unit)? = null) {
    Column {
        state.nodes.forEach { row ->
            // Details open once the node has been heard from.
            NodeStatusRow(row, onClick = if (row.health != null && onNodeClick != null) ({ onNodeClick(row.nodeId) }) else null)
        }
        Text(
            stringResource(R.string.connections_legend),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun NodeStatusRow(row: NodeRow, onClick: (() -> Unit)?) {
    val openLabel = stringResource(R.string.node_detail_open, row.label)
    val clickable = if (onClick != null) Modifier.clickable(onClickLabel = openLabel, onClick = onClick) else Modifier
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().then(clickable).padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = nodeDetail(row),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val health = row.health
        val rssi = health?.rssi
        val signal = health?.signal
        if (rssi != null && signal != null) SignalIndicator(rssi, signal, live = row.status == NodeStatus.ONLINE)
        val (icon, tint, description) = when (row.status) {
            NodeStatus.ONLINE -> Triple(Icons.Filled.CheckCircle, AppTheme.status.active, stringResource(R.string.connections_online))
            NodeStatus.OFFLINE -> Triple(Icons.Filled.Error, AppTheme.status.danger, stringResource(R.string.connections_offline))
            NodeStatus.UNKNOWN -> Triple(
                Icons.AutoMirrored.Filled.HelpOutline,
                MaterialTheme.colorScheme.outline,
                stringResource(R.string.connections_unknown),
            )
        }
        Icon(icon, contentDescription = description, tint = tint)
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** Four bars plus dBm; colored by quality while the node is online, gray (last known value) otherwise. */
@Composable
internal fun SignalIndicator(rssi: Int, quality: SignalQuality, live: Boolean) {
    val color = when {
        !live -> MaterialTheme.colorScheme.outline
        quality == SignalQuality.EXCELLENT || quality == SignalQuality.GOOD -> AppTheme.status.active
        quality == SignalQuality.WEAK -> AppTheme.status.warning
        else -> AppTheme.status.danger
    }
    val empty = MaterialTheme.colorScheme.outlineVariant
    val label = stringResource(R.string.connections_signal, signalName(quality), rssi)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(start = 8.dp, end = 10.dp).semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Canvas(modifier = Modifier.size(width = 22.dp, height = 16.dp)) {
            val gap = size.width / 11f
            val barWidth = (size.width - 3 * gap) / 4f
            for (i in 0 until 4) {
                val barHeight = size.height * (i + 1) / 4f
                drawRoundRect(
                    color = if (i < quality.bars) color else empty,
                    topLeft = Offset(i * (barWidth + gap), size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 3f),
                )
            }
        }
        Text("$rssi dBm", style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
internal fun signalName(quality: SignalQuality): String = stringResource(
    when (quality) {
        SignalQuality.EXCELLENT -> R.string.connections_signal_excellent
        SignalQuality.GOOD -> R.string.connections_signal_good
        SignalQuality.WEAK -> R.string.connections_signal_weak
        SignalQuality.POOR -> R.string.connections_signal_poor
    },
)

/** Second line: firmware, why the node is offline or its weak signal, and when it was last heard. */
@Composable
private fun nodeDetail(row: NodeRow): String {
    val health = row.health ?: return "${row.nodeId} · ${stringResource(R.string.connections_no_message)}"
    val parts = mutableListOf(row.nodeId)
    health.firmware?.let { parts += it }
    when (health.offlineReason) {
        OfflineReason.LAST_WILL -> parts += stringResource(R.string.connections_reason_last_will)
        OfflineReason.NO_HEARTBEAT -> parts += stringResource(R.string.connections_reason_no_heartbeat)
        null -> health.signal?.takeIf { it == SignalQuality.WEAK || it == SignalQuality.POOR }?.let { parts += signalName(it) }
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
                    NodeRow("node1", "Fontana i prskalice", NodeStatus.ONLINE, health("node1", NodeStatus.ONLINE, null, "node1-2.0.0", -64)),
                    NodeRow("node3", "Ulazna vrata", NodeStatus.UNKNOWN, null),
                    NodeRow("node4", "Dnevni boravak (prizemlje)", NodeStatus.ONLINE, health("node4", NodeStatus.ONLINE, null, "node4-2.1.1", -58)),
                    NodeRow("node2", "Dvorište", NodeStatus.ONLINE, health("node2", NodeStatus.ONLINE, null, "node2-2.0.0", -76)),
                    NodeRow("node6", "Bazen", NodeStatus.ONLINE, health("node6", NodeStatus.ONLINE, null, "node6-2.0.0", -86)),
                    NodeRow("node5", "Grejanje", NodeStatus.OFFLINE, health("node5", NodeStatus.OFFLINE, OfflineReason.NO_HEARTBEAT, "node5-2.0.0", -70)),
                    NodeRow("node8", "Dnevni boravak (sprat)", NodeStatus.OFFLINE, health("node8", NodeStatus.OFFLINE, OfflineReason.LAST_WILL, "node8-2.0.0", null)),
                ),
            ),
        )
        }
    }
}
