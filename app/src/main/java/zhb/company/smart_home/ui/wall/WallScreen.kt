package zhb.company.smart_home.ui.wall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.data.wall.DoorLogEntry
import zhb.company.smart_home.data.wall.WallProblem
import zhb.company.smart_home.data.wall.WeatherRepository
import zhb.company.smart_home.domain.door.AccessMethod
import zhb.company.smart_home.domain.heating.HeatPumpStatus
import zhb.company.smart_home.domain.wall.Forecast
import zhb.company.smart_home.domain.wall.SunTimes
import zhb.company.smart_home.domain.wall.WeatherKind
import zhb.company.smart_home.ui.format.NO_VALUE
import zhb.company.smart_home.ui.format.Readings

/**
 * The wall display (`docs/wall_display.md`): the time first, then the forecast and the house.
 * Landscape, laid out for a 960x600 dp tablet. [onOpenApp] goes to the normal screens.
 */
@Composable
fun WallScreen(darkTheme: Boolean, night: Boolean, onOpenApp: () -> Unit, viewModel: WallViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = if (darkTheme) WallColors.Dark else WallColors.Light
    CompositionLocalProvider(LocalWallColors provides colors) {
        Box(Modifier.fillMaxSize().background(colors.bg)) {
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(5f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ClockPanel()
                    WeatherPanel(state, Modifier.weight(1f))
                }
                Column(Modifier.weight(6f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoomsPanel(state.rooms)
                    state.heating?.let { HeatingPanel(it, state.nowMs) }
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DoorPanel(state.door, state.nowMs, Modifier.weight(1f).fillMaxHeight())
                        YardPanel(state.yard, Modifier.weight(1f).fillMaxHeight())
                    }
                    Footer(state, onOpenApp)
                }
            }
            // Night: everything a little darker on top of the low window brightness.
            if (night) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun Panel(modifier: Modifier = Modifier, padding: Int = 14, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalWallColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.panel)
            .border(1.dp, c.line, RoundedCornerShape(12.dp))
            .padding(horizontal = (padding + 2).dp, vertical = padding.dp),
        content = content,
    )
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = LocalWallColors.current.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        maxLines = 1,
    )
}

@Composable
private fun Head(label: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(label, Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.border(1.dp, color, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp),
    )
}

/** A label and value row with a thin line above it (none above the first). */
@Composable
private fun KeyValue(key: String, value: String, first: Boolean, valueColor: Color = LocalWallColors.current.fg) {
    val c = LocalWallColors.current
    if (!first) Spacer(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, color = c.muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(end = 8.dp))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun Big(text: String, size: TextUnit, color: Color = LocalWallColors.current.fg, unit: String? = null, light: Boolean = false) {
    val c = LocalWallColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text, color = color, style = TextStyle(fontFamily = if (light) CondensedLight else Condensed, fontSize = size, lineHeight = size))
        if (unit != null) Text(unit, color = c.muted, style = TextStyle(fontFamily = Condensed, fontSize = size * 0.45f, lineHeight = size * 0.45f), modifier = Modifier.padding(bottom = 2.dp))
    }
}

/** Serbian decimal comma for the wall's numbers: "22.9" → "22,9". */
private fun comma(value: String) = value.replace('.', ',')

private fun comma(value: Double) = comma(Readings.temperature(value))

// ---------------------------------------------------------------- left: clock

/** The only part that changes every second; it recomposes alone. */
@Composable
private fun ClockPanel() {
    val c = LocalWallColors.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000 - now % 1_000)
        }
    }
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(now).atZone(zone)
    val weekdays = stringArrayResource(R.array.wall_weekdays)
    val months = stringArrayResource(R.array.wall_months)
    val date = time.toLocalDate()
    val sun = remember(date) { SunTimes.of(date, zone) }
    Panel(Modifier.fillMaxWidth(), padding = 10) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${time.hour}:%02d".format(time.minute), color = c.fg, style = TextStyle(fontFamily = CondensedLight, fontSize = 116.sp, lineHeight = 116.sp))
            Text("%02d".format(time.second), color = c.muted, style = TextStyle(fontFamily = Condensed, fontSize = 30.sp), modifier = Modifier.padding(start = 8.dp, bottom = 14.dp))
        }
        Text(
            "${weekdays[time.dayOfWeek.value % 7]}, ${date.dayOfMonth}. ${months[date.monthValue - 1]} ${date.year}.",
            color = c.fg,
            fontSize = 18.sp,
        )
        if (sun != null) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val len = sun.dayLength.toMinutes()
                Text(stringResource(R.string.wall_sunrise, hm(sun.sunrise)), color = c.muted, fontSize = 13.sp)
                Text(stringResource(R.string.wall_sunset, hm(sun.sunset)), color = c.muted, fontSize = 13.sp)
                Text(stringResource(R.string.wall_day_length, len / 60, len % 60), color = c.muted, fontSize = 13.sp)
            }
        }
    }
}

