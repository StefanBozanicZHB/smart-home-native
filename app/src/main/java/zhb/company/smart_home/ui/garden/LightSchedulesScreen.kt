package zhb.company.smart_home.ui.garden

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Light
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Water
import androidx.compose.material.icons.outlined.WbIncandescent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.garden.Weekday
import zhb.company.smart_home.domain.schedule.OutputSchedule
import zhb.company.smart_home.domain.schedule.ScheduleProblem
import zhb.company.smart_home.domain.schedule.ScheduleTime
import zhb.company.smart_home.domain.schedule.SunEvent
import zhb.company.smart_home.domain.schedule.TodayWindow
import zhb.company.smart_home.ui.components.AlertBanner
import zhb.company.smart_home.ui.components.Appearing
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.ChoiceGroup
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.StatusChip
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.components.animatedItem
import zhb.company.smart_home.ui.heating.ScheduleTimePickerDialog
import zhb.company.smart_home.ui.theme.AppTheme

/** Daily schedules of the yard lights (Node 2) and the fountain (Node 1), one card per output. */
@Composable
fun LightSchedulesScreen(onBack: () -> Unit, viewModel: LightSchedulesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val leave = { if (state.anyDirty) viewModel.onIntent(ScheduleIntent.RequestLeave) else onBack() }
    BackHandler(enabled = state.anyDirty) { viewModel.onIntent(ScheduleIntent.RequestLeave) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when (message) {
            ScheduleMessage.Saved -> resources.getString(R.string.heating_msg_saved)
            ScheduleMessage.Removed -> resources.getString(R.string.schedules_msg_removed)
            ScheduleMessage.NotSaved -> resources.getString(R.string.garden_msg_not_saved)
            is ScheduleMessage.Rejected -> resources.getString(R.string.heating_msg_rejected, message.error)
            ScheduleMessage.Timeout -> resources.getString(R.string.heating_msg_timeout)
            ScheduleMessage.NotConnected -> resources.getString(R.string.heating_msg_not_connected)
        }
        viewModel.onIntent(ScheduleIntent.MessageShown)
        snackbar.showSnackbar(text)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LightSchedulesContent(state, viewModel::onIntent, onBack = leave)
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
    if (state.showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(ScheduleIntent.KeepEditing) },
            title = { Text(stringResource(R.string.plan_leave_title)) },
            text = { Text(stringResource(R.string.plan_leave_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onIntent(ScheduleIntent.DiscardAll)
                    onBack()
                }) { Text(stringResource(R.string.heating_discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { viewModel.onIntent(ScheduleIntent.KeepEditing) }) { Text(stringResource(R.string.heating_leave_keep)) } },
        )
    }
}

@Composable
private fun LightSchedulesContent(state: LightSchedulesState, onIntent: (ScheduleIntent) -> Unit, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        animatedItem("header") {
            ScreenHeader(title = stringResource(R.string.schedules_title), horizontalPadding = 0.dp, onBack = onBack)
        }
        if (state.outputs.isEmpty()) {
            animatedItem("waiting") { AlertBanner(Icons.Outlined.HourglassEmpty, stringResource(R.string.schedules_waiting), Tone.NEUTRAL) }
            return@LazyColumn
        }
        animatedItem("hint") {
            Text(
                stringResource(R.string.schedules_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.outputs.forEach { output ->
            animatedItem("${output.id.node}:${output.id.name}") { OutputCard(output, state.connected, onIntent) }
        }
    }
}

@Composable
private fun OutputCard(output: OutputScheduleUi, connected: Boolean, onIntent: (ScheduleIntent) -> Unit) {
    val id = output.id
    val schedule = output.schedule
    val enabled = connected && output.nodeOnline
    DashboardCard {
        CardHeader(outputIcon(id), outputLabel(id)) {
            if (output.dirty) StatusChip(stringResource(R.string.plan_unsaved), Tone.WARNING)
            else if (!output.nodeOnline) StatusChip(stringResource(R.string.home_node_offline_tile), Tone.DANGER)
        }
        // Today's window only means something for the schedule the node has.
        Appearing(todayText(output)) { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
        ChoiceGroup(
            options = listOf(false to stringResource(R.string.schedules_manual), true to stringResource(R.string.schedules_scheduled)),
            selected = schedule != null,
            onSelect = { onIntent(ScheduleIntent.SetScheduled(id, it)) },
            enabled = enabled,
        )
        Appearing(schedule == null) {
            Text(stringResource(R.string.schedules_manual_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Appearing(schedule) { s -> ScheduleEditor(id, s, enabled, onIntent) }
        Appearing(output.problem) { Text(problemText(it), style = MaterialTheme.typography.bodySmall, color = AppTheme.status.warning) }
        Appearing(output.dirty) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onIntent(ScheduleIntent.Discard(id)) }) { Text(stringResource(R.string.heating_discard)) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onIntent(ScheduleIntent.Save(id)) },
                    enabled = enabled && output.problem == null,
                ) { Text(stringResource(if (output.slow) R.string.plan_saving else R.string.settings_save)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditor(id: OutputId, s: OutputSchedule, enabled: Boolean, onIntent: (ScheduleIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeEditor(stringResource(R.string.schedules_on), s.on, isOff = false, enabled) { onIntent(ScheduleIntent.SetOn(id, it)) }
        TimeEditor(stringResource(R.string.schedules_off), s.off, isOff = true, enabled) { onIntent(ScheduleIntent.SetOff(id, it)) }
        Text(stringResource(R.string.schedules_days), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Weekday.entries.forEach { day ->
                FilterChip(
                    selected = day in s.days,
                    onClick = { onIntent(ScheduleIntent.ToggleDay(id, day)) },
                    enabled = enabled,
                    label = { Text(weekdayShort(day)) },
                )
            }
        }
    }
}

private enum class TimeKind { SUNSET, SUNRISE, FIXED }

/** Start or end: sunset / sunrise with an offset, or a fixed time of day. */
@Composable
private fun TimeEditor(title: String, time: ScheduleTime, isOff: Boolean, enabled: Boolean, onChange: (ScheduleTime) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val kind = when (time) {
        is ScheduleTime.Fixed -> TimeKind.FIXED
        is ScheduleTime.Sun -> if (time.event == SunEvent.SUNSET) TimeKind.SUNSET else TimeKind.SUNRISE
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceGroup(
            options = listOf(
                TimeKind.SUNSET to stringResource(R.string.schedules_sunset),
                TimeKind.SUNRISE to stringResource(R.string.schedules_sunrise),
                TimeKind.FIXED to stringResource(R.string.schedules_fixed),
            ),
            selected = kind,
            onSelect = { picked ->
                if (picked == kind) return@ChoiceGroup
                val offset = (time as? ScheduleTime.Sun)?.offsetMinutes ?: 0
                onChange(
                    when (picked) {
                        TimeKind.SUNSET -> ScheduleTime.Sun(SunEvent.SUNSET, offset)
                        TimeKind.SUNRISE -> ScheduleTime.Sun(SunEvent.SUNRISE, offset)
                        TimeKind.FIXED -> ScheduleTime.Fixed(if (isOff) DEFAULT_OFF_MINUTE else DEFAULT_ON_MINUTE)
                    },
                )
            },
            enabled = enabled,
        )
        when (time) {
            is ScheduleTime.Sun -> OffsetStepper(time.offsetMinutes, enabled) { onChange(time.copy(offsetMinutes = it)) }
            is ScheduleTime.Fixed -> OutlinedButton(onClick = { picking = true }, enabled = enabled) {
                Icon(Icons.Outlined.Schedule, contentDescription = null)
                Text(time.toWire(), modifier = Modifier.widthIn(min = 56.dp), textAlign = TextAlign.Center)
            }
        }
    }
    if (picking && time is ScheduleTime.Fixed) {
        ScheduleTimePickerDialog(
            initialMinute = time.minuteOfDay,
            onConfirm = {
                picking = false
                onChange(ScheduleTime.Fixed(it))
            },
            onDismiss = { picking = false },
        )
    }
}

/** − offset + in steps of [OFFSET_STEP] minutes, within ±[ScheduleTime.OFFSET_MAX]. */
@Composable
private fun OffsetStepper(offset: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val max = ScheduleTime.OFFSET_MAX
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onChange((offset - OFFSET_STEP).coerceIn(-max, max)) }, enabled = enabled && offset > -max) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.schedules_earlier))
        }
        Text(
            offsetText(offset),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 96.dp),
        )
        FilledTonalIconButton(onClick = { onChange((offset + OFFSET_STEP).coerceIn(-max, max)) }, enabled = enabled && offset < max) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.schedules_later))
        }
    }
}

