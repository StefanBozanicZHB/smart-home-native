package zhb.company.smart_home.ui.garden

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Light
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Water
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbIncandescent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.garden.ActiveRun
import zhb.company.smart_home.domain.garden.NextStart
import zhb.company.smart_home.domain.garden.RunEvent
import zhb.company.smart_home.domain.garden.RunEventType
import zhb.company.smart_home.domain.garden.RunPhase
import zhb.company.smart_home.domain.garden.RunSource
import zhb.company.smart_home.domain.garden.SkipReason
import zhb.company.smart_home.domain.garden.Weekday
import zhb.company.smart_home.ui.components.AlertBanner
import zhb.company.smart_home.ui.components.Appearing
import zhb.company.smart_home.ui.components.BigValue
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.DeviceTile
import zhb.company.smart_home.ui.components.Motion
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.SectionLabel
import zhb.company.smart_home.ui.components.StatusChip
import zhb.company.smart_home.ui.components.TileGrid
import zhb.company.smart_home.ui.components.TileShape
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.components.adaptiveColumns
import zhb.company.smart_home.ui.components.animatedColor
import zhb.company.smart_home.ui.components.animatedItem
import zhb.company.smart_home.ui.components.isLargeFont
import zhb.company.smart_home.ui.format.Durations
import zhb.company.smart_home.ui.theme.AppTheme

@Composable
fun GardenScreen(onOpenPlan: () -> Unit, viewModel: GardenViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when (message) {
            GardenMessage.Started -> context.getString(R.string.garden_msg_started)
            GardenMessage.Stopped -> context.getString(R.string.garden_msg_stopped)
            GardenMessage.NoZones -> context.getString(R.string.garden_msg_no_zones)
            GardenMessage.NotSaved -> context.getString(R.string.garden_msg_not_saved)
            is GardenMessage.Rejected -> context.getString(R.string.heating_msg_rejected, message.error)
            GardenMessage.Timeout -> context.getString(R.string.heating_msg_timeout)
            GardenMessage.NotConnected -> context.getString(R.string.heating_msg_not_connected)
        }
        viewModel.onIntent(GardenIntent.MessageShown)
        snackbar.showSnackbar(text)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GardenContent(state, viewModel::onIntent, onOpenPlan)
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
    if (state.showLocationDialog) {
        LocationDialog(activeIndex = state.irrigation.run?.zone ?: -1, onDismiss = { viewModel.onIntent(GardenIntent.DismissLocationDialog) })
    }
    when (val dialog = state.dialog) {
        is GardenDialog.ConfirmReplace -> AlertDialog(
            onDismissRequest = { viewModel.onIntent(GardenIntent.DismissDialog) },
            title = { Text(stringResource(R.string.garden_replace_title)) },
            text = { Text(stringResource(R.string.garden_replace_text)) },
            confirmButton = { TextButton(onClick = { viewModel.onIntent(GardenIntent.ConfirmReplace) }) { Text(stringResource(R.string.garden_replace_confirm)) } },
            dismissButton = { TextButton(onClick = { viewModel.onIntent(GardenIntent.DismissDialog) }) { Text(stringResource(R.string.heating_cancel)) } },
        )
        is GardenDialog.AskMinutes -> MinutesDialog(
            zone = dialog.zone,
            onConfirm = { viewModel.onIntent(GardenIntent.StartZoneFor(dialog.zone, it)) },
            onDismiss = { viewModel.onIntent(GardenIntent.DismissDialog) },
        )
        null -> Unit
    }
}

