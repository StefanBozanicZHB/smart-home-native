package zhb.company.smart_home.ui.heating

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.heating.DayType
import zhb.company.smart_home.domain.heating.Hysteresis
import zhb.company.smart_home.domain.heating.MINUTES_PER_DAY
import zhb.company.smart_home.domain.heating.Node5Limits
import zhb.company.smart_home.domain.heating.ScheduleProblem
import zhb.company.smart_home.domain.heating.ScheduleRules
import zhb.company.smart_home.domain.heating.ScheduleSegment
import zhb.company.smart_home.domain.heating.TimeOfDay
import zhb.company.smart_home.domain.heating.formatTenths
import zhb.company.smart_home.ui.components.AlertBanner
import zhb.company.smart_home.ui.components.BigValue
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.StatTile
import zhb.company.smart_home.ui.components.StatusChip
import zhb.company.smart_home.ui.components.TileShape
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.components.isLargeFont
import zhb.company.smart_home.ui.theme.AppTheme

private const val TEMP_STEP_TENTHS = 1

@Composable
fun HeatingScreen(onBack: () -> Unit, onOpenSensors: () -> Unit, viewModel: HeatingViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Leaving drops the view model and with it every unsaved schedule draft, so ask first.
    BackHandler(enabled = state.hasUnsavedDrafts) { viewModel.onIntent(HeatingIntent.RequestLeave) }
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

    Box(modifier = Modifier.fillMaxSize()) {
        HeatingContent(state, viewModel::onIntent, onOpenSensors = onOpenSensors)
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (state.showSettingsDialog) HeatingSettingsDialog(state, viewModel::onIntent)
    if (state.showMainSwitchOffConfirm) MainSwitchOffDialog(viewModel::onIntent)
    if (state.showDiscardDraftsConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(HeatingIntent.KeepEditing) },
            title = { Text(stringResource(R.string.heating_leave_title)) },
            text = { Text(stringResource(R.string.heating_leave_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onIntent(HeatingIntent.DiscardAllDrafts)
                    onBack()
                }) { Text(stringResource(R.string.heating_discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { viewModel.onIntent(HeatingIntent.KeepEditing) }) { Text(stringResource(R.string.heating_leave_keep)) } },
        )
    }
}

@Composable
private fun HeatingContent(
    state: HeatingState,
    onIntent: (HeatingIntent) -> Unit,
    initiallyEditing: Boolean = false,
    onOpenSensors: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { HeatingHeader(state, onIntent) }
        if (state.sending) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        item { StatusBanners(state, onIntent) }
        if (!state.loading) {
            if (state.zones.size > 1) item { ZoneSelector(state, onIntent) }
            item { ZoneCard(state) }
        }
        item { HeatPumpCard(state, onIntent, onOpenSensors) }
        if (!state.loading) item { ScheduleCard(state, onIntent, initiallyEditing) }
        // The flow temperature is already in the heat pump card; outdoor explains how hard it works.
        item { StatTile(stringResource(R.string.home_outdoor), "${state.outdoorTemp}°", Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun HeatingHeader(state: HeatingState, onIntent: (HeatingIntent) -> Unit) {
    val subtitle = state.nodeTime?.let { time ->
        val dayLabel = stringResource(if (state.nodeIsWeekend == true) R.string.heating_weekend else R.string.heating_workday)
        stringResource(R.string.heating_node_time, time, dayLabel)
    }
    ScreenHeader(title = stringResource(R.string.menu_heating), subtitle = subtitle, horizontalPadding = 0.dp) {
        if (state.nodeId != "node5") StatusChip(stringResource(R.string.heating_test_board), Tone.WARNING, Modifier.padding(end = 6.dp))
        StatusChip(
            text = stringResource(
                when (state.online) {
                    true -> R.string.connections_online
                    false -> R.string.connections_offline
                    null -> R.string.connections_unknown
                },
            ),
            tone = when (state.online) {
                true -> Tone.ACTIVE
                false -> Tone.DANGER
                null -> Tone.NEUTRAL
            },
        )
        IconButton(onClick = { onIntent(HeatingIntent.OpenSettingsDialog) }, enabled = !state.loading) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.heating_settings_title))
        }
    }
}

/** Connection, node and safe-mode problems, most fundamental first. */
@Composable
private fun StatusBanners(state: HeatingState, onIntent: (HeatingIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            !state.connected -> AlertBanner(Icons.Outlined.CloudOff, stringResource(R.string.heating_not_connected), Tone.DANGER)
            state.online == false -> AlertBanner(Icons.Outlined.WarningAmber, stringResource(R.string.heating_node_offline), Tone.DANGER)
            state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                AlertBanner(
                    Icons.Outlined.HourglassEmpty,
                    stringResource(R.string.heating_waiting_for_node, state.nodeId),
                    Tone.NEUTRAL,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onIntent(HeatingIntent.Refresh) }) { Text(stringResource(R.string.heating_refresh)) }
            }
        }
        state.safeMode?.let { safe ->
            AlertBanner(Icons.Outlined.WarningAmber, stringResource(R.string.heating_safe_mode_title), Tone.WARNING, detail = safeModeText(safe.reason))
        }
    }
}

