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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.model.HeatingSegment
import zhb.company.smart_home.ui.components.PulsingStatusDot
import zhb.company.smart_home.ui.theme.DisableRed
import zhb.company.smart_home.ui.theme.EnableGreen
import zhb.company.smart_home.ui.theme.PanelBorderTeal
import zhb.company.smart_home.ui.theme.PrimaryColor
import zhb.company.smart_home.ui.theme.White

private val PipeOffset = 22.dp
private val PipeWidth = 5.dp

@Composable
fun HeatingScreen(
    viewModel: HeatingViewModel = koinViewModel(),
    onOpenHome: () -> Unit,
    onOpenGarden: () -> Unit,
    onOpenHouse: () -> Unit,
    onOpenShed: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Scaffold { padding -> HeatingContent(state, viewModel, padding, onOpenHome) }

    if (state.showSettingsDialog) {
        HeatingSettingsDialog(state, viewModel)
    }
}

@Composable
private fun HeatingContent(
    state: HeatingState,
    viewModel: HeatingViewModel,
    padding: PaddingValues,
    onOpenHome: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        item {
            HeatingTitleRow(
                online = state.online,
                onBack = onOpenHome,
                onOpenSettings = { viewModel.onIntent(HeatingIntent.OpenSettingsDialog) },
            )
        }
        item { HeatingDiagramCard(state, viewModel) }
        item {
            ScheduleEditor(
                title = stringResource(R.string.heating_schedule_workday),
                segments = state.workdaySegments,
                onSegmentsChanged = { viewModel.onIntent(HeatingIntent.SetWorkdaySegments(it)) },
                onSave = { viewModel.onIntent(HeatingIntent.SaveWorkdaySchedule) },
            )
        }
        item {
            ScheduleEditor(
                title = stringResource(R.string.heating_schedule_weekend),
                segments = state.weekendSegments,
                onSegmentsChanged = { viewModel.onIntent(HeatingIntent.SetWeekendSegments(it)) },
                onSave = { viewModel.onIntent(HeatingIntent.SaveWeekendSchedule) },
            )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

/** Plain (non-collapsing) title row: screen name, a live online/offline indicator, and settings access. */
@Composable
private fun HeatingTitleRow(online: Boolean, onBack: () -> Unit, onOpenSettings: () -> Unit) {
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
            modifier = Modifier.weight(1f),
        )
        PulsingStatusDot(online = online)
        Text(
            text = if (online) stringResource(R.string.connections_online) else stringResource(R.string.connections_offline),
            style = MaterialTheme.typography.labelSmall,
            color = if (online) EnableGreen else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 6.dp, end = 4.dp),
        )
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.heating_settings_title))
        }
    }
}

/**
 * A single unified diagram combining every heating reading, drawn entirely with vector shapes
 * (no bitmap illustration): a vertical stack of rows - outdoor, "Drugi sprat", "Prvi sprat", then
 * the boiler - with two pipes rising from the boiler to each floor's row, turning green while the
 * boiler relay is actively heating and staying muted gray when idle.
 */