@Composable
private fun GardenContent(state: GardenState, onIntent: (GardenIntent) -> Unit, onOpenPlan: () -> Unit) {
    val offline = stringResource(R.string.home_node_offline_tile)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        animatedItem("garden-1") {
            ScreenHeader(
                title = stringResource(R.string.menu_garden),
                subtitle = "${state.temperature}°",
                horizontalPadding = 0.dp,
            )
        }
        if (state.yardOnline && !state.outdoorSensorOk) {
            animatedItem("garden-2") { AlertBanner(Icons.Outlined.WarningAmber, stringResource(R.string.garden_sensor_missing), Tone.WARNING) }
        }
        animatedItem("garden-3") { SprinklerCard(state, onIntent, onOpenPlan) }
        animatedItem("garden-4") { SoilMoistureCard() }
        animatedItem("garden-5") { SectionLabel(stringResource(R.string.garden_fountain_and_lights)) }
        animatedItem("garden-6") {
            TileGrid(
                columns = adaptiveColumns(2),
                tiles = listOf(
                    { m ->
                        DeviceTile(
                            Icons.Outlined.Water, stringResource(R.string.garden_fountain_pump), state.fountainPumpOn,
                            { onIntent(GardenIntent.SetFountainPump(it)) }, m,
                            detail = waitingOr(GardenOutput.FOUNTAIN_PUMP in state.slow, state.fountainPumpLeftSeconds),
                            enabled = state.node1Online, disabledReason = if (state.node1Online) null else offline,
                        )
                    },
                    { m ->
                        DeviceTile(
                            Icons.Outlined.Light, stringResource(R.string.garden_fountain_light), state.fountainLightOn,
                            { onIntent(GardenIntent.SetFountainLight(it)) }, m,
                            detail = waitingOr(GardenOutput.FOUNTAIN_LIGHT in state.slow, state.fountainLightLeftSeconds),
                            enabled = state.node1Online, disabledReason = if (state.node1Online) null else offline,
                        )
                    },
                    { m ->
                        DeviceTile(
                            Icons.Outlined.Lightbulb, stringResource(R.string.garden_led_strip), state.ledStripOn,
                            { onIntent(GardenIntent.SetLedStrip(it)) }, m,
                            detail = waitingOr(GardenOutput.LED_STRIP in state.slow, state.ledStripLeftSeconds),
                            enabled = state.yardOnline, disabledReason = if (state.yardOnline) null else offline,
                        )
                    },
                    { m ->
                        DeviceTile(
                            Icons.Outlined.WbIncandescent, stringResource(R.string.garden_bulb), state.bulbOn,
                            { onIntent(GardenIntent.SetBulb(it)) }, m,
                            detail = waitingOr(GardenOutput.BULB in state.slow, state.bulbLeftSeconds),
                            enabled = state.yardOnline, disabledReason = if (state.yardOnline) null else offline,
                        )
                    },
                ),
            )
        }
        animatedItem("garden-7") {
            Column {
                TimerRow(
                    stringResource(R.string.garden_fountain_timer),
                    state.node1Online,
                    listOf(
                        stringResource(R.string.garden_fountain_pump_short) to { m: Int -> onIntent(GardenIntent.SetFountainPump(true, m)) },
                        stringResource(R.string.garden_fountain_light_short) to { m: Int -> onIntent(GardenIntent.SetFountainLight(true, m)) },
                    ),
                )
                TimerRow(
                    stringResource(R.string.garden_lights_timer),
                    state.yardOnline,
                    listOf(
                        stringResource(R.string.garden_led_strip_short) to { m: Int -> onIntent(GardenIntent.SetLedStrip(true, m)) },
                        stringResource(R.string.garden_bulb_short) to { m: Int -> onIntent(GardenIntent.SetBulb(true, m)) },
                    ),
                )
            }
        }
        animatedItem("garden-8") { PoolCard(state, onIntent) }
    }
}

/** "Uključi na…" for outputs the node switches off by itself (fountain, lights, pool). */
@Composable
private fun TimerRow(
    title: String,
    enabled: Boolean,
    items: List<Pair<String, (Int) -> Unit>>,
    choices: List<Int> = FOUNTAIN_TIMER_MINUTES,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        items.forEach { (label, onPick) -> TimerMenu(label, enabled, choices, onPick = onPick) }
    }
}

private val FOUNTAIN_TIMER_MINUTES = listOf(30, 60, 120, 240)

/** The pool pump and heating usually run for hours. */
private val POOL_TIMER_MINUTES = listOf(60, 120, 240, 480)

@Composable
private fun TimerMenu(label: String, enabled: Boolean, choices: List<Int> = FOUNTAIN_TIMER_MINUTES, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.garden_on_for, Durations.compact(minutes * 60L))) },
                    onClick = {
                        open = false
                        onPick(minutes)
                    },
                )
            }
        }
    }
}

