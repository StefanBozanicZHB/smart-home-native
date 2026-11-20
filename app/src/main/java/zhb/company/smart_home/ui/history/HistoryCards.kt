package zhb.company.smart_home.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import zhb.company.smart_home.R
import zhb.company.smart_home.data.history.BarUnit
import zhb.company.smart_home.data.history.HistoryCharts
import zhb.company.smart_home.data.history.HistoryDoc
import zhb.company.smart_home.data.history.HistoryMeta
import zhb.company.smart_home.data.history.HistoryPeriod
import zhb.company.smart_home.data.history.HistoryRepository
import zhb.company.smart_home.data.history.HistorySeriesNames
import zhb.company.smart_home.data.remote.RtdbError
import zhb.company.smart_home.ui.components.AvailabilityStrip
import zhb.company.smart_home.ui.components.BarLineChart
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.ChartAxis
import zhb.company.smart_home.ui.components.ChartLine
import zhb.company.smart_home.ui.components.ChartReadout
import zhb.company.smart_home.ui.components.ChoiceGroup
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.Legend
import zhb.company.smart_home.ui.components.LegendStyle
import zhb.company.smart_home.ui.components.LineChart
import zhb.company.smart_home.ui.components.PeriodTabs
import zhb.company.smart_home.ui.components.Sparkline
import zhb.company.smart_home.ui.components.XTick
import zhb.company.smart_home.ui.format.Durations
import zhb.company.smart_home.ui.theme.AppTheme

private val SR = Locale.forLanguageTag("sr-Latn")

@Composable
private fun periodOptions() = listOf(
    HistoryPeriod.DAY to stringResource(R.string.history_24h),
    HistoryPeriod.WEEK to stringResource(R.string.history_7d),
    HistoryPeriod.MONTH to stringResource(R.string.history_30d),
)

/**
 * Home heating card: room temperature over the last 24 hours, shaded where the pump ran, with
 * the target as a dashed line. Shows nothing until there is history.
 */
@Composable
fun RoomTrend(state: HistoryState, zone: String = "ground") {
    val day = state.doc(HistoryPeriod.DAY) ?: return
    val room = day[HistorySeriesNames.ROOM_GROUND_T]?.takeIf { it.hasData } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Sparkline(
            values = room.avg,
            color = AppTheme.status.active,
            shade = day[HistorySeriesNames.PUMP]?.avg,
            reference = day[HistorySeriesNames.target(zone)]?.avg,
            description = stringResource(R.string.history_room_trend_description),
        )
        ChartReadout(stringResource(R.string.history_room_trend_caption))
    }
}

/** Heating screen: pump run time per hour (24 h) or per day (7 / 30 days) and the outdoor mean. */
@Composable
fun PumpRunCard(state: HistoryState) {
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.WEEK) }
    var selected by remember(period) { mutableStateOf<Int?>(null) }
    val data = HistoryCharts.pumpRuns(period, state.doc(HistoryPeriod.DAY), state.doc(HistoryPeriod.MONTH))
    DashboardCard {
        CardHeader(Icons.Outlined.BarChart, stringResource(R.string.history_pump_title)) {
            PeriodTabs(periodOptions(), period, { period = it })
        }
        val loading = state.isLoading(if (period == HistoryPeriod.DAY) HistoryPeriod.DAY else HistoryPeriod.MONTH)
        if (data == null || !data.hasBars) {
            HistoryPlaceholder(state.error, loading)
            return@DashboardCard
        }
        val bars = AppTheme.status.active
        val line = AppTheme.status.cold
        val unit = if (data.barUnit == BarUnit.MINUTES) "min" else "h"
        BarLineChart(
            bars = data.bars,
            line = data.line,
            barColor = bars,
            lineColor = line,
            xTicks = xTicks(data.starts, data.endMs),
            barFormat = { "${it.toInt()} $unit" },
            lineFormat = { "${it.toInt()}°" },
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.history_pump_description),
        )
        val i = selected
        if (i != null && i in data.bars.indices) {
            val parts = listOfNotNull(
                pointLabel(data.starts[i], if (data.barUnit == BarUnit.MINUTES) Granularity.HOUR else Granularity.DAY),
                data.bars[i]?.let { runText(it, data.barUnit) } ?: stringResource(R.string.history_no_data),
                data.line[i]?.let { stringResource(R.string.history_outdoor_value, temp(it)) },
            )
            ChartReadout(parts.joinToString(" · "), emphasized = true)
        } else {
            Legend(
                listOf(
                    Triple(bars, stringResource(if (data.barUnit == BarUnit.MINUTES) R.string.history_pump_legend_minutes else R.string.history_pump_legend_hours), LegendStyle.BAR),
                    Triple(line, stringResource(R.string.history_outdoor), LegendStyle.SOLID),
                ),
            )
            val total = data.bars.filterNotNull().sum()
            ChartReadout(stringResource(R.string.history_pump_total, runText(total, data.barUnit)))
        }
    }
}

