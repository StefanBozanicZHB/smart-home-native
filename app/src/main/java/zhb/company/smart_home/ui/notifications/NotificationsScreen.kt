package zhb.company.smart_home.ui.notifications

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.data.notifications.AlertPayload
import zhb.company.smart_home.data.notifications.InboxEntry
import zhb.company.smart_home.data.notifications.NotificationInbox
import zhb.company.smart_home.ui.components.AlertBanner
import zhb.company.smart_home.ui.components.ChoiceGroup
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.SectionLabel
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.format.Durations
import zhb.company.smart_home.ui.theme.AppTheme
import zhb.company.smart_home.ui.theme.SmartHomeTheme

class NotificationsViewModel(private val inbox: NotificationInbox, private val context: Context) : ViewModel() {
    val entries: StateFlow<List<InboxEntry>> = inbox.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), inbox.entries.value)

    fun label(entry: InboxEntry, label: String) {
        val id = entry.id ?: return
        // The notification may still be in the shade with its buttons: the question is answered.
        NotificationManagerCompat.from(context).cancel(entry.tag, 0)
        viewModelScope.launch { inbox.label(id, label, NotificationInbox.VIA_APP) }
    }
}

private enum class Filter { ALL, TO_LABEL }

private val SR = Locale.forLanguageTag("sr-Latn")

/**
 * Every notification that reached this phone, newest first and grouped by day. Problem
 * notifications ("node offline", "safe mode", "too cold", logger) can be labeled here at any
 * time, not only from the notification in the shade.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpenReport: (String) -> Unit, viewModel: NotificationsViewModel = koinViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    NotificationsContent(entries, onBack, onOpenReport, viewModel::label)
}

@Composable
private fun NotificationsContent(
    entries: List<InboxEntry>,
    onBack: () -> Unit,
    onOpenReport: (String) -> Unit,
    onLabel: (InboxEntry, String) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val toLabel = remember(entries) { entries.count { it.needsLabel } }
    var filter by rememberSaveable { mutableStateOf(if (toLabel > 0) Filter.TO_LABEL else Filter.ALL) }
    // Grouped once per list and filter, not on every recomposition.
    val days = remember(entries, filter) {
        val zone = ZoneId.systemDefault()
        val shown = if (filter == Filter.TO_LABEL) entries.filter { it.needsLabel } else entries
        shown.groupBy { Instant.ofEpochMilli(it.atMs).atZone(zone).toLocalDate() }
    }
    val resolutions = remember(entries) { resolutions(entries) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(title = stringResource(R.string.inbox_title), horizontalPadding = 0.dp, onBack = onBack) }
        if (entries.isEmpty()) {
            item { AlertBanner(Icons.Outlined.NotificationsNone, stringResource(R.string.inbox_empty), Tone.NEUTRAL) }
            return@LazyColumn
        }
        item {
            ChoiceGroup(
                options = listOf(
                    Filter.TO_LABEL to stringResource(R.string.inbox_filter_to_label, toLabel),
                    Filter.ALL to stringResource(R.string.inbox_filter_all),
                ),
                selected = filter,
                onSelect = { filter = it },
            )
        }
        if (filter == Filter.TO_LABEL) {
            item { AlertBanner(Icons.Outlined.Info, stringResource(R.string.inbox_label_help), Tone.NEUTRAL) }
            if (days.isEmpty()) item { AlertBanner(Icons.Outlined.CheckCircle, stringResource(R.string.inbox_all_labeled), Tone.ACTIVE) }
        }
        days.forEach { (day, items) ->
            item(key = "day-$day", contentType = "day") { SectionLabel(dayLabel(day, today)) }
            items(items, key = { it.key }, contentType = { "entry" }) { entry ->
                EntryCard(entry, resolutions[entry.key], onLabel, onOpenReport)
            }
        }
    }
}

@Composable
private fun EntryCard(entry: InboxEntry, resolved: InboxEntry?, onLabel: (InboxEntry, String) -> Unit, onOpenReport: (String) -> Unit) {
    val reportDate = entry.extras["date"].takeIf { entry.type == "report" }
    DashboardCard(onClick = reportDate?.let { { onOpenReport(it) } }) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                iconFor(entry),
                contentDescription = null,
                tint = if (entry.feedbackOffered) AppTheme.status.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp).size(20.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(time(entry.atMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                entry.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (entry.feedbackOffered) {
                    val status = if (resolved != null) {
                        stringResource(R.string.inbox_resolved_after, Durations.compact((resolved.atMs - entry.atMs).coerceAtLeast(0) / 1000))
                    } else {
                        stringResource(R.string.inbox_not_resolved)
                    }
                    Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (entry.feedbackOffered && entry.id != null) LabelRow(entry, onLabel)
    }
}

@Composable
private fun LabelRow(entry: InboxEntry, onLabel: (InboxEntry, String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        LabelButton(stringResource(R.string.feedback_ok), entry.feedback == AlertPayload.LABEL_OK, Modifier.weight(1f)) {
            onLabel(entry, AlertPayload.LABEL_OK)
        }
        LabelButton(stringResource(R.string.feedback_problem), entry.feedback == AlertPayload.LABEL_PROBLEM, Modifier.weight(1f), danger = true) {
            onLabel(entry, AlertPayload.LABEL_PROBLEM)
        }
    }
    val at = entry.feedbackMs
    if (entry.feedback != null && at != null) {
        val via = stringResource(if (entry.feedbackVia == NotificationInbox.VIA_NOTIFICATION) R.string.inbox_via_notification else R.string.inbox_via_app)
        Text(
            stringResource(R.string.inbox_labeled_at, time(at), via),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LabelButton(text: String, selected: Boolean, modifier: Modifier, danger: Boolean = false, onClick: () -> Unit) {
    if (selected) {
        val s = AppTheme.status
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (danger) s.dangerContainer else s.activeContainer,
                contentColor = if (danger) s.onDangerContainer else s.onActiveContainer,
            ),
        ) { Text(text, maxLines = 1) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text, maxLines = 1) }
    }
}

private fun iconFor(e: InboxEntry): ImageVector = when {
    e.type == "report" -> Icons.Outlined.Assessment
    e.type == "node_status" -> if (e.feedbackOffered) Icons.Outlined.WifiOff else Icons.Outlined.CheckCircle
    e.event == "cold" -> Icons.Outlined.Thermostat
    e.type == "heating" -> Icons.Outlined.LocalFireDepartment
    e.type == "system" -> Icons.Outlined.Dns
    else -> Icons.Outlined.NotificationsNone
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.inbox_today)
    today.minusDays(1) -> stringResource(R.string.inbox_yesterday)
    else -> day.format(DAY_FORMAT).replaceFirstChar { it.titlecase(SR) }
}

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, d. M.", SR)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", SR)

private fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

/**
 * The entry that resolved each problem, by the problem's key. [NotificationInbox.resolution] only
 * looks at entries with the same tag, so each problem is checked against its own tag's entries
 * instead of the whole inbox.
 */