/**
 * Sprinklers on Node 1: what is watering now (zone, phase, time left), the six zones with their
 * planned minutes (tap = water that zone now), the next scheduled start and the last run event.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SprinklerCard(state: GardenState, onIntent: (GardenIntent) -> Unit, onOpenPlan: () -> Unit) {
    val irrigation = state.irrigation
    val run = irrigation.run
    val canCommand = state.node1Online && state.node1Ready
    DashboardCard {
        CardHeader(Icons.Outlined.WaterDrop, stringResource(R.string.garden_sprinklers)) {
            when {
                !state.node1Online -> StatusChip(stringResource(R.string.home_node_offline_tile), Tone.DANGER)
                run != null -> StatusChip(stringResource(R.string.home_sprinklers_running, run.zone), Tone.ACTIVE)
                irrigation.pausedUntil != null -> StatusChip(stringResource(R.string.irrigation_paused_chip, shortDate(irrigation.pausedUntil)), Tone.WARNING)
                irrigation.auto -> StatusChip(stringResource(R.string.home_sprinklers_auto), Tone.ACTIVE)
                else -> StatusChip(stringResource(R.string.home_sprinklers_manual), Tone.NEUTRAL)
            }
        }
        Appearing(irrigation.storage) { AlertBanner(Icons.Outlined.WarningAmber, stringResource(R.string.irrigation_storage_problem), Tone.DANGER, detail = it) }
        Appearing(run) { RunPanel(it, canCommand, onIntent) }
        ZoneButtons(irrigation, canCommand, onIntent)
        Text(
            when {
                !state.node1Ready -> stringResource(R.string.irrigation_waiting)
                !irrigation.auto -> stringResource(R.string.irrigation_auto_off)
                !irrigation.clockKnown -> stringResource(R.string.irrigation_no_clock)
                irrigation.next != null -> stringResource(R.string.irrigation_next, nextStartText(irrigation.next))
                else -> stringResource(R.string.irrigation_nothing_scheduled)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        irrigation.lastRun?.let {
            Text(
                stringResource(R.string.irrigation_last, lastRunText(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.Center) {
            if (run == null) {
                FilledTonalButton(onClick = { onIntent(GardenIntent.StartAll) }, enabled = canCommand) { Text(stringResource(R.string.garden_water_all)) }
            }
            TextButton(onClick = onOpenPlan) { Text(stringResource(R.string.garden_plan)) }
            TextButton(onClick = { onIntent(GardenIntent.OpenLocationDialog) }) { Text(stringResource(R.string.garden_map)) }
        }
    }
}

/** The active run: zone and phase, time left in the phase and in the whole run, and Stop. */
@Composable
private fun RunPanel(run: ActiveRun, enabled: Boolean, onIntent: (GardenIntent) -> Unit) {
    Surface(shape = TileShape, color = AppTheme.status.activeContainer, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                val phase = when (run.phase) {
                    RunPhase.SOAK -> stringResource(R.string.irrigation_phase_soak)
                    else -> stringResource(R.string.irrigation_phase_on)
                }
                Text(
                    stringResource(R.string.irrigation_run_zone, run.zone, phase),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.status.onActiveContainer,
                )
                val parts = listOfNotNull(
                    run.phaseLeftSeconds?.let { stringResource(R.string.irrigation_phase_left, leftText(it)) },
                    stringResource(R.string.irrigation_total_left, leftText(run.totalLeftSeconds)),
                )
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = AppTheme.status.onActiveContainer)
            }
            FilledTonalButton(onClick = { onIntent(GardenIntent.StopRun) }, enabled = enabled) {
                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.garden_stop), modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** Six zones: number and planned minutes; the running zone is highlighted, queued zones outlined. */