private fun hm(t: ZonedDateTime) = "${t.hour}:%02d".format(t.minute)

private fun hm(ms: Long) = hm(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

// ---------------------------------------------------------------- left: outside and forecast

@Composable
private fun WeatherPanel(state: WallState, modifier: Modifier) {
    val c = LocalWallColors.current
    val forecast = state.forecast
    val outdoor = state.outdoor
    val today = LocalDate.now()
    Panel(modifier.fillMaxWidth()) {
        Head(stringResource(R.string.wall_outside)) {
            val age = forecast?.let { state.nowMs - it.fetchedMs }
            Text(
                if (age != null && age > WeatherRepository.OLD_MS) stringResource(R.string.wall_forecast_old, age / 3_600_000)
                else stringResource(R.string.wall_forecast_source),
                color = if (age != null && age > WeatherRepository.OLD_MS) c.accent else c.muted,
                fontSize = 10.sp,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            forecast?.currentKind?.let { WeatherIcon(it, Modifier.size(50.dp)) }
            Spacer(Modifier.width(12.dp))
            val measured = outdoor.measured != NO_VALUE
            if (measured) {
                Big("${comma(outdoor.measured)}°", 56.sp, color = if (outdoor.staleSinceMs != null) c.muted else c.fg)
            } else {
                // No reading from Node 2: a dash, and the forecast's temperature in muted text.
                Row(verticalAlignment = Alignment.Bottom) {
                    Big("–", 56.sp)
                    forecast?.currentTemperature?.let {
                        Text(" ${comma(it)}°", color = c.muted, style = TextStyle(fontFamily = Condensed, fontSize = 26.sp), modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                forecast?.currentKind?.let { Text(weatherText(it), color = c.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                val parts = buildList {
                    if (outdoor.staleSinceMs != null) add(stringResource(R.string.wall_measured_stale, hm(outdoor.staleSinceMs)))
                    else if (measured) add(stringResource(R.string.wall_measured))
                    if (outdoor.todayMin != null && outdoor.todayMax != null) add(stringResource(R.string.wall_today_range, comma(outdoor.todayMin), comma(outdoor.todayMax)))
                    forecast?.windKmh?.let { add(stringResource(R.string.wall_wind, Math.round(it))) }
                }
                Text(parts.joinToString(" · "), color = c.muted, fontSize = 12.sp, maxLines = 2)
            }
        }
        if (forecast == null) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.wall_forecast_none), color = c.muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            return@Panel
        }
        val hours = remember(forecast, state.nowMs / Forecast.HOUR_MS) { forecast.nextHours(state.nowMs) }
        HourlyChart(hours, stringResource(R.string.wall_now), Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp))
        Spacer(Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(c.line))
        val shortDays = stringArrayResource(R.array.wall_weekdays_short)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            forecast.daysFrom(today).forEach { d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (d.date == today) stringResource(R.string.wall_today) else shortDays[d.date.dayOfWeek.value % 7],
                        color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    WeatherIcon(d.kind, Modifier.padding(vertical = 2.dp).size(30.dp))
                    Row {
                        Text("${Math.round(d.max)}° ", color = c.fg, style = TextStyle(fontFamily = Condensed, fontSize = 17.sp))
                        Text("${Math.round(d.min)}°", color = c.muted, style = TextStyle(fontFamily = Condensed, fontSize = 17.sp))
                    }
                    Text(
                        if (d.precipitationMm >= 0.5) stringResource(R.string.wall_rain_mm, Math.round(d.precipitationMm)) else "",
                        color = c.cool, fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun weatherText(kind: WeatherKind): String = stringResource(
    when (kind) {
        WeatherKind.CLEAR -> R.string.wall_weather_clear
        WeatherKind.PARTLY -> R.string.wall_weather_partly
        WeatherKind.CLOUDY -> R.string.wall_weather_cloudy
        WeatherKind.FOG -> R.string.wall_weather_fog
        WeatherKind.DRIZZLE -> R.string.wall_weather_drizzle
        WeatherKind.RAIN -> R.string.wall_weather_rain
        WeatherKind.SHOWERS -> R.string.wall_weather_showers
        WeatherKind.SNOW -> R.string.wall_weather_snow
        WeatherKind.THUNDER -> R.string.wall_weather_thunder
    },
)

// ---------------------------------------------------------------- right: rooms

@Composable
private fun RoomsPanel(rooms: List<WallRoom>) {
    val c = LocalWallColors.current
    Panel(Modifier.fillMaxWidth()) {
        Head(stringResource(R.string.wall_rooms))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rooms.forEach { room ->
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(stringResource(room.name), color = c.fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (room.here) Text(" · " + stringResource(R.string.wall_here), color = c.accent, fontSize = 13.sp, maxLines = 1)
                    }
                    val stale = room.staleSinceMs != null
                    Big(comma(room.temperature), 36.sp, color = if (stale) c.muted else c.fg, unit = "°")
                    Text(
                        if (stale) stringResource(R.string.wall_stale_since, hm(room.staleSinceMs!!))
                        else stringResource(R.string.wall_humidity, room.humidity),
                        color = if (stale) c.accent else c.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- right: heating

@Composable
private fun HeatingPanel(h: WallHeating, nowMs: Long) {
    val c = LocalWallColors.current
    if (h.outOfSeason) {
        // Out of season: two lines, no empty charts.
        Panel(Modifier.fillMaxWidth(), padding = 10) {
            Head(stringResource(R.string.wall_heating)) { Pill(stringResource(R.string.wall_heating_off_season), c.muted) }
            val last = h.lastRunEndMs?.let { dayTime(it, nowMs) }
                ?: h.lastRunDay?.let { stringResource(R.string.wall_date_short, it.dayOfMonth, stringArrayResource(R.array.wall_months)[it.monthValue - 1]) }
                ?: NO_VALUE
            KeyValue(stringResource(R.string.wall_heating_last_run), last, first = true)
            if (h.status == HeatPumpStatus.Off) KeyValue(stringResource(R.string.wall_heating_main_switch), stringResource(R.string.wall_heating_main_off), first = false)
        }
        return
    }
    val zone = ZoneId.systemDefault()
    val dayStart = remember(nowMs / 3_600_000) { LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli() }
    Panel(Modifier.fillMaxWidth()) {
        Head(stringResource(R.string.wall_heating)) {
            when (val s = h.status) {
                is HeatPumpStatus.Running -> Pill(
                    s.minutes?.let { stringResource(R.string.wall_heating_running_for, it) } ?: stringResource(R.string.wall_heating_running),
                    c.warm,
                )
                is HeatPumpStatus.SafeMode -> Pill(stringResource(R.string.wall_heating_safe), c.accent)
                HeatPumpStatus.Unknown -> Pill(NO_VALUE, c.muted)
                else -> Pill(stringResource(R.string.wall_heating_idle), c.ok)
            }
        }
        val total = h.today.totalMinutes
        Row(verticalAlignment = Alignment.Bottom) {
            Big(if (total >= 60) "${total / 60} h ${total % 60}" else "$total", 34.sp)
            Text(
                " " + stringResource(R.string.wall_heating_min_today),
                color = c.muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.wall_heating_cycles, h.today.cycles, h.today.cycles) + " · " +
                    stringResource(R.string.wall_heating_flow, comma(h.flowTemp)),
                color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        RunStrip(h.today.runs, dayStart, nowMs, Modifier.padding(top = 10.dp).fillMaxWidth().height(20.dp).clip(RoundedCornerShape(5.dp)))
        Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
            listOf("0", "6", "12", "18", "24").forEachIndexed { i, t ->
                Text(t, color = c.muted, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = when (i) {
                    0 -> androidx.compose.ui.text.style.TextAlign.Start
                    4 -> androidx.compose.ui.text.style.TextAlign.End
                    else -> androidx.compose.ui.text.style.TextAlign.Center
                })
            }
        }
    }
}

/** "07:52" today, "juče 18:05", or "24. 11. 18:05". */
@Composable
private fun dayTime(ms: Long, nowMs: Long): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(ms).atZone(zone)
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val time = "%02d:%02d".format(at.hour, at.minute)
    return when (at.toLocalDate()) {
        today -> time
        today.minusDays(1) -> stringResource(R.string.wall_yesterday, time)
        else -> "${at.dayOfMonth}. ${at.monthValue}. $time"
    }
}

// ---------------------------------------------------------------- right: door and yard

@Composable
private fun DoorPanel(door: WallDoor, nowMs: Long, modifier: Modifier) {
    val c = LocalWallColors.current
    Panel(modifier) {
        Head(stringResource(R.string.wall_door)) {
            when {
                !door.known -> Pill(NO_VALUE, c.muted)
                door.lockOpen -> Pill(stringResource(R.string.wall_door_open), c.accent)
                else -> Pill(stringResource(R.string.wall_door_locked), c.ok)
            }
        }
        if (!door.readerOk) KeyValue(stringResource(R.string.wall_door_reader), stringResource(R.string.wall_door_reader_down), first = true, valueColor = c.accent)
        if (door.events.isEmpty()) {
            Text(stringResource(R.string.wall_door_no_events), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        door.events.forEachIndexed { i, e -> KeyValue(dayTime(e.atMs, nowMs), accessText(e), first = i == 0 && door.readerOk) }
    }
}

@Composable
private fun accessText(e: DoorLogEntry): String {
    val ev = e.event
    return when {
        ev.method == AccessMethod.BUTTON -> stringResource(R.string.wall_access_button)
        ev.method == AccessMethod.APP -> stringResource(R.string.wall_access_app)
        ev.method == AccessMethod.TAG && ev.granted -> stringResource(R.string.wall_access_card, ev.name ?: stringResource(R.string.wall_access_card_known))
        ev.method == AccessMethod.TAG -> stringResource(R.string.wall_access_unknown_card)
        ev.granted -> stringResource(R.string.wall_access_granted)
        else -> stringResource(R.string.wall_access_denied)
    }
}

@Composable
private fun YardPanel(yard: WallYard?, modifier: Modifier) {
    val c = LocalWallColors.current
    Panel(modifier) {
        Head(stringResource(R.string.wall_yard))
        if (yard == null) return@Panel
        @Composable
        fun light(on: Boolean, at: String?) = when {
            on -> stringResource(R.string.wall_on)
            at != null -> at
            else -> NO_VALUE
        }
        val lights = "${light(yard.neonOn, yard.neonAt)} / ${light(yard.lampOn, yard.lampAt)}"
        KeyValue(stringResource(R.string.wall_yard_lights), lights, first = true)
        KeyValue(
            stringResource(R.string.wall_yard_fountain),
            when {
                yard.fountainOn == true -> stringResource(R.string.wall_on)
                yard.fountainAt != null -> stringResource(R.string.wall_yard_starts_at, yard.fountainAt)
                yard.fountainOn == false -> stringResource(R.string.wall_off)
                else -> NO_VALUE
            },
            first = false,
        )
        KeyValue(
            stringResource(R.string.wall_yard_pool),
            if (yard.poolTemperature == NO_VALUE) NO_VALUE else "${comma(yard.poolTemperature)}°",
            first = false,
            valueColor = if (yard.poolStaleSinceMs != null) c.muted else c.fg,
        )
    }
}

// ---------------------------------------------------------------- bottom strip

@Composable
private fun Footer(state: WallState, onOpenApp: () -> Unit) {
    val c = LocalWallColors.current
    val f = state.footer
    Panel(Modifier.fillMaxWidth(), padding = 4) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                val problem = state.problem
                if (problem != null) {
                    Dot(c.accent)
                    Text(problemText(problem), color = c.accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Status(f.nodesOnline == f.nodesTotal, stringResource(R.string.wall_nodes_online, f.nodesOnline, f.nodesTotal))
                    Status(f.connected, stringResource(if (f.connected) R.string.wall_broker else R.string.wall_broker_down))
                }
                f.battery?.let { b ->
                    Status(
                        b.charging || b.percent >= 40,
                        stringResource(if (b.charging) R.string.wall_battery_charging else R.string.wall_battery, b.percent),
                    )
                }
            }
            IconButton(onClick = onOpenApp, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.wall_open_app), tint = c.muted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Status(ok: Boolean, text: String) {
    val c = LocalWallColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(if (ok) c.ok else c.accent)
        Text(text, color = c.muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.padding(end = 5.dp).size(7.dp).clip(CircleShape).background(color))
}

@Composable
private fun problemText(p: WallProblem) = stringResource(
    when (p) {
        WallProblem.NOT_SIGNED_IN -> R.string.wall_problem_signed_out
        WallProblem.NO_NETWORK -> R.string.wall_problem_network
        WallProblem.NO_BROKER -> R.string.wall_problem_broker
    },
)