@Composable
private fun offsetText(offset: Int): String = when {
    offset == 0 -> stringResource(R.string.schedules_offset_exact)
    offset > 0 -> stringResource(R.string.schedules_offset_after, offset)
    else -> stringResource(R.string.schedules_offset_before, -offset)
}

/** "danas 16:52–23:30" / "danas ne gori" for a saved schedule; null without one or before the state. */
@Composable
private fun todayText(output: OutputScheduleUi): String? {
    if (output.saved == null || !output.todayKnown) return null
    return output.today?.let { todayWindowText(it) } ?: stringResource(R.string.schedules_today_none)
}

@Composable
internal fun todayWindowText(window: TodayWindow): String = stringResource(R.string.schedules_today, window.on, window.off)

@Composable
private fun problemText(problem: ScheduleProblem): String = stringResource(
    when (problem) {
        ScheduleProblem.NO_DAYS -> R.string.schedules_problem_no_days
        ScheduleProblem.OFFSET_RANGE -> R.string.schedules_problem_offset
        ScheduleProblem.SAME_TIMES -> R.string.schedules_problem_same
    },
)

/** Known outputs get their usual names; one a newer firmware adds shows its key. */
@Composable
internal fun outputLabel(id: OutputId): String = when (id.node to id.name) {
    ScheduleNode.NODE2 to "neon" -> stringResource(R.string.garden_led_strip)
    ScheduleNode.NODE2 to "lamp" -> stringResource(R.string.garden_bulb)
    ScheduleNode.NODE1 to "pump" -> stringResource(R.string.garden_fountain_pump)
    ScheduleNode.NODE1 to "light" -> stringResource(R.string.garden_fountain_light)
    else -> id.name
}

