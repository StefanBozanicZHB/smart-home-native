package zhb.company.smart_home.ui.system

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import zhb.company.smart_home.R
import zhb.company.smart_home.data.mqtt.NodeHealth
import zhb.company.smart_home.data.mqtt.NodeStatus
import zhb.company.smart_home.data.mqtt.OfflineReason
import zhb.company.smart_home.ui.components.AlertBanner
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.StatusChip
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.components.animatedItem
import zhb.company.smart_home.ui.components.isLargeFont
import zhb.company.smart_home.ui.format.Durations
import zhb.company.smart_home.ui.settings.NodeRow
import zhb.company.smart_home.ui.settings.SignalIndicator
import zhb.company.smart_home.ui.settings.signalName
import zhb.company.smart_home.ui.theme.AppTheme

/** Diagnostics for one node: connection, uptime and restarts, memory, firmware and network. */
@Composable
fun NodeDetailScreen(nodeId: String, onBack: () -> Unit, viewModel: NodeDetailViewModel = koinViewModel { parametersOf(nodeId) }) {
    val row by viewModel.state.collectAsStateWithLifecycle()
    // Relative times (uptime, "seen 20 s ago") move on between heartbeats.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    NodeDetailContent(row, now, onBack)
}

private const val TICK_MS = 5_000L

@Composable
private fun NodeDetailContent(row: NodeRow, nowMs: Long, onBack: () -> Unit = {}) {
    val health = row.health
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        animatedItem("node-1") {
            ScreenHeader(title = row.label, subtitle = row.nodeId, horizontalPadding = 0.dp, onBack = onBack) {
                val (text, tone) = when (row.status) {
                    NodeStatus.ONLINE -> R.string.connections_online to Tone.ACTIVE
                    NodeStatus.OFFLINE -> R.string.connections_offline to Tone.DANGER
                    NodeStatus.UNKNOWN -> R.string.connections_unknown to Tone.NEUTRAL
                }
                StatusChip(stringResource(text), tone)
            }
        }
        if (health == null) {
            animatedItem("node-2") {
                DashboardCard {
                    Text(stringResource(R.string.node_detail_no_data), style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@LazyColumn
        }
        if (health.recentlyRestarted(nowMs)) {
            animatedItem("node-3") {
                AlertBanner(
                    Icons.Outlined.RestartAlt,
                    stringResource(R.string.node_detail_restarted, Durations.compact(health.uptimeSecondsAt(nowMs) ?: 0)),
                    Tone.WARNING,
                    detail = health.currentBoot?.reset?.let { stringResource(R.string.node_detail_restarted_reason, it) },
                )
            }
        }
        animatedItem("node-4") { ConnectionCard(health, nowMs) }
        animatedItem("node-5") { RunningCard(health, nowMs) }
        animatedItem("node-6") { VersionCard(health) }
        animatedItem("node-7") { NetworkCard(health) }
        animatedItem("node-8") {
            Text(
                stringResource(R.string.node_detail_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectionCard(health: NodeHealth, nowMs: Long) {
    DashboardCard {
        CardHeader(Icons.Outlined.Wifi, stringResource(R.string.node_detail_connection)) {
            val rssi = health.rssi
            val signal = health.signal
            if (rssi != null && signal != null) SignalIndicator(rssi, signal, live = health.status == NodeStatus.ONLINE)
        }
        val status = when (health.status) {
            NodeStatus.ONLINE -> stringResource(R.string.connections_online)
            NodeStatus.UNKNOWN -> stringResource(R.string.connections_unknown)
            NodeStatus.OFFLINE -> stringResource(R.string.connections_offline) + when (health.offlineReason) {
                OfflineReason.LAST_WILL -> ", " + stringResource(R.string.connections_reason_last_will)
                OfflineReason.NO_HEARTBEAT -> ", " + stringResource(R.string.connections_reason_no_heartbeat)
                null -> ""
            }
        }
        InfoRow(stringResource(R.string.node_detail_status), status)
        health.lastSeenAtMs?.let {
            InfoRow(
                stringResource(R.string.node_detail_last_seen),
                stringResource(R.string.node_detail_last_seen_ago, clock(it), Durations.compact((nowMs - it) / 1000)),
            )
        }
        val signal = health.signal
        health.heartbeat?.rssi?.let { rssi ->
            InfoRow(stringResource(R.string.node_detail_signal_now), stringResource(R.string.node_detail_signal_value, rssi, signalName(signal!!)))
        }
        health.info?.rssi?.let { InfoRow(stringResource(R.string.node_detail_signal_connect), "$it dBm") }
        health.reconnects?.let {
            InfoRow(
                stringResource(R.string.node_detail_reconnects),
                stringResource(R.string.node_detail_reconnects_value, it.toInt()),
                valueColor = if (it >= RECONNECTS_WARN) AppTheme.status.warning else Color.Unspecified,
            )
        }
    }
}

/** Several broker reconnects in one run point at an unstable Wi-Fi or broker. */
private const val RECONNECTS_WARN = 3L

@Composable
private fun RunningCard(health: NodeHealth, nowMs: Long) {
    val uptime = health.uptimeSecondsAt(nowMs)
    val boot = health.currentBoot
    val heap = health.heartbeat?.freeHeap
    if (uptime == null && boot?.reset == null && heap == null) return
    DashboardCard {
        CardHeader(Icons.Outlined.Memory, stringResource(R.string.node_detail_running))
        if (uptime != null) {
            InfoRow(
                stringResource(R.string.node_detail_uptime),
                stringResource(R.string.node_detail_uptime_value, Durations.compact(uptime), dateTime(health.bootedAtMs!!)),
                valueColor = if (health.recentlyRestarted(nowMs)) AppTheme.status.warning else Color.Unspecified,
            )
        }
        boot?.reset?.let { InfoRow(stringResource(R.string.node_detail_reset), it) }
        heap?.let { InfoRow(stringResource(R.string.node_detail_heap), Durations.kilobytes(it)) }
    }
}

@Composable
private fun VersionCard(health: NodeHealth) {
    val firmware = health.firmware
    val git = health.info?.gitRevision ?: health.boot?.gitRevision
    if (firmware == null && git == null) return
    DashboardCard {
        CardHeader(Icons.Outlined.Code, stringResource(R.string.node_detail_version))
        firmware?.let { InfoRow(stringResource(R.string.node_detail_firmware), it, copyable = true) }
        git?.let {
            InfoRow(stringResource(R.string.node_detail_git), it, copyable = true)
            if (it.endsWith("-dirty")) {
                Text(stringResource(R.string.node_detail_git_dirty), style = MaterialTheme.typography.labelMedium, color = AppTheme.status.warning)
            }
        }
    }
}

@Composable
private fun NetworkCard(health: NodeHealth) {
    val info = health.info ?: return
    if (info.ip == null && info.mac == null && info.host == null && info.ssid == null) return
    DashboardCard {
        CardHeader(Icons.Outlined.Lan, stringResource(R.string.node_detail_network))
        info.ip?.let { InfoRow(stringResource(R.string.node_detail_ip), it, copyable = true) }
        info.mac?.let { InfoRow(stringResource(R.string.node_detail_mac), it, copyable = true) }
        info.host?.let { InfoRow(stringResource(R.string.node_detail_host), it, copyable = true) }
        info.ssid?.let { InfoRow(stringResource(R.string.node_detail_ssid), it) }
    }
}

/**
 * Label on the left, value on the right; with a large font the label goes above the value, so
 * addresses do not break in the middle. [copyable] adds a copy button.
 */
@Composable
private fun InfoRow(label: String, value: String, copyable: Boolean = false, valueColor: Color = Color.Unspecified) {
    val stacked = isLargeFont()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        val labelText: @Composable (Modifier) -> Unit = {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = it)
        }
        if (stacked) {
            Column(modifier = Modifier.weight(1f)) {
                labelText(Modifier)
                Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
            }
        } else {
            labelText(Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1.4f).padding(start = 8.dp),
            )
        }
        if (copyable) CopyButton(label, value)
    }
}

@Composable
private fun CopyButton(label: String, value: String) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.node_detail_copied, value)
    IconButton(
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
                // Android 13+ confirms copies itself.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
        },
        modifier = Modifier.size(36.dp),
    ) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.node_detail_copy, label), modifier = Modifier.size(18.dp))
    }
}

