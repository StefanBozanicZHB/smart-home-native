package zhb.company.smart_home.ui.garden

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.garden.IrrigationDraft
import zhb.company.smart_home.domain.garden.IrrigationProblem
import zhb.company.smart_home.domain.garden.IrrigationRules
import zhb.company.smart_home.domain.garden.NextStart
import zhb.company.smart_home.domain.garden.Soak
import zhb.company.smart_home.domain.garden.Weekday
import zhb.company.smart_home.domain.heating.TimeOfDay
import zhb.company.smart_home.ui.components.AlertBanner
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.StatusChip
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.components.isLargeFont
import zhb.company.smart_home.ui.heating.ScheduleTimePickerDialog

/** The sprinkler plan on Node 1: days, start times, minutes per zone, soak cycles, rain pause. */
@Composable
fun IrrigationPlanScreen(onBack: () -> Unit, viewModel: IrrigationPlanViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val leave = { if (state.dirty) viewModel.onIntent(PlanIntent.RequestLeave) else onBack() }
    BackHandler(enabled = state.dirty) { viewModel.onIntent(PlanIntent.RequestLeave) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when (message) {
            PlanMessage.Saved -> resources.getString(R.string.heating_msg_saved)
            PlanMessage.NotSaved -> resources.getString(R.string.garden_msg_not_saved)
            PlanMessage.Paused -> resources.getString(R.string.plan_msg_paused)
            PlanMessage.PauseCleared -> resources.getString(R.string.plan_msg_pause_cleared)
            is PlanMessage.Rejected -> resources.getString(R.string.heating_msg_rejected, message.error)
            PlanMessage.Timeout -> resources.getString(R.string.heating_msg_timeout)
            PlanMessage.NotConnected -> resources.getString(R.string.heating_msg_not_connected)
        }
        viewModel.onIntent(PlanIntent.MessageShown)
        snackbar.showSnackbar(text)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        IrrigationPlanContent(state, viewModel::onIntent, onBack = leave)
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
    if (state.showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(PlanIntent.KeepEditing) },
            title = { Text(stringResource(R.string.plan_leave_title)) },
            text = { Text(stringResource(R.string.plan_leave_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onIntent(PlanIntent.Discard)
                    onBack()
                }) { Text(stringResource(R.string.heating_discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { viewModel.onIntent(PlanIntent.KeepEditing) }) { Text(stringResource(R.string.heating_leave_keep)) } },
        )
    }
}

@Composable
private fun IrrigationPlanContent(state: IrrigationPlanState, onIntent: (PlanIntent) -> Unit, onBack: () -> Unit) {
    val draft = state.draft
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(title = stringResource(R.string.plan_title), horizontalPadding = 0.dp, onBack = onBack) {
                if (state.dirty) StatusChip(stringResource(R.string.plan_unsaved), Tone.WARNING)
            }
        }
        if (draft == null) {
            item { AlertBanner(Icons.Outlined.HourglassEmpty, stringResource(R.string.irrigation_waiting), Tone.NEUTRAL) }
            return@LazyColumn
        }
        val enabled = state.connected && !state.saving
        item { AutoCard(draft, state.next, enabled, onIntent) }
        item { PauseCard(state.pausedUntil, enabled, onIntent) }
        item { DaysCard(draft, enabled, onIntent) }
        item { StartsCard(draft, state.limits.startsMax, enabled, onIntent) }
        item { ZonesCard(draft, state.limits.zoneMinutesMax, enabled, onIntent) }
        item { SoakCard(draft.soak, state.limits.soakOnMax, state.limits.soakOffMax, enabled, onIntent) }
        if (state.problems.isNotEmpty()) {
            item {
                AlertBanner(
                    Icons.Outlined.Close, stringResource(R.string.plan_problems), Tone.DANGER,
                    detail = state.problems.map { problemText(it) }.joinToString("\n"),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onIntent(PlanIntent.Discard) }, enabled = state.dirty && !state.saving, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.plan_discard))
                }
                Button(onClick = { onIntent(PlanIntent.Save) }, enabled = state.canSave, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (state.saving) R.string.plan_saving else R.string.settings_save))
                }
            }
        }
    }
}