private fun resolutions(entries: List<InboxEntry>): Map<String, InboxEntry> =
    entries.filter { it.tag != null }.groupBy { it.tag }.values
        .flatMap { group -> group.mapNotNull { e -> NotificationInbox.resolution(e, group)?.let { e.key to it } } }
        .toMap()

@Preview(name = "Notifications", heightDp = 1100)
@Composable
private fun NotificationsPreview() {
    val now = System.currentTimeMillis()
    val min = 60_000L
    val entries = listOf(
        InboxEntry("a", "a", "Čvor nije dostupan: Grejanje", "Grejanje (node5) se ne javlja od 18:40.", "node_status", "node-node5", "node_status", state = "offline", feedbackOffered = true, atMs = now - 30 * min, receivedMs = now - 28 * min),
        InboxEntry("b", "b", "Čvor ponovo radi: Ulazna vrata", "Nije bio dostupan 12 min.", "node_status", "node-node3", "node_status", state = "online", atMs = now - 60 * min, receivedMs = now - 60 * min),
        InboxEntry("c", "c", "Čvor nije dostupan: Ulazna vrata", "Ulazna vrata (node3) se ne javlja od 17:23.", "node_status", "node-node3", "node_status", state = "offline", feedbackOffered = true, atMs = now - 72 * min, receivedMs = now - 72 * min, feedback = "ok", feedbackMs = now - 50 * min, feedbackVia = "notification"),
        InboxEntry("d", null, "Izveštaj o grejanju: 19.11.", "Pumpa je radila 6 h 20 min.", "reports", "report-heating", "report", atMs = now - 26 * 60 * min, receivedMs = now - 26 * 60 * min, extras = mapOf("date" to "2026-11-19")),
    )
    SmartHomeTheme(darkTheme = false) { Surface { NotificationsContent(entries, {}, {}, { _, _ -> }) } }
}