@Composable
private fun ZoneSelector(state: HeatingState, onIntent: (HeatingIntent) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        state.zones.forEachIndexed { index, zone ->
            SegmentedButton(
                selected = zone.id == state.selectedZone,
                onClick = { onIntent(HeatingIntent.SelectZone(zone.id)) },
                shape = SegmentedButtonDefaults.itemShape(index, state.zones.size),
                label = { Text(zoneName(zone.id)) },
            )
        }
    }
}

/** Selected zone: room temperature in a ring that fills toward the target, target, demand and next change. */
@Composable
private fun ZoneCard(state: HeatingState) {
    val zone = state.zones.firstOrNull { it.id == state.selectedZone } ?: return
    DashboardCard {
        val ring: @Composable () -> Unit = { RoomVsTargetRing(zone.roomTempValue, zone.targetTenths, zone.roomTemp, zone.target, warm = zone.demand) }
        val details: @Composable (Modifier) -> Unit = { m ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = m) {
                Text(zoneName(zone.id), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (zone.demand) StatusChip(stringResource(R.string.heating_demand), Tone.ACTIVE)
                    zone.fault?.let { StatusChip(faultText(it), Tone.DANGER) }
                }
                val next = state.nextChange
                Text(
                    if (next != null) stringResource(R.string.heating_next_change, next.time, next.temp) else stringResource(R.string.heating_no_change_today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.hysteresis?.let { h ->
                    Text(
                        stringResource(R.string.heating_hysteresis_short, formatTenths(h.lowerTenths), formatTenths(h.upperTenths)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // With a large system font the ring goes above the text instead of next to it.
        if (isLargeFont()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ring()
                details(Modifier.fillMaxWidth())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ring()
                details(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Ring around the room temperature. The arc covers target −2 °C … target +1 °C, so a room right
 * at the target shows two thirds filled; it is warm-colored while the zone demands heat.
 */
@Composable
private fun RoomVsTargetRing(room: Double?, targetTenths: Int?, roomText: String, targetText: String?, warm: Boolean) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = if (warm) AppTheme.status.warm else AppTheme.status.active
    val fraction = if (room != null && targetTenths != null) {
        val low = targetTenths / 10.0 - 2.0
        ((room - low) / 3.0).coerceIn(0.0, 1.0).toFloat()
    } else 0f
    // The ring grows with the system font so the temperature always fits inside.
    val ringSize = 104.dp * LocalDensity.current.fontScale.coerceIn(1f, 2f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, startAngle = 135f, sweepAngle = 270f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (fraction > 0f) {
                drawArc(fill, startAngle = 135f, sweepAngle = 270f * fraction, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$roomText°", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (targetText != null) {
                Text(stringResource(R.string.home_target, targetText), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Heat pump: running state, main switch, last change and the four pipes (well side, heating side). */
@Composable
private fun HeatPumpCard(state: HeatingState, onIntent: (HeatingIntent) -> Unit, onOpenSensors: () -> Unit) {
    DashboardCard {
        CardHeader(Icons.Outlined.SwapVert, stringResource(R.string.heating_heat_pump)) {
            TextButton(onClick = onOpenSensors) { Text(stringResource(R.string.sensors_open)) }
            StatusChip(
                stringResource(if (state.pump) R.string.heating_pump_running else R.string.heating_pump_idle),
                if (state.pump) Tone.ACTIVE else Tone.NEUTRAL,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.heating_main_switch), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = state.mainSwitch,
                onCheckedChange = { onIntent(HeatingIntent.ToggleMainSwitch(it)) },
                enabled = state.connected && !state.loading && !state.sending,
            )
        }
        state.lastPumpChange?.let { change ->
            val whenText = buildString {
                change.minutesAgo?.let { append(stringResource(R.string.heating_minutes_ago, it)).append(", ") }
                append(pumpSourceText(change.source))
            }
            Text(
                stringResource(if (change.on) R.string.heating_last_change_on else R.string.heating_last_change_off, whenText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PipeGrid(state.pipes)
    }
}

@Composable
private fun PipeGrid(pipes: PipeTemps) {
    val s = AppTheme.status
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PipeSide(
            title = stringResource(R.string.heating_well_side),
            first = stringResource(R.string.heating_pipe_in) to pipes.wellIn,
            second = stringResource(R.string.heating_pipe_out) to pipes.wellOut,
            container = s.coldContainer, onContainer = s.onColdContainer,
            modifier = Modifier.weight(1f),
        )
        PipeSide(
            title = stringResource(R.string.heating_heating_side),
            first = stringResource(R.string.heating_pipe_supply) to pipes.supply,
            second = stringResource(R.string.heating_pipe_return) to pipes.ret,
            container = s.warmContainer, onContainer = s.onWarmContainer,
            modifier = Modifier.weight(1f),
        )
    }
    if (listOf(pipes.wellIn, pipes.wellOut, pipes.ret).any { it == null }) {
        Text(stringResource(R.string.heating_pipes_pending), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PipeSide(
    title: String,
    first: Pair<String, String?>,
    second: Pair<String, String?>,
    container: androidx.compose.ui.graphics.Color,
    onContainer: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val a = first.second?.toDoubleOrNull()
    val b = second.second?.toDoubleOrNull()
    val delta = if (a != null && b != null) String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(a - b)) else null
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            listOfNotNull(title, delta?.let { stringResource(R.string.heating_delta, it) }).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(first, second).forEach { (label, value) ->
            Surface(shape = TileShape, color = if (value != null) container else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (value != null) onContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (value != null) "$value°" else "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (value != null) onContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Day program as a 24-hour bar; the editor opens with "Izmeni" and stays open while there is a draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleCard(state: HeatingState, onIntent: (HeatingIntent) -> Unit, initiallyEditing: Boolean) {
    var editingStartIndex by remember { mutableStateOf<Int?>(null) }
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    val showEditor = editing || state.scheduleDirty || state.dayNotSet
    // Back first closes an open editor without changes; with a draft the screen asks instead.
    BackHandler(enabled = editing && !state.scheduleDirty) { editing = false }
    val segments = state.editorSegments

    DashboardCard {
        CardHeader(Icons.Outlined.CalendarMonth, stringResource(R.string.heating_schedule_title)) {
            if (!state.scheduleDirty && !state.dayNotSet) {
                TextButton(onClick = { editing = !editing }) {
                    Text(stringResource(if (editing) R.string.heating_done else R.string.heating_edit))
                }
            }
        }
        val largeFont = isLargeFont()
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DayType.entries.forEachIndexed { index, day ->
                val isToday = state.nodeIsWeekend != null && state.nodeIsWeekend == (day == DayType.WEEKEND)
                val label = stringResource(if (day == DayType.WORKDAY) R.string.heating_workday else R.string.heating_weekend)
                SegmentedButton(
                    selected = state.selectedDay == day,
                    onClick = { onIntent(HeatingIntent.SelectDay(day)) },
                    shape = SegmentedButtonDefaults.itemShape(index, DayType.entries.size),
                    // "(danas)" is left out with a large font; the time marker on the timeline shows today.
                    label = { Text(if (isToday && !largeFont) "$label (${stringResource(R.string.heating_today)})" else label, maxLines = 1) },
                )
            }
        }

        val isToday = state.nodeIsWeekend != null && state.nodeIsWeekend == (state.selectedDay == DayType.WEEKEND)
        ScheduleTimeline(segments, state.limits, nowMinute = if (isToday) TimeOfDay.parse(state.nodeTime) else null)

        if (state.dayNotSet && !state.scheduleDirty) Hint(stringResource(R.string.heating_day_not_set), warning = false)
        if (state.remoteChangedWhileEditing) Hint(stringResource(R.string.heating_remote_changed), warning = true)

        if (showEditor) {
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
                    enabled = (state.scheduleDirty || state.dayNotSet) && state.scheduleProblem == null && state.connected && !state.sending,
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

/**
 * The day as one bar: each segment's width is its duration, its color is darker for warmer
 * targets, and its temperature is written inside when it fits. A marker shows the node's time.
 */
@Composable
private fun ScheduleTimeline(segments: List<ScheduleSegment>, limits: Node5Limits, nowMinute: Int?) {
    val low = AppTheme.status.activeContainer
    val high = AppTheme.status.active
    val onLow = AppTheme.status.onActiveContainer
    val marker = MaterialTheme.colorScheme.onSurface
    val temps = segments.map { it.tempTenths }
    val minT = (temps.minOrNull() ?: limits.tempMinTenths).toFloat()
    val maxT = (temps.maxOrNull() ?: limits.tempMaxTenths).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(32.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                segments.forEachIndexed { i, seg ->
                    val start = seg.startMinute / MINUTES_PER_DAY.toFloat() * size.width
                    val end = ScheduleRules.endMinute(segments, i) / MINUTES_PER_DAY.toFloat() * size.width
                    val t = if (maxT > minT) (seg.tempTenths - minT) / (maxT - minT) else 1f
                    drawRoundRect(
                        color = androidx.compose.ui.graphics.lerp(low, high, t),
                        topLeft = Offset(start + 1f, 0f),
                        size = Size((end - start - 2f).coerceAtLeast(1f), size.height),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }
                nowMinute?.let {
                    val x = it / MINUTES_PER_DAY.toFloat() * size.width
                    drawLine(marker, Offset(x, -2f), Offset(x, size.height + 2f), strokeWidth = 2.dp.toPx())
                }
            }
            Row(modifier = Modifier.fillMaxSize()) {
                segments.forEachIndexed { i, seg ->
                    val minutes = ScheduleRules.endMinute(segments, i) - seg.startMinute
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(minutes.coerceAtLeast(1).toFloat()).fillMaxSize()) {
                        if (minutes >= 150) {
                            val t = if (maxT > minT) (seg.tempTenths - minT) / (maxT - minT) else 1f
                            Text(
                                formatTenths(seg.tempTenths),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (t > 0.5f) MaterialTheme.colorScheme.onPrimary else onLow,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "24").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

private val PreviewToday = HeatingState(
    nodeId = "node5", connected = true, online = true, loading = false,
    outdoorTemp = "3.5", mainSwitch = true, pump = true, demand = true, flowTemp = "34.3",
    nodeTime = "21:35", nodeIsWeekend = false,
    lastPumpChange = PumpChangeUi(on = true, source = "thermostat", minutesAgo = 12),
    pipes = PipeTemps(supply = "34.3"),
    nextChange = NextChangeUi("22:30", "19.5"),
    zones = listOf(ZoneUi("ground", roomTemp = "21.2", target = "21.5", roomTempValue = 21.2, targetTenths = 215, demand = true, fault = null)),
    limits = PreviewLimits, selectedZone = "ground", selectedDay = DayType.WORKDAY,
    editorSegments = listOf(ScheduleSegment(0, 195), ScheduleSegment(360, 215), ScheduleSegment(1350, 195)),
    hysteresis = Hysteresis(2, 1), minOnSeconds = 600, minOffSeconds = 600, configRev = 9,
)

@androidx.compose.ui.tooling.preview.Preview(name = "Heating - today (flow pipe only)", heightDp = 1250)
@androidx.compose.ui.tooling.preview.Preview(name = "Heating - font 200%", heightDp = 2600, fontScale = 2f)
@Composable
private fun HeatingTodayPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface { HeatingContent(PreviewToday, onIntent = {}) }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Heating - two zones, four pipes (dark)", heightDp = 1250)
@Composable
private fun HeatingFuturePreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = true) {
        Surface {
            HeatingContent(
                PreviewToday.copy(
                    pipes = PipeTemps(wellIn = "11.2", wellOut = "8.1", supply = "34.3", ret = "29.7"),
                    zones = PreviewToday.zones + ZoneUi("upper", "22.4", "21.0", 22.4, 210, demand = false, fault = null),
                ),
                onIntent = {},
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Heating - bench board, safe mode", heightDp = 1250)
@Composable
private fun HeatingSafeModePreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface {
            HeatingContent(
                PreviewToday.copy(
                    nodeId = "testnode5", pump = false, demand = false, flowTemp = "--", pipes = PipeTemps(),
                    nodeIsWeekend = true, selectedDay = DayType.WEEKEND, nextChange = null,
                    safeMode = SafeModeUi("no_schedule", "ground"),
                    lastPumpChange = PumpChangeUi(on = false, source = "safe_mode", minutesAgo = 3),
                    zones = listOf(ZoneUi("ground", "20.9", null, 20.9, null, demand = false, fault = "no_schedule")),
                    editorSegments = ScheduleRules.defaultDay(PreviewLimits), dayNotSet = true,
                ),
                onIntent = {},
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Heating - editing an invalid draft", heightDp = 1450)
@Composable
private fun HeatingInvalidDraftPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface {
            HeatingContent(
                PreviewToday.copy(
                    editorSegments = listOf(ScheduleSegment(0, 195), ScheduleSegment(480, 215), ScheduleSegment(420, 195)),
                    scheduleDirty = true,
                    scheduleProblem = ScheduleProblem.StartsNotRising(2),
                    remoteChangedWhileEditing = true,
                ),
                onIntent = {},
                initiallyEditing = true,
            )
        }
    }
}
