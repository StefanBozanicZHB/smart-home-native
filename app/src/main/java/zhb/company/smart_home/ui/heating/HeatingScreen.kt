package zhb.company.smart_home.ui.heating

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.heating.DayType
import zhb.company.smart_home.domain.heating.Hysteresis
import zhb.company.smart_home.domain.heating.Node5Limits
import zhb.company.smart_home.domain.heating.ScheduleProblem
import zhb.company.smart_home.domain.heating.ScheduleRules
import zhb.company.smart_home.domain.heating.ScheduleSegment
import zhb.company.smart_home.domain.heating.TimeOfDay
import zhb.company.smart_home.domain.heating.formatTenths
import zhb.company.smart_home.ui.components.PulsingStatusDot
import zhb.company.smart_home.ui.theme.DisableRed
import zhb.company.smart_home.ui.theme.EnableGreen
import zhb.company.smart_home.ui.theme.PanelBorderTeal
import zhb.company.smart_home.ui.theme.PrimaryColor
import zhb.company.smart_home.ui.theme.White

private val PipeOffset = 22.dp
private val PipeWidth = 5.dp
private const val TEMP_STEP_TENTHS = 1

@Composable
fun HeatingScreen(
    viewModel: HeatingViewModel = koinViewModel(),
    onOpenHome: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenGarden: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenHouse: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenShed: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when (message) {
            HeatingMessage.Saved -> context.getString(R.string.heating_msg_saved)
            is HeatingMessage.Rejected -> context.getString(R.string.heating_msg_rejected, message.error)
            HeatingMessage.Timeout -> context.getString(R.string.heating_msg_timeout)
            HeatingMessage.NotConnected -> context.getString(R.string.heating_msg_not_connected)
        }
        viewModel.onIntent(HeatingIntent.MessageShown)
        snackbar.showSnackbar(text)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        HeatingContent(state, viewModel::onIntent, padding, onOpenHome)
    }

    if (state.showSettingsDialog) HeatingSettingsDialog(state, viewModel::onIntent)
    if (state.showMainSwitchOffConfirm) MainSwitchOffDialog(viewModel::onIntent)
}

@Composable
private fun HeatingContent(
    state: HeatingState,
    onIntent: (HeatingIntent) -> Unit,
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        item { HeatingTitleRow(state, onBack = onBack, onIntent = onIntent) }
        if (state.sending) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        item { StatusBanners(state, onIntent) }
        item { HeatingDiagramCard(state, onIntent) }
        if (!state.loading) item { ScheduleCard(state, onIntent) }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun HeatingTitleRow(state: HeatingState, onBack: () -> Unit, onIntent: (HeatingIntent) -> Unit) {
    val online = state.online == true
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
        }
        Text(
            text = stringResource(R.string.menu_heating),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (state.nodeId != "node5") {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = stringResource(R.string.heating_test_board),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        PulsingStatusDot(online = online)
        Text(
            text = stringResource(if (online) R.string.connections_online else R.string.connections_offline),
            style = MaterialTheme.typography.labelSmall,
            color = if (online) EnableGreen else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 6.dp, end = 4.dp),
        )
        IconButton(onClick = { onIntent(HeatingIntent.OpenSettingsDialog) }, enabled = !state.loading) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.heating_settings_title))
        }
    }
}

/** Connection, node and safe-mode problems, most fundamental first. */
@Composable
private fun StatusBanners(state: HeatingState, onIntent: (HeatingIntent) -> Unit) {
    when {
        !state.connected -> Banner(stringResource(R.string.heating_not_connected), null)
        state.online == false -> Banner(stringResource(R.string.heating_node_offline), null)
        state.loading -> Banner(stringResource(R.string.heating_waiting_for_node, state.nodeId), null) {
            TextButton(onClick = { onIntent(HeatingIntent.Refresh) }) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.heating_refresh), modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
    state.safeMode?.let { safe ->
        Banner(stringResource(R.string.heating_safe_mode_title), safeModeText(safe.reason))
    }
}

@Composable
private fun Banner(title: String, detail: String?, action: (@Composable () -> Unit)? = null) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            action?.invoke()
        }
    }
}

