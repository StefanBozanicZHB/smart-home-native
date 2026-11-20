package zhb.company.smart_home.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Water
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.model.AcMode
import zhb.company.smart_home.ui.components.ActionTile
import zhb.company.smart_home.ui.components.AlertBanner
import zhb.company.smart_home.ui.components.BigValue
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.CompactRow
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.DeviceTile
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.SectionLabel
import zhb.company.smart_home.ui.components.StatTile
import zhb.company.smart_home.ui.components.StatusChip
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.format.NO_VALUE

@Composable
fun HomeScreen(
    onOpenHeating: () -> Unit,
    onOpenGarden: () -> Unit,
    onOpenHouse: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.ShowToast -> Toast.makeText(context, context.getString(effect.messageRes), Toast.LENGTH_SHORT).show()
            }
        }
    }

    HomeContent(state, viewModel::onIntent, onOpenHeating, onOpenGarden, onOpenHouse)

    if (state.showDevicesOnDialog) DevicesOnDialog(state.devicesOnList) { viewModel.onIntent(HomeIntent.DismissDevicesOnDialog) }
    if (state.showUnlockConfirm) UnlockDialog(viewModel::onIntent)
}

@Composable
private fun HomeContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onOpenHeating: () -> Unit,
    onOpenGarden: () -> Unit,
    onOpenHouse: () -> Unit,
    now: LocalDateTime = LocalDateTime.now(),
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { HomeHeader(state.connected, now) }
        state.alerts.forEach { alert -> item { HomeAlertBanner(alert) } }
        state.sections.forEach { section ->
            item(key = section.name) {
                when (section) {
                    HomeSection.HEATING_HERO -> HeatingHeroCard(state.heating, onOpenHeating)
                    HomeSection.GARDEN_HERO -> GardenHeroCard(state, onOpenGarden)
                    HomeSection.SUMMER_TILES -> SummerTiles(state, onOpenGarden, onOpenHouse)
                    HomeSection.ROOM_TEMPS -> RoomTemps(state, showOutdoor = HomeSection.GARDEN_HERO !in state.sections)
                    HomeSection.DEVICES -> Devices(state, onIntent)
                    HomeSection.HEATING_ROW -> CompactRow(
                        icon = Icons.Outlined.LocalFireDepartment,
                        text = stringResource(R.string.home_heating_off_row),
                        onClick = onOpenHeating,
                        actionLabel = stringResource(R.string.home_heating_turn_on).takeIf { state.heating.online },
                        onAction = { onIntent(HomeIntent.TurnHeatingOn) },
                    )
                    HomeSection.SPRINKLERS_ROW -> CompactRow(
                        icon = Icons.Outlined.WaterDrop,
                        text = stringResource(R.string.home_sprinklers_off_row),
                        onClick = onOpenGarden,
                    )
                    HomeSection.GOODNIGHT -> CompactRow(
                        icon = Icons.Outlined.Bedtime,
                        text = stringResource(R.string.goodnight_title),
                        onClick = { onIntent(HomeIntent.ShowDevicesOnDialog) },
                        actionLabel = stringResource(R.string.home_goodnight_button),
                        onAction = { onIntent(HomeIntent.Goodnight) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(connected: Boolean, now: LocalDateTime) {
    val greeting = stringResource(
        when (now.hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..17 -> R.string.home_greeting_day
            else -> R.string.home_greeting_evening
        },
    )
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, HH:mm", Locale.forLanguageTag("sr-Latn")))
        .replaceFirstChar { it.titlecase(Locale.forLanguageTag("sr-Latn")) }
    ScreenHeader(title = greeting, subtitle = date, horizontalPadding = 0.dp) {
        StatusChip(
            text = stringResource(if (connected) R.string.home_connected else R.string.home_disconnected),
            tone = if (connected) Tone.ACTIVE else Tone.DANGER,
        )
    }
}

@Composable
private fun HomeAlertBanner(alert: HomeAlert) {
    when (alert) {
        HomeAlert.NotConnected -> AlertBanner(
            Icons.Outlined.CloudOff,
            stringResource(R.string.home_alert_not_connected),
            Tone.DANGER,
            detail = stringResource(R.string.home_alert_not_connected_detail),
        )
        is HomeAlert.NodeOffline -> AlertBanner(
            Icons.Outlined.WarningAmber,
            stringResource(if (alert.lastWill) R.string.home_alert_node_last_will else R.string.home_alert_node_offline, alert.label),
            Tone.WARNING,
        )
        is HomeAlert.HeatingSafeMode -> AlertBanner(
            Icons.Outlined.WarningAmber,
            stringResource(R.string.home_alert_safe_mode),
            Tone.WARNING,
            detail = alert.reason,
        )
    }
}

@Composable
private fun zoneLabel(id: String): String = when (id) {
    "ground" -> stringResource(R.string.home_room_ground)
    "upper" -> stringResource(R.string.home_room_upper)
    else -> id
}

@Composable
private fun HeatingHeroCard(heating: HeatingSummary, onOpen: () -> Unit) {
    DashboardCard(onClick = onOpen) {
        CardHeader(Icons.Outlined.LocalFireDepartment, stringResource(R.string.home_heating)) {
            StatusChip(
                text = stringResource(if (heating.pump) R.string.heating_pump_running else R.string.heating_pump_idle),
                tone = if (heating.pump) Tone.ACTIVE else Tone.NEUTRAL,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            heating.zones.ifEmpty { listOf(ZoneSummary("ground", NO_VALUE, null, false)) }.forEach { zone ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(zoneLabel(zone.id), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BigValue("${zone.roomTemp}°")
                    val detail = listOfNotNull(
                        zone.target?.let { stringResource(R.string.home_target, it) },
                        stringResource(R.string.home_demand).takeIf { zone.demand },
                    ).joinToString(" · ")
                    if (detail.isNotEmpty()) {
                        Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(
            stringResource(R.string.home_flow, heating.flowTemp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GardenHeroCard(state: HomeState, onOpen: () -> Unit) {
    DashboardCard(onClick = onOpen) {
        CardHeader(Icons.Outlined.Grass, stringResource(R.string.home_tab_garden)) {
            val running = state.sprinklerRunningZone
            when {
                running != null -> StatusChip(stringResource(R.string.home_sprinklers_running, running), Tone.ACTIVE)
                state.sprinklerAuto -> StatusChip(stringResource(R.string.home_sprinklers_auto), Tone.ACTIVE)
                else -> StatusChip(stringResource(R.string.home_sprinklers_manual), Tone.NEUTRAL)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_outdoor), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigValue("${state.outdoorTemp}°")
                Text(
                    stringResource(R.string.home_humidity, state.outdoorHumidity),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_next_watering), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigValue(if (state.sprinklerAuto) state.sprinklerStartTime else stringResource(R.string.home_sprinklers_manual_value))
            }
        }
    }
}

@Composable
private fun SummerTiles(state: HomeState, onOpenGarden: () -> Unit, onOpenHouse: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        if (state.poolPumpOn || state.poolHeaterOn) {
            ActionTile(
                icon = Icons.Outlined.Pool,
                label = "${stringResource(R.string.garden_pool)} · ${state.poolTemp}°",
                detail = stringResource(if (state.poolHeaterOn) R.string.home_pool_heating_on else R.string.home_pool_running),
                onClick = onOpenGarden,
                modifier = Modifier.weight(1f),
            )
        }
        if (state.acOn) {
            ActionTile(
                icon = Icons.Outlined.AcUnit,
                label = "${stringResource(R.string.ac_title)} · ${state.acTemp}°",
                detail = stringResource(acModeLabel(state.acMode)),
                onClick = onOpenHouse,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun acModeLabel(mode: AcMode): Int = when (mode) {
    AcMode.COOL -> R.string.ac_mode_cool
    AcMode.AUTO -> R.string.ac_mode_auto
    AcMode.FAN -> R.string.ac_mode_fan
    AcMode.HEAT -> R.string.ac_mode_heat
}

@Composable
private fun RoomTemps(state: HomeState, showOutdoor: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        // In summer the yard card already shows the outdoor reading.
        if (showOutdoor) StatTile(stringResource(R.string.home_outdoor), "${state.outdoorTemp}°", Modifier.weight(1f), detail = stringResource(R.string.home_humidity, state.outdoorHumidity))
        StatTile(stringResource(R.string.home_room_ground), "${state.groundTemp}°", Modifier.weight(1f), detail = stringResource(R.string.home_humidity, state.groundHumidity))
        StatTile(stringResource(R.string.home_room_upper), "${state.upperTemp}°", Modifier.weight(1f), detail = stringResource(R.string.home_humidity, state.upperHumidity))
    }
}

@Composable
private fun Devices(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    val offline = stringResource(R.string.home_node_offline_tile)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val onCount = state.devicesOnList.size
        SectionLabel(if (onCount > 0) stringResource(R.string.home_on_count, onCount) else stringResource(R.string.home_devices)) {
            if (onCount > 0) {
                TextButton(onClick = { onIntent(HomeIntent.ShowDevicesOnDialog) }) { Text(stringResource(R.string.home_all_devices)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeviceTile(
                Icons.Outlined.Lightbulb, stringResource(R.string.home_led_strip), state.ledStripOn,
                { onIntent(HomeIntent.ToggleLedStrip(it)) }, Modifier.weight(1f),
                detail = stringResource(R.string.home_tab_garden), enabled = state.yardNodeOnline, disabledReason = offline,
            )
            DeviceTile(
                Icons.Outlined.Water, stringResource(R.string.home_fountain), state.fountainOn,
                { onIntent(HomeIntent.ToggleFountain(it)) }, Modifier.weight(1f),
                enabled = state.fountainNodeOnline, disabledReason = offline,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeviceTile(
                Icons.Outlined.Pool, stringResource(R.string.home_pool_pump), state.poolPumpOn,
                { onIntent(HomeIntent.TogglePoolPump(it)) }, Modifier.weight(1f),
                detail = "${state.poolTemp}°", enabled = state.poolNodeOnline, disabledReason = offline,
            )
            ActionTile(
                Icons.Outlined.DoorFront, stringResource(R.string.home_front_door),
                onClick = { if (state.doorNodeOnline) onIntent(HomeIntent.RequestUnlockFrontDoor) },
                modifier = Modifier.weight(1f),
                detail = if (state.doorNodeOnline) stringResource(R.string.home_unlock) else offline,
            )
        }
    }
}

@Composable
private fun DevicesOnDialog(devices: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text(stringResource(R.string.home_devices_on)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (devices.isEmpty()) Text(stringResource(R.string.home_nothing_on)) else devices.forEach { Text(it) }
            }
        },
    )
}

@Composable
private fun UnlockDialog(onIntent: (HomeIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(HomeIntent.DismissUnlockFrontDoor) },
        title = { Text(stringResource(R.string.home_unlock_title)) },
        text = { Text(stringResource(R.string.home_unlock_text)) },
        confirmButton = { TextButton(onClick = { onIntent(HomeIntent.ConfirmUnlockFrontDoor) }) { Text(stringResource(R.string.home_unlock_confirm)) } },
        dismissButton = { OutlinedButton(onClick = { onIntent(HomeIntent.DismissUnlockFrontDoor) }) { Text(stringResource(R.string.home_cancel)) } },
    )
}

// ---- previews ------------------------------------------------------------------------------------

private val PreviewWinter = HomeState(
    connected = true,
    alerts = listOf(HomeAlert.NodeOffline("Ulazna vrata", lastWill = false)),
    sections = HomeLayout.sections(HomeSignals(true, false, false, false, false, false)),
    heating = HeatingSummary(
        online = true, mainSwitch = true, pump = true, flowTemp = "34.3",
        zones = listOf(ZoneSummary("ground", "21.5", "21.5", true)),
    ),
    outdoorTemp = "3.5", outdoorHumidity = "81", groundTemp = "21.5", groundHumidity = "45", upperTemp = "22.4", upperHumidity = "40",
    poolTemp = "8.0", ledStripOn = true, yardNodeOnline = true, fountainNodeOnline = true, poolNodeOnline = true, doorNodeOnline = false,
    devicesOnList = listOf("LED traka (dvorište)"),
)

private val PreviewSummer = HomeState(
    connected = true,
    sections = HomeLayout.sections(HomeSignals(false, true, false, true, false, true)),
    heating = HeatingSummary(online = true, mainSwitch = false),
    outdoorTemp = "31.2", outdoorHumidity = "38", groundTemp = "25.1", groundHumidity = "52", upperTemp = "26.8", upperHumidity = "48",
    poolTemp = "26.4", sprinklerAuto = true, sprinklerStartTime = "05:30", poolPumpOn = true, acOn = true, acTemp = 24,
    yardNodeOnline = true, fountainNodeOnline = true, poolNodeOnline = true, doorNodeOnline = true,
    devicesOnList = listOf("Pumpa bazena", "Klima uređaj"),
)

@androidx.compose.ui.tooling.preview.Preview(name = "Home - winter", heightDp = 900)
@Composable
private fun HomeWinterPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface { HomeContent(PreviewWinter, {}, {}, {}, {}, LocalDateTime.of(2026, 11, 19, 21, 35)) }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Home - summer (dark)", heightDp = 900)
@Composable
private fun HomeSummerPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = true) {
        Surface { HomeContent(PreviewSummer, {}, {}, {}, {}, LocalDateTime.of(2027, 7, 10, 18, 10)) }
    }
}