@Composable
private fun AutoCard(draft: IrrigationDraft, next: NextStart?, enabled: Boolean, onIntent: (PlanIntent) -> Unit) {
    DashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(stringResource(R.string.plan_auto), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        !draft.auto -> stringResource(R.string.plan_auto_off_text)
                        next != null -> stringResource(R.string.irrigation_next, nextStartText(next))
                        else -> stringResource(R.string.irrigation_nothing_scheduled)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = draft.auto, onCheckedChange = { onIntent(PlanIntent.SetAuto(it)) }, enabled = enabled)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PauseCard(pausedUntil: String?, enabled: Boolean, onIntent: (PlanIntent) -> Unit) {
    DashboardCard {
        CardHeader(Icons.Outlined.Umbrella, stringResource(R.string.plan_pause)) {
            if (pausedUntil != null) StatusChip(stringResource(R.string.irrigation_paused_chip, shortDate(pausedUntil)), Tone.WARNING)
        }
        Text(
            if (pausedUntil != null) stringResource(R.string.plan_pause_active, shortDate(pausedUntil)) else stringResource(R.string.plan_pause_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { onIntent(PlanIntent.PauseFor(1)) }, enabled = enabled, label = { Text(stringResource(R.string.plan_pause_today)) })
            AssistChip(onClick = { onIntent(PlanIntent.PauseFor(2)) }, enabled = enabled, label = { Text(stringResource(R.string.plan_pause_tomorrow)) })
            AssistChip(onClick = { onIntent(PlanIntent.PauseFor(3)) }, enabled = enabled, label = { Text(stringResource(R.string.plan_pause_days, 3)) })
            AssistChip(onClick = { onIntent(PlanIntent.PauseFor(7)) }, enabled = enabled, label = { Text(stringResource(R.string.plan_pause_days, 7)) })
            if (pausedUntil != null) {
                AssistChip(onClick = { onIntent(PlanIntent.ClearPause) }, enabled = enabled, label = { Text(stringResource(R.string.plan_pause_clear)) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaysCard(draft: IrrigationDraft, enabled: Boolean, onIntent: (PlanIntent) -> Unit) {
    DashboardCard {
        CardHeader(Icons.Outlined.CalendarMonth, stringResource(R.string.plan_days)) {
            if (draft.days.size < Weekday.entries.size) {
                TextButton(onClick = { onIntent(PlanIntent.EveryDay) }, enabled = enabled) { Text(stringResource(R.string.plan_every_day)) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Weekday.entries.forEach { day ->
                FilterChip(
                    selected = day in draft.days,
                    onClick = { onIntent(PlanIntent.ToggleDay(day)) },
                    enabled = enabled,
                    label = { Text(weekdayShort(day)) },
                )
            }
        }
        if (draft.days.isEmpty()) {
            Text(stringResource(R.string.plan_no_days), style = MaterialTheme.typography.bodySmall, color = zhb.company.smart_home.ui.theme.AppTheme.status.warning)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartsCard(draft: IrrigationDraft, max: Int, enabled: Boolean, onIntent: (PlanIntent) -> Unit) {
    var editing by remember { mutableStateOf<Int?>(null) }
    DashboardCard {
        CardHeader(Icons.Outlined.Schedule, stringResource(R.string.plan_starts)) {
            Text(stringResource(R.string.plan_starts_count, draft.starts.size, max), style = MaterialTheme.typography.labelMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            draft.starts.forEachIndexed { i, time ->
                InputChip(
                    selected = false,
                    onClick = { editing = i },
                    enabled = enabled,
                    label = { Text(time, style = MaterialTheme.typography.titleSmall) },
                    trailingIcon = {
                        IconButton(onClick = { onIntent(PlanIntent.RemoveStart(i)) }, enabled = enabled) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.plan_remove_start, time))
                        }
                    },
                )
            }
            if (draft.starts.size < max) {
                AssistChip(
                    onClick = { onIntent(PlanIntent.AddStart) },
                    enabled = enabled,
                    leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    label = { Text(stringResource(R.string.plan_add_start)) },
                )
            }
        }
        if (draft.starts.isEmpty()) {
            Text(stringResource(R.string.plan_no_starts), style = MaterialTheme.typography.bodySmall, color = zhb.company.smart_home.ui.theme.AppTheme.status.warning)
        }
    }
    editing?.let { index ->
        ScheduleTimePickerDialog(
            initialMinute = TimeOfDay.parse(draft.starts.getOrNull(index)) ?: (6 * 60),
            onConfirm = {
                onIntent(PlanIntent.ChangeStart(index, TimeOfDay.format(it)))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ZonesCard(draft: IrrigationDraft, max: Int, enabled: Boolean, onIntent: (PlanIntent) -> Unit) {
    DashboardCard {
        val stacked = isLargeFont()
        val total = stringResource(R.string.plan_run_total, IrrigationRules.runMinutes(draft.zones))
        CardHeader(Icons.Outlined.Grass, stringResource(R.string.plan_zones)) {
            if (!stacked) Text(total, style = MaterialTheme.typography.labelMedium)
        }
        if (stacked) Text(total, style = MaterialTheme.typography.labelMedium)
        draft.zones.forEachIndexed { i, minutes ->
            val zone = i + 1
            val label: @Composable () -> Unit = { Text(stringResource(R.string.garden_zone_desc, zone), style = MaterialTheme.typography.bodyLarge) }
            val stepper: @Composable () -> Unit = {
                MinuteStepper(minutes, 0..max, onChange = { onIntent(PlanIntent.SetZone(zone, it)) }, enabled = enabled, zeroLabel = stringResource(R.string.plan_zone_off))
            }
            if (stacked) {
                Column { label(); stepper() }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) { label() }
                    stepper()
                }
            }
        }
        Text(stringResource(R.string.plan_zones_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SoakCard(soak: Soak, onMax: Int, offMax: Int, enabled: Boolean, onIntent: (PlanIntent) -> Unit) {
    DashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.plan_soak), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.plan_soak_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = soak.enabled, onCheckedChange = { onIntent(PlanIntent.SetSoakEnabled(it)) }, enabled = enabled)
        }
        if (soak.enabled) {
            SoakRow(stringResource(R.string.plan_soak_on), soak.onMinutes, 1..onMax, enabled) { onIntent(PlanIntent.SetSoak(soak.copy(onMinutes = it))) }
            SoakRow(stringResource(R.string.plan_soak_off), soak.offMinutes, 1..offMax, enabled) { onIntent(PlanIntent.SetSoak(soak.copy(offMinutes = it))) }
        }
    }
}

@Composable
private fun SoakRow(label: String, value: Int, range: IntRange, enabled: Boolean, onChange: (Int) -> Unit) {
    if (isLargeFont()) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            MinuteStepper(value, range, onChange, enabled = enabled)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            MinuteStepper(value, range, onChange, enabled = enabled)
        }
    }
}

@Composable
private fun problemText(problem: IrrigationProblem): String = when (problem) {
    is IrrigationProblem.TooManyStarts -> stringResource(R.string.plan_problem_starts, problem.max)
    is IrrigationProblem.BadStart -> stringResource(R.string.plan_problem_bad_start, problem.value)
    is IrrigationProblem.DuplicateStart -> stringResource(R.string.plan_problem_duplicate, problem.value)
    is IrrigationProblem.ZoneMinutes -> stringResource(R.string.plan_problem_zone, problem.zone, problem.max)
    is IrrigationProblem.SoakOn -> stringResource(R.string.plan_problem_soak_on, problem.max)
    is IrrigationProblem.SoakOff -> stringResource(R.string.plan_problem_soak_off, problem.max)
    IrrigationProblem.BadPauseDate -> stringResource(R.string.plan_problem_pause)
}

// ---- previews ------------------------------------------------------------------------------------

private val PreviewDraft = IrrigationDraft(
    auto = true, days = setOf(Weekday.MON, Weekday.WED, Weekday.FRI), starts = listOf("06:00", "20:30"),
    zones = listOf(10, 10, 0, 15, 10, 10), soak = Soak(5, 10), pauseUntil = null,
)

@androidx.compose.ui.tooling.preview.Preview(name = "Irrigation plan", heightDp = 1700)
@androidx.compose.ui.tooling.preview.Preview(name = "Irrigation plan - font 200%", heightDp = 3000, fontScale = 2f)
@Composable
private fun IrrigationPlanPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface {
            IrrigationPlanContent(
                IrrigationPlanState(connected = true, draft = PreviewDraft, dirty = true, pausedUntil = "2026-11-22", next = NextStart(Weekday.MON, "06:00", 3)),
                onIntent = {},
                onBack = {},
            )
        }
    }
}