private fun outputIcon(id: OutputId): ImageVector = when (id.node to id.name) {
    ScheduleNode.NODE2 to "neon" -> Icons.Outlined.Lightbulb
    ScheduleNode.NODE2 to "lamp" -> Icons.Outlined.WbIncandescent
    ScheduleNode.NODE1 to "pump" -> Icons.Outlined.Water
    ScheduleNode.NODE1 to "light" -> Icons.Outlined.Light
    else -> Icons.Outlined.Schedule
}

private const val OFFSET_STEP = 5
private const val DEFAULT_ON_MINUTE = 18 * 60
private const val DEFAULT_OFF_MINUTE = 23 * 60 + 30

// ---- previews ------------------------------------------------------------------------------------

@androidx.compose.ui.tooling.preview.Preview(name = "Light schedules", heightDp = 1500)
@androidx.compose.ui.tooling.preview.Preview(name = "Light schedules - font 200%", heightDp = 3000, fontScale = 2f)
@Composable
private fun LightSchedulesPreview() {
    val lamp = OutputSchedule.DEFAULT
    val pump = OutputSchedule(ScheduleTime.Fixed(10 * 60), ScheduleTime.Sun(SunEvent.SUNSET, -30), setOf(Weekday.SAT, Weekday.SUN))
    fun out(node: ScheduleNode, name: String, saved: OutputSchedule?, shown: OutputSchedule?, today: TodayWindow?) = OutputScheduleUi(
        id = OutputId(node, name), nodeOnline = true, schedule = shown, saved = saved, dirty = shown != saved,
        problem = null, today = today, todayKnown = true, saving = false, slow = false,
    )
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        androidx.compose.material3.Surface {
            LightSchedulesContent(
                LightSchedulesState(
                    connected = true,
                    outputs = listOf(
                        out(ScheduleNode.NODE2, "neon", null, null, null),
                        out(ScheduleNode.NODE2, "lamp", lamp, lamp, TodayWindow("16:52", "23:30")),
                        out(ScheduleNode.NODE1, "pump", pump, pump.copy(off = ScheduleTime.Sun(SunEvent.SUNSET, -45)), null),
                        out(ScheduleNode.NODE1, "light", null, null, null),
                    ),
                ),
                onIntent = {},
                onBack = {},
            )
        }
    }
}