@Composable
private fun HeatingDiagramCard(state: HeatingState, viewModel: HeatingViewModel) {
    val density = LocalDensity.current
    val pipeColor = if (state.isTempRelay) EnableGreen else MaterialTheme.colorScheme.outlineVariant
    val pipeOffsetPx = with(density) { PipeOffset.toPx() }
    val pipeWidthPx = with(density) { PipeWidth.toPx() }

    var boxRootY by remember { mutableStateOf(0f) }
    var upperRowY by remember { mutableStateOf(0f) }
    var lowerRowY by remember { mutableStateOf(0f) }
    var boilerRowTopY by remember { mutableStateOf(0f) }

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
                if (boilerRowTopY > 0f) {
                    val centerX = size.width / 2f
                    drawLine(
                        color = pipeColor,
                        start = Offset(centerX - pipeOffsetPx, boilerRowTopY - boxRootY),
                        end = Offset(centerX - pipeOffsetPx, lowerRowY - boxRootY),
                        strokeWidth = pipeWidthPx,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = pipeColor,
                        start = Offset(centerX + pipeOffsetPx, boilerRowTopY - boxRootY),
                        end = Offset(centerX + pipeOffsetPx, upperRowY - boxRootY),
                        strokeWidth = pipeWidthPx,
                        cap = StrokeCap.Round,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                DiagramNodeRow(
                    icon = Icons.Filled.Thermostat,
                    label = stringResource(R.string.heating_outdoor_temp),
                    value = "${state.outdoorTemp}°C",
                    active = false,
                )
                DiagramNodeRow(
                    icon = Icons.Filled.Thermostat,
                    label = stringResource(R.string.menu_second_floor),
                    value = "${state.tempUp}°C / ${state.humidityUp}%",
                    active = state.isTempRelay,
                    modifier = Modifier.onGloballyPositioned { upperRowY = it.positionInRoot().y + it.size.height / 2f },
                )
                DiagramNodeRow(
                    icon = Icons.Filled.Thermostat,
                    label = stringResource(R.string.menu_first_floor),
                    value = "${state.tempDown}°C / ${state.humidityDown}%",
                    active = state.isTempRelay,
                    modifier = Modifier.onGloballyPositioned { lowerRowY = it.positionInRoot().y + it.size.height / 2f },
                )
                Spacer(modifier = Modifier.height(6.dp))
                BoilerDock(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.onGloballyPositioned { boilerRowTopY = it.positionInRoot().y },
                )
            }
        }
    }
}

@Composable
private fun DiagramNodeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Surface(shape = CircleShape, color = if (active) PrimaryColor else MaterialTheme.colorScheme.outlineVariant) {
            Icon(icon, contentDescription = null, tint = White, modifier = Modifier.padding(9.dp).size(18.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BoilerDock(state: HeatingState, viewModel: HeatingViewModel, modifier: Modifier = Modifier) {
    val pelletsLow = state.pelletsLevelPercent <= state.alarmLimitPercent
    Surface(
        color = if (state.isTempRelay) PrimaryColor.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Surface(shape = CircleShape, color = if (state.isTempRelay) PrimaryColor else MaterialTheme.colorScheme.outlineVariant) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = White, modifier = Modifier.padding(10.dp).size(20.dp))
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = stringResource(R.string.heating_boiler),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.isTempRelay) stringResource(R.string.heating_relay_running) else stringResource(R.string.heating_relay_idle),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.isTempRelay) EnableGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 2.dp)) {
                    MiniReadout(Icons.Filled.Thermostat, "${state.tubeTemp}°C")
                    MiniReadout(Icons.Filled.Grain, "${state.pelletsLevelPercent.toInt()}%", warning = pelletsLow)
                }
            }
            Switch(
                checked = state.isMainSwitch,
                onCheckedChange = { viewModel.onIntent(HeatingIntent.ToggleMainSwitch(it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = PrimaryColor),
            )
        }
    }
}