@Composable
private fun ZoneButtons(irrigation: IrrigationUi, enabled: Boolean, onIntent: (GardenIntent) -> Unit) {
    val run = irrigation.run
    val columns = if (isLargeFont()) 3 else 6
    irrigation.zoneMinutes.indices.chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { i ->
                val zone = i + 1
                val minutes = irrigation.zoneMinutes[i]
                val running = run?.zone == zone
                val queued = run != null && !running && run.zones.indexOf(zone).let { it >= 0 && run.leftSeconds.getOrNull(it)?.let { left -> left > 0 } == true }
                val description = stringResource(R.string.garden_zone_desc, zone)
                val stateText = when {
                    running -> stringResource(R.string.garden_zone_running)
                    queued -> stringResource(R.string.garden_zone_queued)
                    else -> stringResource(R.string.garden_zone_minutes, minutes)
                }
                Surface(
                    onClick = { onIntent(GardenIntent.StartZone(zone)) },
                    enabled = enabled,
                    shape = TileShape,
                    color = animatedColor(if (running) AppTheme.status.activeContainer else MaterialTheme.colorScheme.surfaceContainerHighest, "zoneBg"),
                    border = if (queued) BorderStroke(1.5.dp, AppTheme.status.active) else null,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).semantics {
                        contentDescription = description
                        stateDescription = stateText
                    },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                        val fg = animatedColor(if (running) AppTheme.status.onActiveContainer else MaterialTheme.colorScheme.onSurface, "zoneFg")
                        Text("$zone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = fg)
                        Text(
                            if (minutes > 0) stringResource(R.string.garden_minutes_short, minutes) else "–",
                            style = MaterialTheme.typography.labelSmall,
                            color = animatedColor(if (running) AppTheme.status.onActiveContainer else MaterialTheme.colorScheme.onSurfaceVariant, "zoneMinutes"),
                        )
                    }
                }
            }
            repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

/** How long to water a zone that has no minutes in the plan. */
@Composable
private fun MinutesDialog(zone: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var minutes by remember { mutableIntStateOf(5) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.garden_minutes_title, zone)) },
        text = {
            Column {
                Text(stringResource(R.string.garden_minutes_text), style = MaterialTheme.typography.bodyMedium)
                MinuteStepper(minutes, 1..60, onChange = { minutes = it }, modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(minutes) }) { Text(stringResource(R.string.garden_water_now)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.heating_cancel)) } },
    )
}