@Composable
private fun safeModeText(reason: String?): String = when (reason) {
    "startup" -> stringResource(R.string.heating_safe_startup)
    "no_time" -> stringResource(R.string.heating_safe_no_time)
    "mqtt_down" -> stringResource(R.string.heating_safe_mqtt_down)
    "no_schedule" -> stringResource(R.string.heating_safe_no_schedule)
    "sensor_offline" -> stringResource(R.string.heating_safe_sensor_offline)
    "room_temp_stale" -> stringResource(R.string.heating_safe_room_temp_stale)
    else -> stringResource(R.string.heating_safe_unknown, reason ?: "?")
}

@Composable
private fun zoneName(id: String): String = when (id) {
    "ground" -> stringResource(R.string.heating_zone_ground)
    "upper" -> stringResource(R.string.heating_zone_upper)
    else -> id
}

@Composable
private fun faultText(fault: String): String = when (fault) {
    "no_schedule" -> stringResource(R.string.heating_fault_no_schedule)
    "sensor_offline" -> stringResource(R.string.heating_fault_sensor_offline)
    "room_temp_stale" -> stringResource(R.string.heating_fault_room_temp_stale)
    else -> fault
}

@Composable
private fun pumpSourceText(source: String): String = when (source) {
    "thermostat" -> stringResource(R.string.heating_source_thermostat)
    "schedule" -> stringResource(R.string.heating_source_schedule)
    "app" -> stringResource(R.string.heating_source_app)
    "safe_mode" -> stringResource(R.string.heating_source_safe_mode)
    "boot" -> stringResource(R.string.heating_source_boot)
    else -> source
}

/**
 * Outdoor temperature, one row per zone (room temperature and current target), and the heat
 * pump at the bottom, with pipes from the pump to the zones that turn green while it runs.
 */