@Composable
private fun MiniReadout(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, warning: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (warning) DisableRed else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (warning) DisableRed else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

private const val MIN_SCHEDULE_SEGMENTS = 1
private const val MAX_SCHEDULE_SEGMENTS = 6
private const val MIN_SCHEDULE_TEMP = 20
private const val MAX_SCHEDULE_TEMP = 25

/**
 * A day is always fully covered: the first segment always starts at 00:00, the last always ends
 * at 24:00, and between 1 and 6 segments split the day - each just a temperature and the time it
 * lasts until. Editing a segment's end time (via a [TimePicker] dialog) moves the shared boundary
 * with the next segment; adding a segment splits the current last one in two; removing one merges
 * its span into its neighbor. There's nothing to "add a time" for - the schedule can't have gaps
 * or overlaps.
 */
@Composable
private fun ScheduleEditor(
    title: String,
    segments: List<HeatingSegment>,
    onSegmentsChanged: (List<HeatingSegment>) -> Unit,
    onSave: () -> Unit,
) {
    val safeSegments = if (segments.isEmpty()) listOf(HeatingSegment(time = "00:00", temp = 20)) else segments
    var editingBoundaryIndex by remember { mutableStateOf<Int?>(null) }

    Card(modifier = Modifier.padding(16.dp).fillMaxWidth(), border = BorderStroke(2.dp, PanelBorderTeal)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onSave) { Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.heating_save_schedule)) }
            }
            safeSegments.forEachIndexed { index, segment ->
                val isLast = index == safeSegments.lastIndex
                ScheduleSegmentRow(
                    startLabel = if (index == 0) "00:00" else segment.time,
                    endLabel = if (isLast) "24:00" else safeSegments[index + 1].time,
                    endEditable = !isLast,
                    onEndTimeClick = { editingBoundaryIndex = index + 1 },
                    temp = segment.temp,
                    onTempChange = { newTemp ->
                        val updated = safeSegments.toMutableList()
                        updated[index] = updated[index].copy(temp = newTemp)
                        onSegmentsChanged(updated)
                    },
                    onDelete = if (safeSegments.size > MIN_SCHEDULE_SEGMENTS) {
                        {
                            val updated = safeSegments.toMutableList()
                            updated.removeAt(index)
                            if (updated.isNotEmpty()) updated[0] = updated[0].copy(time = "00:00")
                            onSegmentsChanged(updated)
                        }
                    } else null,
                )
            }
            val largestSpan = findLargestSpan(safeSegments)
            if (safeSegments.size < MAX_SCHEDULE_SEGMENTS && largestSpan.minutes >= 2) {
                TextButton(onClick = {
                    val target = safeSegments[largestSpan.index]
                    val targetEnd = if (largestSpan.index == safeSegments.lastIndex) "24:00" else safeSegments[largestSpan.index + 1].time
                    val splitTime = midpointTime(target.time, targetEnd)
                    val updated = safeSegments.toMutableList()
                    updated.add(largestSpan.index + 1, HeatingSegment(time = splitTime, temp = target.temp))
                    onSegmentsChanged(updated)
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.heating_add_segment), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }

    val boundaryIndex = editingBoundaryIndex
    if (boundaryIndex != null && boundaryIndex < safeSegments.size) {
        ScheduleTimePickerDialog(
            initialTime = safeSegments[boundaryIndex].time,
            onConfirm = { newTime ->
                val updated = safeSegments.toMutableList()
                updated[boundaryIndex] = updated[boundaryIndex].copy(time = newTime)
                onSegmentsChanged(updated)
                editingBoundaryIndex = null
            },
            onDismiss = { editingBoundaryIndex = null },
        )
    }
}

@Composable
private fun ScheduleSegmentRow(
    startLabel: String,
    endLabel: String,
    endEditable: Boolean,
    onEndTimeClick: () -> Unit,
    temp: Int,
    onTempChange: (Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Text(startLabel, style = MaterialTheme.typography.labelMedium)
        Text("–", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 4.dp))
        if (endEditable) {
            Surface(
                onClick = onEndTimeClick,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = stringResource(R.string.heating_pick_time), modifier = Modifier.size(14.dp))
                    Text(endLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
                }
            }
        } else {
            Text(endLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.weight(1f))
        TempStepper(temp = temp, onChange = onTempChange)
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        } else {
            Spacer(modifier = Modifier.size(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimePickerDialog(initialTime: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val parts = initialTime.split(":")
    val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val pickerState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.heating_pick_time), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                TimePicker(state = pickerState)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Otkaži") }
                    TextButton(onClick = {
                        val hh = pickerState.hour.toString().padStart(2, '0')
                        val mm = pickerState.minute.toString().padStart(2, '0')
                        onConfirm("$hh:$mm")
                    }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun TempStepper(temp: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((temp - 1).coerceIn(MIN_SCHEDULE_TEMP, MAX_SCHEDULE_TEMP)) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.ac_temp_decrease), modifier = Modifier.size(16.dp))
        }
        Text(
            text = "$temp°C",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        IconButton(onClick = { onChange((temp + 1).coerceIn(MIN_SCHEDULE_TEMP, MAX_SCHEDULE_TEMP)) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ac_temp_increase), modifier = Modifier.size(16.dp))
        }
    }
}