/** House screen: temperature (solid, min/max band) and humidity (dashed) for one place. */
@Composable
fun ClimateHistoryCard(state: HistoryState) {
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.DAY) }
    var place by rememberSaveable { mutableStateOf(Place.UPPER) }
    var selected by remember(period, place) { mutableStateOf<Int?>(null) }
    val doc = state.doc(period)
    DashboardCard {
        CardHeader(Icons.Outlined.ShowChart, stringResource(R.string.history_climate_title)) {
            PeriodTabs(periodOptions(), period, { period = it })
        }
        ChoiceGroup(
            options = Place.entries.map { it to stringResource(it.label) },
            selected = place,
            onSelect = { place = it },
        )
        val t = doc?.get(place.temperature)?.takeIf { it.hasData }
        val h = doc?.get(place.humidity)?.takeIf { it.hasData }
        if (doc == null || (t == null && h == null)) {
            HistoryPlaceholder(state.error, state.isLoading(period))
            return@DashboardCard
        }
        val tc = AppTheme.status.warm
        val hc = AppTheme.status.cold
        LineChart(
            lines = listOfNotNull(
                t?.let { ChartLine(it.avg, tc, low = it.min, high = it.max) },
                h?.let { ChartLine(it.avg, hc, dashed = true, axis = ChartAxis.RIGHT) },
            ),
            xTicks = xTicks(doc.times, doc.endMs),
            leftFormat = { "${it.toInt()}°" },
            rightFormat = { "${it.toInt()} %" },
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.history_climate_description, stringResource(place.label)),
        )
        val i = selected
        if (i != null && i in doc.times.indices) {
            val parts = listOfNotNull(
                pointLabel(doc.times[i], granularity(period)),
                t?.avg?.getOrNull(i)?.let { v ->
                    val lo = t.min?.getOrNull(i)
                    val hi = t.max?.getOrNull(i)
                    if (lo != null && hi != null) "${temp(v)} (${temp(lo)}–${temp(hi)})" else temp(v)
                },
                h?.avg?.getOrNull(i)?.let { "${it.toInt()} %" },
            )
            ChartReadout(if (parts.size > 1) parts.joinToString(" · ") else parts.first() + " · " + stringResource(R.string.history_no_data), emphasized = true)
        } else {
            Legend(
                listOfNotNull(
                    t?.let { Triple(tc, stringResource(R.string.history_temperature), LegendStyle.SOLID) },
                    h?.let { Triple(hc, stringResource(R.string.history_humidity), LegendStyle.DASHED) },
                ),
            )
            if (period != HistoryPeriod.DAY && t?.min != null) ChartReadout(stringResource(R.string.history_band_hint))
        }
    }
}

enum class Place(val label: Int, val temperature: String, val humidity: String) {
    GROUND(R.string.home_room_ground, HistorySeriesNames.ROOM_GROUND_T, HistorySeriesNames.ROOM_GROUND_H),
    UPPER(R.string.home_room_upper, HistorySeriesNames.ROOM_UPPER_T, HistorySeriesNames.ROOM_UPPER_H),
    OUTDOOR(R.string.history_outdoor, HistorySeriesNames.OUTDOOR_T, HistorySeriesNames.OUTDOOR_H),
}