private fun clock(ms: Long) = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

private fun dateTime(ms: Long) = SimpleDateFormat("dd.MM. HH:mm", Locale.US).format(Date(ms))

// ---- preview -------------------------------------------------------------------------------------

private val PREVIEW_NOW = 1_763_500_000_000L

private fun previewHealth(uptime: Long, mqtt: Long, dirty: Boolean) = NodeHealth(
    nodeId = "node5",
    status = NodeStatus.ONLINE,
    offlineReason = null,
    lastSeenAtMs = PREVIEW_NOW - 20_000,
    heartbeat = zhb.company.smart_home.data.mqtt.NodeHeartbeat("node5-2.0.0", uptime, -67, 27_560, mqtt),
    info = zhb.company.smart_home.data.mqtt.NodeInfo(
        "node5-2.0.0", if (dirty) "1a62d4e-dirty" else "1a62d4e", "192.168.1.57", "node5-heating", -64, "A4:CF:12:00:00:00", "HomeWiFi",
    ),
    heartbeatAtMs = PREVIEW_NOW - 20_000,
    boot = zhb.company.smart_home.data.mqtt.NodeBoot("node5-2.0.0", "1a62d4e", "Hardware Watchdog", PREVIEW_NOW - 20_000 - uptime * 1000 + 4_000),
)

@androidx.compose.ui.tooling.preview.Preview(name = "Node detail", heightDp = 1100)
@androidx.compose.ui.tooling.preview.Preview(name = "Node detail - font 200%", heightDp = 2200, fontScale = 2f)
@Composable
private fun NodeDetailPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface { NodeDetailContent(NodeRow("node5", "Grejanje", NodeStatus.ONLINE, previewHealth(uptime = 312_000, mqtt = 4, dirty = false)), PREVIEW_NOW) }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Node detail - restarted", heightDp = 1100)
@Composable
private fun NodeDetailRestartedPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface { NodeDetailContent(NodeRow("node5", "Grejanje", NodeStatus.ONLINE, previewHealth(uptime = 240, mqtt = 1, dirty = true)), PREVIEW_NOW) }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Node detail - no data", heightDp = 400)
@Composable
private fun NodeDetailNoDataPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface { NodeDetailContent(NodeRow("node2", "Dvorište", NodeStatus.UNKNOWN, null), PREVIEW_NOW) }
    }
}