private fun parseMinutesOfDay(value: String): Int {
    val parts = value.split(":")
    val hh = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val mm = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return (hh * 60 + mm).coerceIn(0, 24 * 60)
}

private data class SegmentSpan(val index: Int, val minutes: Int)

/**
 * The segment with the most room to split - not necessarily the last one. A schedule built up
 * through the time picker can end up with a tiny last segment (e.g. one edited to end right
 * before 24:00), so splitting is always offered against whichever segment has the largest span,
 * guaranteeing "+" stays usable as long as any segment still has room.
 */
private fun findLargestSpan(segments: List<HeatingSegment>): SegmentSpan {
    var bestIndex = 0
    var bestMinutes = -1
    segments.forEachIndexed { index, segment ->
        val startMin = parseMinutesOfDay(segment.time)
        val endMin = if (index == segments.lastIndex) 24 * 60 else parseMinutesOfDay(segments[index + 1].time)
        val span = endMin - startMin
        if (span > bestMinutes) {
            bestMinutes = span
            bestIndex = index
        }
    }
    return SegmentSpan(bestIndex, bestMinutes)
}

/**
 * Rounds the midpoint between two "HH:MM" (or "24:00") times to the nearest 30 minutes. Falls
 * back to the minute right after [start] when the span is too small (under 2 minutes) for a
 * clean split - callers should avoid offering a split at all in that case (see [findLargestSpan]).
 */
private fun midpointTime(start: String, end: String): String {
    val startMin = parseMinutesOfDay(start)
    val endMin = parseMinutesOfDay(end)
    val mid = if (endMin - startMin < 2) {
        (startMin + 1).coerceAtMost(endMin)
    } else {
        val midRaw = (startMin + endMin) / 2
        ((midRaw / 30) * 30).coerceIn(startMin + 1, endMin - 1)
    }
    val hh = (mid / 60).coerceIn(0, 23)
    val mm = mid % 60
    return "${hh.toString().padStart(2, '0')}:${mm.toString().padStart(2, '0')}"
}

@Composable
private fun HeatingSettingsDialog(state: HeatingState, viewModel: HeatingViewModel) {
    var upStep by remember { mutableStateOf(state.upStep) }
    var downStep by remember { mutableStateOf(state.downStep) }
    var alarmLimit by remember { mutableStateOf(state.alarmLimitPercent) }

    AlertDialog(
        onDismissRequest = { viewModel.onIntent(HeatingIntent.DismissSettingsDialog) },
        title = { Text(stringResource(R.string.heating_settings_title)) },
        confirmButton = {
            TextButton(onClick = {
                viewModel.onIntent(HeatingIntent.SetUpperHysteresis(upStep))
                viewModel.onIntent(HeatingIntent.SetLowerHysteresis(downStep))
                viewModel.onIntent(HeatingIntent.SetAlarmLimit(alarmLimit))
                viewModel.onIntent(HeatingIntent.DismissSettingsDialog)
            }) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.onIntent(HeatingIntent.DismissSettingsDialog) }) { Text("Otkaži") }
        },
        text = {
            Column {
                Text(stringResource(R.string.heating_upper_hysteresis))
                Slider(value = upStep.toFloat(), onValueChange = { upStep = it.toDouble() }, valueRange = 0f..5f)
                Text(stringResource(R.string.heating_lower_hysteresis))
                Slider(value = downStep.toFloat(), onValueChange = { downStep = it.toDouble() }, valueRange = 0f..5f)
                Text(stringResource(R.string.heating_alarm_limit))
                Slider(value = alarmLimit.toFloat(), onValueChange = { alarmLimit = it.toInt() }, valueRange = 0f..100f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { viewModel.onIntent(HeatingIntent.SetFullLevel) }) { Text(stringResource(R.string.heating_set_full_level)) }
                    TextButton(onClick = { viewModel.onIntent(HeatingIntent.SetEmptyLevel) }) { Text(stringResource(R.string.heating_set_empty_level)) }
                }
            }
        },
    )
}