@Composable
private fun HeatingDiagramCard(state: HeatingState, onIntent: (HeatingIntent) -> Unit) {
    val density = LocalDensity.current
    val pipeColor = if (state.pump) EnableGreen else MaterialTheme.colorScheme.outlineVariant
    val pipeOffsetPx = with(density) { PipeOffset.toPx() }
    val pipeWidthPx = with(density) { PipeWidth.toPx() }

    var boxRootY by remember { mutableFloatStateOf(0f) }
    var topZoneY by remember { mutableFloatStateOf(0f) }
    var pumpRowTopY by remember { mutableFloatStateOf(0f) }

    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .onGloballyPositioned { boxRootY = it.positionInRoot().y },
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                if (pumpRowTopY > 0f && topZoneY > 0f) {
                    val centerX = size.width / 2f
                    listOf(-pipeOffsetPx, pipeOffsetPx).forEach { dx ->
                        drawLine(
                            color = pipeColor,
                            start = Offset(centerX + dx, pumpRowTopY - boxRootY),
                            end = Offset(centerX + dx, topZoneY - boxRootY),
                            strokeWidth = pipeWidthPx,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                DiagramRow(
                    icon = Icons.Filled.Thermostat,
                    label = stringResource(R.string.heating_outdoor_temp),
                    value = "${state.outdoorTemp}°C",
                    detail = null,
                    active = false,
                )
                state.zones.forEachIndexed { index, zone ->
                    val detail = buildList {
                        zone.target?.let { add(stringResource(R.string.heating_target, it)) }
                        if (zone.demand) add(stringResource(R.string.heating_demand))
                        zone.fault?.let { add(faultText(it)) }
                    }.joinToString(" · ").ifEmpty { null }
                    DiagramRow(
                        icon = Icons.Filled.Thermostat,
                        label = zoneName(zone.id),
                        value = "${zone.roomTemp}°C",
                        detail = detail,
                        active = state.pump && zone.demand,
                        warning = zone.fault != null,
                        modifier = if (index == 0) {
                            // Pipes end at the bottom edge of the row, so they never cross its text.
                            Modifier.onGloballyPositioned { topZoneY = it.positionInRoot().y + it.size.height }
                        } else Modifier,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                HeatPumpDock(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.onGloballyPositioned { pumpRowTopY = it.positionInRoot().y },
                )
                state.nodeTime?.let { time ->
                    val dayLabel = stringResource(if (state.nodeIsWeekend == true) R.string.heating_weekend else R.string.heating_workday)
                    Text(
                        text = stringResource(R.string.heating_node_time, time, dayLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagramRow(
    icon: ImageVector,
    label: String,
    value: String,
    detail: String?,
    active: Boolean,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Surface(shape = CircleShape, color = if (active) PrimaryColor else MaterialTheme.colorScheme.outlineVariant) {
            Icon(icon, contentDescription = null, tint = White, modifier = Modifier.padding(9.dp).size(18.dp))
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (warning) DisableRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeatPumpDock(state: HeatingState, onIntent: (HeatingIntent) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = if (state.pump) PrimaryColor.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Surface(shape = CircleShape, color = if (state.pump) PrimaryColor else MaterialTheme.colorScheme.outlineVariant) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = White, modifier = Modifier.padding(10.dp).size(20.dp))
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(stringResource(R.string.heating_heat_pump), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringResource(if (state.pump) R.string.heating_pump_running else R.string.heating_pump_idle),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.pump) EnableGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${stringResource(R.string.heating_flow_temp)}: ${state.flowTemp}°C",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.lastPumpChange?.let { change ->
                    val whenText = buildString {
                        change.minutesAgo?.let { append(stringResource(R.string.heating_minutes_ago, it)).append(", ") }
                        append(pumpSourceText(change.source))
                    }
                    Text(
                        text = stringResource(if (change.on) R.string.heating_last_change_on else R.string.heating_last_change_off, whenText),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = state.mainSwitch,
                    onCheckedChange = { onIntent(HeatingIntent.ToggleMainSwitch(it)) },
                    enabled = state.connected && !state.loading && !state.sending,
                    colors = SwitchDefaults.colors(checkedTrackColor = PrimaryColor),
                )
                Text(stringResource(R.string.heating_main_switch), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleCard(state: HeatingState, onIntent: (HeatingIntent) -> Unit) {
    var editingStartIndex by remember { mutableStateOf<Int?>(null) }
    val segments = state.editorSegments

    Card(modifier = Modifier.padding(16.dp).fillMaxWidth(), border = BorderStroke(2.dp, PanelBorderTeal)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.heating_schedule_title), style = MaterialTheme.typography.titleSmall)

            if (state.zones.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    state.zones.forEach { zone ->
                        FilterChip(
                            selected = zone.id == state.selectedZone,
                            onClick = { onIntent(HeatingIntent.SelectZone(zone.id)) },
                            label = { Text(zoneName(zone.id)) },
                        )
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                DayType.entries.forEachIndexed { index, day ->
                    val isToday = state.nodeIsWeekend != null && state.nodeIsWeekend == (day == DayType.WEEKEND)
                    val label = stringResource(if (day == DayType.WORKDAY) R.string.heating_workday else R.string.heating_weekend)
                    SegmentedButton(
                        selected = state.selectedDay == day,
                        onClick = { onIntent(HeatingIntent.SelectDay(day)) },
                        shape = SegmentedButtonDefaults.itemShape(index, DayType.entries.size),
                        label = { Text(if (isToday) "$label (${stringResource(R.string.heating_today)})" else label) },
                    )
                }
            }

            if (state.dayNotSet && !state.scheduleDirty) Hint(stringResource(R.string.heating_day_not_set), warning = false)
            if (state.remoteChangedWhileEditing) Hint(stringResource(R.string.heating_remote_changed), warning = true)

            segments.forEachIndexed { index, segment ->
                ScheduleSegmentRow(
                    segment = segment,
                    endMinute = ScheduleRules.endMinute(segments, index),
                    startEditable = index > 0,
                    onStartClick = { editingStartIndex = index },
                    limits = state.limits,
                    onTempChange = { delta -> onIntent(HeatingIntent.ChangeSegmentTemp(index, delta)) },
                    onDelete = if (segments.size > 1) ({ onIntent(HeatingIntent.RemoveSegment(index)) }) else null,
                )
            }

            state.scheduleProblem?.let { Hint(problemText(it), warning = true) }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                if (segments.size < state.limits.segmentsMax) {
                    TextButton(onClick = { onIntent(HeatingIntent.AddSegment) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.heating_add_segment), modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (state.scheduleDirty) {
                    OutlinedButton(onClick = { onIntent(HeatingIntent.DiscardSchedule) }, enabled = !state.sending) {
                        Text(stringResource(R.string.heating_discard))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Button(
                    onClick = { onIntent(HeatingIntent.SaveSchedule) },
                    enabled = (state.scheduleDirty || state.dayNotSet) && state.scheduleProblem == null &&
                        state.connected && !state.sending,
                ) { Text(stringResource(R.string.heating_save)) }
            }
        }
    }

    editingStartIndex?.let { index ->
        segments.getOrNull(index)?.let { segment ->
            ScheduleTimePickerDialog(
                initialMinute = segment.startMinute,
                onConfirm = { minute ->
                    onIntent(HeatingIntent.SetSegmentStart(index, minute))
                    editingStartIndex = null
                },
                onDismiss = { editingStartIndex = null },
            )
        }
    }
}

@Composable
private fun Hint(text: String, warning: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun problemText(problem: ScheduleProblem): String = when (problem) {
    is ScheduleProblem.SegmentCount -> stringResource(R.string.heating_problem_segments, problem.max)
    ScheduleProblem.FirstNotMidnight -> stringResource(R.string.heating_problem_first_midnight)
    is ScheduleProblem.StartsNotRising -> stringResource(R.string.heating_problem_rising, problem.index + 1)
    is ScheduleProblem.TempOutOfRange -> stringResource(
        R.string.heating_problem_temp, problem.index + 1, formatTenths(problem.minTenths), formatTenths(problem.maxTenths),
    )
    is ScheduleProblem.HysteresisOutOfRange -> stringResource(R.string.heating_problem_hysteresis, formatTenths(problem.maxTenths))
}

@Composable
private fun ScheduleSegmentRow(
    segment: ScheduleSegment,
    endMinute: Int,
    startEditable: Boolean,
    onStartClick: () -> Unit,
    limits: Node5Limits,
    onTempChange: (Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (startEditable) {
            Surface(onClick = onStartClick, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = stringResource(R.string.heating_pick_time), modifier = Modifier.size(14.dp))
                    Text(
                        TimeOfDay.format(segment.startMinute),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        } else {
            Text(
                TimeOfDay.format(segment.startMinute),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Text("–", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 4.dp))
        Text(TimeOfDay.format(endMinute), style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.weight(1f))
        TenthsStepper(
            tenths = segment.tempTenths,
            unit = "°C",
            canDecrease = segment.tempTenths > limits.tempMinTenths,
            canIncrease = segment.tempTenths < limits.tempMaxTenths,
            onDecrease = { onTempChange(-TEMP_STEP_TENTHS) },
            onIncrease = { onTempChange(TEMP_STEP_TENTHS) },
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.heating_remove_segment), modifier = Modifier.size(16.dp))
            }
        } else {
            Spacer(modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun TenthsStepper(
    tenths: Int,
    unit: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDecrease, enabled = canDecrease, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.heating_temp_decrease), modifier = Modifier.size(16.dp))
        }
        Text(
            text = "${formatTenths(tenths)}$unit",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        IconButton(onClick = onIncrease, enabled = canIncrease, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.heating_temp_increase), modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimePickerDialog(initialMinute: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.heating_pick_time), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                TimePicker(state = pickerState)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.heating_cancel)) }
                    TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun HeatingSettingsDialog(state: HeatingState, onIntent: (HeatingIntent) -> Unit) {
    val initial = state.hysteresis ?: DefaultHysteresis
    var lower by remember { mutableIntStateOf(initial.lowerTenths) }
    var upper by remember { mutableIntStateOf(initial.upperTenths) }
    val max = state.limits.stepMaxTenths
    val zoneLabel = state.selectedZone?.let { zoneName(it) }

    AlertDialog(
        onDismissRequest = { onIntent(HeatingIntent.DismissSettingsDialog) },
        title = { Text(listOfNotNull(stringResource(R.string.heating_settings_title), zoneLabel).joinToString(" · ")) },
        confirmButton = {
            TextButton(
                onClick = { onIntent(HeatingIntent.SaveHysteresis(Hysteresis(lower, upper))) },
                enabled = state.connected && !state.sending && Hysteresis(lower, upper) != state.hysteresis,
            ) { Text(stringResource(R.string.heating_save)) }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(HeatingIntent.DismissSettingsDialog) }) { Text(stringResource(R.string.heating_cancel)) }
        },
        text = {
            Column {
                Text(stringResource(R.string.heating_hysteresis_help), style = MaterialTheme.typography.bodySmall)
                HysteresisRow(stringResource(R.string.heating_hysteresis_lower), lower, max) { lower = it }
                HysteresisRow(stringResource(R.string.heating_hysteresis_upper), upper, max) { upper = it }
                if (state.minOnSeconds != null && state.minOffSeconds != null) {
                    Text(
                        stringResource(R.string.heating_min_run_pause, state.minOnSeconds / 60, state.minOffSeconds / 60),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                state.configRev?.let {
                    Text(stringResource(R.string.heating_config_rev, it, state.nodeId), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
    )
}

@Composable
private fun HysteresisRow(label: String, tenths: Int, maxTenths: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        TenthsStepper(
            tenths = tenths,
            unit = " °C",
            canDecrease = tenths > 0,
            canIncrease = tenths < maxTenths,
            onDecrease = { onChange((tenths - 1).coerceAtLeast(0)) },
            onIncrease = { onChange((tenths + 1).coerceAtMost(maxTenths)) },
        )
    }
}

@Composable
private fun MainSwitchOffDialog(onIntent: (HeatingIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(HeatingIntent.DismissMainSwitchOff) },
        title = { Text(stringResource(R.string.heating_main_switch_off_title)) },
        text = { Text(stringResource(R.string.heating_main_switch_off_text)) },
        confirmButton = {
            TextButton(onClick = { onIntent(HeatingIntent.ConfirmMainSwitchOff) }) {
                Text(stringResource(R.string.heating_turn_off), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(HeatingIntent.DismissMainSwitchOff) }) { Text(stringResource(R.string.heating_cancel)) }
        },
    )
}

// ---- previews (rendered with `android preview render`, no device needed) ----------------------

private val PreviewLimits = Node5Limits()

private val PreviewRunning = HeatingState(
    nodeId = "node5",
    connected = true,
    online = true,
    loading = false,
    outdoorTemp = "3.50",
    mainSwitch = true,
    pump = true,
    demand = true,
    flowTemp = "34.3",
    nodeTime = "21:35",
    nodeIsWeekend = false,
    lastPumpChange = PumpChangeUi(on = true, source = "thermostat", minutesAgo = 12),
    zones = listOf(ZoneUi("ground", roomTemp = "21.2", target = "21.5", demand = true, fault = null)),
    limits = PreviewLimits,
    selectedZone = "ground",
    selectedDay = DayType.WORKDAY,
    editorSegments = listOf(ScheduleSegment(0, 195), ScheduleSegment(360, 215), ScheduleSegment(1350, 195)),
    scheduleDirty = true,
    hysteresis = Hysteresis(2, 1),
    minOnSeconds = 600,
    minOffSeconds = 600,
    configRev = 9,
)

@androidx.compose.ui.tooling.preview.Preview(name = "Running, edited workday", heightDp = 1100)
@Composable
private fun HeatingRunningPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface { HeatingContent(PreviewRunning, onIntent = {}, padding = PaddingValues(), onBack = {}) }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Bench board, safe mode, no weekend program", heightDp = 1100)
@Composable
private fun HeatingSafeModePreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = true) {
        Surface {
            HeatingContent(
                PreviewRunning.copy(
                    nodeId = "testnode5",
                    pump = false,
                    demand = false,
                    flowTemp = "--",
                    nodeIsWeekend = true,
                    selectedDay = DayType.WEEKEND,
                    safeMode = SafeModeUi("no_schedule", "ground"),
                    lastPumpChange = PumpChangeUi(on = false, source = "safe_mode", minutesAgo = 3),
                    zones = listOf(ZoneUi("ground", roomTemp = "20.9", target = null, demand = false, fault = "no_schedule")),
                    editorSegments = ScheduleRules.defaultDay(PreviewLimits),
                    dayNotSet = true,
                    scheduleDirty = false,
                ),
                onIntent = {},
                padding = PaddingValues(),
                onBack = {},
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Invalid draft and remote change", heightDp = 1100)
@Composable
private fun HeatingInvalidDraftPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface {
            HeatingContent(
                PreviewRunning.copy(
                    editorSegments = listOf(ScheduleSegment(0, 195), ScheduleSegment(480, 215), ScheduleSegment(420, 195)),
                    scheduleProblem = ScheduleProblem.StartsNotRising(2),
                    remoteChangedWhileEditing = true,
                ),
                onIntent = {},
                padding = PaddingValues(),
                onBack = {},
            )
        }
    }
}