/** System screen: per day over the last 7 days, was the node online the whole time. */
@Composable
fun AvailabilityCard(state: HistoryState, nodes: List<Pair<String, String>>) {
    val month = state.doc(HistoryPeriod.MONTH)
    val withData = nodes.filter { (id, _) -> HistoryCharts.availability(month, id).any { it.second != null } }
    var node by rememberSaveable { mutableStateOf(nodes.firstOrNull()?.first.orEmpty()) }
    LaunchedEffect(withData) {
        if (withData.isNotEmpty() && withData.none { it.first == node }) node = withData.first().first
    }
    DashboardCard {
        CardHeader(Icons.Outlined.WifiTethering, stringResource(R.string.history_availability_title)) {
            if (withData.size > 1) PeriodTabs(withData.map { it.first to it.first }, node, { node = it })
            else if (withData.size == 1) Text(withData.first().first, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val days = HistoryCharts.availability(month, node)
        if (days.none { it.second != null }) {
            HistoryPlaceholder(state.error, state.isLoading(HistoryPeriod.MONTH))
            return@DashboardCard
        }
        AvailabilityStrip(
            values = days.map { it.second },
            labels = days.map { it.first.format(DateTimeFormatter.ofPattern("EEEEE", SR)).uppercase(SR) },
        )
        val known = days.mapNotNull { it.second }
        val avg = known.average() * 100
        val label = nodes.firstOrNull { it.first == node }?.second ?: node
        ChartReadout(stringResource(R.string.history_availability_caption, label, String.format(SR, "%.1f", avg)))
    }
}

/** "logger radi" and friends for the broker card, from `history/v1/meta`; null = nothing known. */
@Composable
fun loggerStatusText(meta: HistoryMeta?, now: Long = System.currentTimeMillis()): Pair<String, Boolean>? {
    meta ?: return null
    val updated = meta.updatedMs ?: return null
    if (now - updated > HistoryRepository.STALE_MS) {
        return stringResource(R.string.history_rpi_stale, Durations.compact((now - updated) / 1000)) to false
    }
    val last = meta.loggerLastMs ?: return stringResource(R.string.history_logger_empty) to false
    val silent = updated - last
    return if (silent > LOGGER_SILENT_MS) {
        stringResource(R.string.history_logger_silent, Durations.compact(silent / 1000)) to false
    } else {
        stringResource(R.string.history_logger_ok) to true
    }
}

private const val LOGGER_SILENT_MS = 15 * 60_000L

@Composable
private fun HistoryPlaceholder(error: RtdbError?, loading: Boolean) {
    val text = when {
        loading -> R.string.history_loading
        error == RtdbError.PERMISSION_DENIED -> R.string.history_error_permission
        error != null -> R.string.history_error_network
        else -> R.string.history_empty
    }
    ChartReadout(stringResource(text))
}

// --- labels ---------------------------------------------------------------------------------

private enum class Granularity { MINUTE, HOUR, DAY }

private fun granularity(period: HistoryPeriod) = when (period) {
    HistoryPeriod.DAY -> Granularity.MINUTE
    HistoryPeriod.WEEK -> Granularity.HOUR
    HistoryPeriod.MONTH -> Granularity.DAY
}

private fun pointLabel(ms: Long, g: Granularity, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    val pattern = when (g) {
        Granularity.MINUTE -> "HH:mm"
        Granularity.HOUR -> "EEE HH:mm"
        Granularity.DAY -> "EEE d. M."
    }
    return t.format(DateTimeFormatter.ofPattern(pattern, SR))
}

/**
 * Labels under the x axis, chosen from the spacing of the points: every 6 hours within a day,
 * midnights (weekday) for hourly points over days, each weekday for up to 7 days, else weekly dates.
 */
fun xTicks(times: List<Long>, endMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<XTick> {
    if (times.isEmpty()) return emptyList()
    val step = if (times.size > 1) times[1] - times[0] else endMs - times[0]
    val span = endMs - times.first()
    val zoned = times.map { Instant.ofEpochMilli(it).atZone(zone) }
    return when {
        step < 3_600_000L * 2 && span <= 26 * 3_600_000L -> zoned.mapIndexedNotNull { i, t ->
            if (t.minute == 0 && t.hour % 6 == 0) XTick(i, t.format(DateTimeFormatter.ofPattern("HH", SR))) else null
        }
        step < 3_600_000L * 2 -> zoned.mapIndexedNotNull { i, t ->
            if (t.minute == 0 && t.hour == 0) XTick(i, t.format(DateTimeFormatter.ofPattern("EEE", SR))) else null
        }
        times.size <= 7 -> zoned.mapIndexed { i, t -> XTick(i, t.format(DateTimeFormatter.ofPattern("EEE", SR))) }
        else -> zoned.mapIndexedNotNull { i, t ->
            if ((times.size - 1 - i) % 7 == 0) XTick(i, t.format(DateTimeFormatter.ofPattern("d.M.", SR))) else null
        }
    }
}

private fun temp(v: Float) = String.format(SR, "%.1f°", v)

private fun runText(value: Float, unit: BarUnit): String {
    val seconds = when (unit) {
        BarUnit.MINUTES -> (value * 60).toLong()
        BarUnit.HOURS -> (value * 3600).toLong()
    }
    return if (seconds < 60) "0 min" else Durations.compact(seconds - seconds % 60)
}