/** Placeholder until the soil moisture sensor exists (planned next year). */
@Composable
private fun SoilMoistureCard() {
    DashboardCard {
        CardHeader(Icons.Outlined.Grass, stringResource(R.string.garden_soil)) {
            StatusChip(stringResource(R.string.garden_soon), Tone.NEUTRAL)
        }
        Text(stringResource(R.string.garden_soil_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PoolCard(state: GardenState, onIntent: (GardenIntent) -> Unit) {
    DashboardCard {
        CardHeader(Icons.Outlined.Pool, stringResource(R.string.garden_pool)) {
            if (!state.poolOnline) StatusChip(stringResource(R.string.home_node_offline_tile), Tone.DANGER)
        }
        BigValue("${state.poolTemperature}°")
        Appearing(state.poolOnline && !state.poolSensorOk) {
            Text(stringResource(R.string.garden_pool_sensor_missing), style = MaterialTheme.typography.bodySmall, color = AppTheme.status.warning)
        }
        SwitchRow(stringResource(R.string.garden_pool_pump), state.poolPumpOn, state.poolOnline) { onIntent(GardenIntent.SetPoolPump(it)) }
        Appearing(waitingOr(GardenOutput.POOL_PUMP in state.slow, state.poolPumpLeftSeconds?.takeIf { state.poolPumpOn })) {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val heatingOn = state.poolHeatingOn
        if (heatingOn != null) {
            SwitchRow(stringResource(R.string.garden_pool_heating), heatingOn, state.poolOnline) { onIntent(GardenIntent.SetPoolHeating(it)) }
            Appearing(waitingOr(GardenOutput.POOL_HEATING in state.slow, state.poolHeatingLeftSeconds?.takeIf { heatingOn })) {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TimerRow(
            stringResource(R.string.garden_pool_timer),
            state.poolOnline,
            buildList {
                add(stringResource(R.string.garden_fountain_pump_short) to { m: Int -> onIntent(GardenIntent.SetPoolPump(true, m)) })
                if (heatingOn != null) add(stringResource(R.string.garden_pool_heating_short) to { m: Int -> onIntent(GardenIntent.SetPoolHeating(true, m)) })
            },
            POOL_TIMER_MINUTES,
        )
    }
}

/** "čeka čvor…" while the answer is late, else the time until the output switches off. */
@Composable
private fun waitingOr(waiting: Boolean, leftSeconds: Int?): String? = when {
    waiting -> stringResource(R.string.garden_waiting)
    leftSeconds != null -> stringResource(R.string.garden_off_in, leftText(leftSeconds))
    else -> null
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/**
 * Normalized (bias, from -1f..1f on each axis) positions of the 6 sprinkler zones over the
 * yard/house diagram (`img_nodes_diagram`), picked to sit on the green lawn areas of that
 * 392x381 image: a front strip along the top, a side strip along the left, and the back-yard
 * patch at the bottom (avoiding the house block and the two existing pool/fountain markers).
 */
private val sprinklerMapPositions = listOf(
    BiasAlignment(-0.69f, -0.90f), // 1 - front yard, left
    BiasAlignment(0.17f, -0.90f), // 2 - front yard, right
    BiasAlignment(-0.75f, -0.48f), // 3 - side yard, upper
    BiasAlignment(-0.75f, 0.15f), // 4 - side yard, lower
    BiasAlignment(-0.50f, 0.70f), // 5 - back yard, left
    BiasAlignment(0.10f, 0.70f), // 6 - back yard, right
)

@Composable
private fun LocationDialog(activeIndex: Int, onDismiss: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pinPulse")
    val pinPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "pinPulseScale",
    )
    val pinPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "pinPulseAlpha",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text(stringResource(R.string.garden_sprinkler_location)) },
        text = {
            Column {
                Text(
                    text = if (activeIndex != -1) "${stringResource(R.string.garden_sprinkler_active)} $activeIndex" else stringResource(R.string.garden_sprinkler_none_active),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(392f / 381f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                ) {
                    Image(
                        painter = painterResource(R.drawable.img_nodes_diagram),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    sprinklerMapPositions.forEachIndexed { i, bias ->
                        val number = i + 1
                        val isActive = number == activeIndex
                        Box(modifier = Modifier.align(bias)) {
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .align(Alignment.Center)
                                        .graphicsLayer { scaleX = pinPulse; scaleY = pinPulse; alpha = pinPulseAlpha }
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = animatedColor(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), "pinBg"),
                                border = if (isActive) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(animateDpAsState(if (isActive) 30.dp else 22.dp, Motion.standard(), label = "pinSize").value),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = "$number",
                                        color = animatedColor(if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, "pinFg"),
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = if (isActive) 14.sp else 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

// ---- previews ------------------------------------------------------------------------------------

private val PreviewIrrigation = IrrigationUi(
    auto = true,
    zoneMinutes = listOf(10, 10, 0, 15, 10, 10),
    next = NextStart(Weekday.MON, "20:30", 0),
    run = ActiveRun(RunSource.SCHEDULE, 2, RunPhase.ON, 95, listOf(1, 2, 4, 5, 6), listOf(0, 95, 900, 600, 600)),
    lastRun = RunEvent(7, 86412, RunEventType.STARTED, RunSource.SCHEDULE, "06:00", listOf(1, 2, 4, 5, 6), listOf(10, 10, 15, 10, 10), null),
)

@androidx.compose.ui.tooling.preview.Preview(name = "Garden - watering", heightDp = 1250)
@androidx.compose.ui.tooling.preview.Preview(name = "Garden - font 200%", heightDp = 2600, fontScale = 2f)
@Composable
private fun GardenPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface {
            GardenContent(
                GardenState(
                    yardOnline = true, node1Online = true, node1Ready = true, poolOnline = true,
                    temperature = "28.4", ledStripOn = true, ledStripLeftSeconds = 3600, poolPumpOn = true, poolPumpLeftSeconds = 7140, poolHeatingOn = true, poolHeatingLeftSeconds = 3600, poolTemperature = "26.4",
                    fountainLightOn = true, fountainLightLeftSeconds = 5400, irrigation = PreviewIrrigation,
                ),
                onIntent = {},
                onOpenPlan = {},
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Garden - paused, idle", heightDp = 700)
@Composable
private fun GardenPausedPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface {
            GardenContent(
                GardenState(
                    yardOnline = true, node1Online = true, node1Ready = true,
                    irrigation = PreviewIrrigation.copy(
                        run = null, pausedUntil = "2026-11-22", next = NextStart(Weekday.MON, "06:00", 3),
                        lastRun = RunEvent(8, 90000, RunEventType.SKIPPED, RunSource.SCHEDULE, "20:30", emptyList(), emptyList(), SkipReason.PAUSED),
                    ),
                ),
                onIntent = {},
                onOpenPlan = {},
            )
        }
    }
}
