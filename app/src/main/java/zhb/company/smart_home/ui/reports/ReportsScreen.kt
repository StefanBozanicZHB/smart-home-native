package zhb.company.smart_home.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import zhb.company.smart_home.R
import zhb.company.smart_home.data.reports.HeatingReport
import zhb.company.smart_home.data.reports.ReportSummary
import zhb.company.smart_home.data.reports.ReportsError
import zhb.company.smart_home.ui.components.AlertBanner
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.StatTile
import zhb.company.smart_home.ui.components.StatusChip
import zhb.company.smart_home.ui.components.TileGrid
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.components.adaptiveColumns
import zhb.company.smart_home.ui.format.Durations

/** Daily heating reports from the RPi, newest first. */
@Composable
fun ReportsScreen(onOpen: (String) -> Unit, viewModel: ReportsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(title = stringResource(R.string.reports_title), horizontalPadding = 0.dp) {
                TextButton(onClick = viewModel::refresh, enabled = !state.loading) { Text(stringResource(R.string.reports_refresh)) }
            }
        }
        when {
            state.loading -> item { Loading() }
            state.error != null -> item { ErrorBanner(state.error!!) }
            state.items.isEmpty() -> item {
                Text(stringResource(R.string.reports_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> items(state.items, key = { it.date }) { ReportRow(it, onClick = { onOpen(it.date) }) }
        }
    }
}

@Composable
private fun ReportRow(item: ReportSummary, onClick: () -> Unit) {
    DashboardCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(formatDay(item.date), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (item.safeModeSeconds > 0) StatusChip(stringResource(R.string.reports_safe_mode_chip), Tone.WARNING)
            if ((item.completenessPct ?: 100.0) < 95.0) {
                StatusChip(stringResource(R.string.reports_data_chip, pct(item.completenessPct)), Tone.NEUTRAL, Modifier.padding(start = 6.dp))
            }
        }
        Text(
            stringResource(R.string.reports_row_pump, Durations.compact(item.runSeconds), item.starts),
            style = MaterialTheme.typography.bodyMedium,
        )
        val parts = listOfNotNull(
            range(item.roomMin, item.roomMax)?.let { stringResource(R.string.reports_row_room, it) },
            range(item.outdoorMin, item.outdoorMax)?.let { stringResource(R.string.reports_row_outdoor, it) },
        )
        if (parts.isNotEmpty()) {
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One day's report. */
@Composable
fun ReportDetailScreen(date: String, viewModel: ReportDetailViewModel = koinViewModel(key = "report-$date") { parametersOf(date) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(title = formatDay(date), subtitle = stringResource(R.string.reports_title), horizontalPadding = 0.dp) }
        val report = state.report
        when {
            state.loading -> item { Loading() }
            state.error != null -> item { ErrorBanner(state.error!!) }
            state.missing || report == null -> item {
                Text(stringResource(R.string.reports_missing), style = MaterialTheme.typography.bodyMedium)
            }
            else -> reportItems(report)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reportItems(r: HeatingReport) {
    item { PumpCard(r) }
    r.zones.toSortedMap().forEach { (zone, z) -> item(key = "zone-$zone") { ZoneCard(zone, z, single = r.zones.size == 1) } }
    r.outdoor?.let { o -> item { OutdoorCard(o, r.efficiency.runHoursPerHdd) } }
    if (r.pipes.isNotEmpty()) item { PipesCard(r.pipes) }
    if (r.safeMode.totalSeconds > 0) item { SafeModeCard(r.safeMode) }
    item { DataCard(r.data) }
}

@Composable
private fun PumpCard(r: HeatingReport) {
    val p = r.pump
    DashboardCard {
        CardHeader(Icons.Outlined.Bolt, stringResource(R.string.reports_pump))
        val cols = adaptiveColumns(3)
        TileGrid(
            cols,
            listOf(
                { m -> StatTile(stringResource(R.string.reports_run_time), Durations.compact(p.runSeconds), m) },
                { m -> StatTile(stringResource(R.string.reports_starts), p.starts.toString(), m) },
                { m ->
                    StatTile(
                        stringResource(R.string.reports_cycle_avg),
                        p.cycles?.let { Durations.compact(it.avgSeconds) } ?: "–",
                        m,
                        detail = p.cycles?.let { "${Durations.compact(it.minSeconds)} – ${Durations.compact(it.maxSeconds)}" },
                    )
                },
            ),
        )
        if (p.startSources.isNotEmpty()) {
            val sources = p.startSources.entries.sortedByDescending { it.value }.map { "${srcLabel(it.key)} ${it.value}" }
            Text(
                stringResource(R.string.reports_start_sources, sources.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ZoneCard(zone: String, z: HeatingReport.Zone, single: Boolean) {
    DashboardCard {
        CardHeader(Icons.Outlined.Thermostat, if (single) stringResource(R.string.reports_room) else stringResource(R.string.reports_room_zone, zone))
        TileGrid(
            adaptiveColumns(3),
            listOf(
                { m -> StatTile(stringResource(R.string.reports_min), temp(z.room?.min), m) },
                { m -> StatTile(stringResource(R.string.reports_avg), temp(z.room?.avg), m, detail = z.targetAvg?.let { stringResource(R.string.reports_target, temp(it)) }) },
                { m -> StatTile(stringResource(R.string.reports_max), temp(z.room?.max), m) },
            ),
        )
        if (z.belowTargetSeconds > 0) {
            Text(stringResource(R.string.reports_below_target, Durations.compact(z.belowTargetSeconds)), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OutdoorCard(o: HeatingReport.Outdoor, perHdd: Double?) {
    DashboardCard {
        CardHeader(Icons.Outlined.WbCloudy, stringResource(R.string.reports_outdoor))
        TileGrid(
            adaptiveColumns(3),
            listOf(
                { m -> StatTile(stringResource(R.string.reports_min), temp(o.min), m) },
                { m -> StatTile(stringResource(R.string.reports_avg), temp(o.avg), m) },
                { m -> StatTile(stringResource(R.string.reports_max), temp(o.max), m) },
            ),
        )
        o.hdd?.let { hdd ->
            Text(
                stringResource(R.string.reports_hdd, num(hdd, 1)) + (perHdd?.let { " · " + stringResource(R.string.reports_per_hdd, num(it * 60, 0)) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PipesCard(pipes: Map<String, Double>) {
    val labels = listOf(
        "supply" to R.string.reports_pipe_supply,
        "return" to R.string.reports_pipe_return,
        "delta" to R.string.reports_pipe_delta,
        "well_in" to R.string.reports_pipe_well_in,
        "well_out" to R.string.reports_pipe_well_out,
        "well_delta" to R.string.reports_pipe_well_delta,
    ).filter { pipes[it.first] != null }
    DashboardCard {
        CardHeader(Icons.Outlined.Thermostat, stringResource(R.string.reports_pipes))
        TileGrid(adaptiveColumns(3), labels.map { (key, label) -> { m: Modifier -> StatTile(stringResource(label), temp(pipes[key]), m) } })
    }
}

@Composable
private fun SafeModeCard(s: HeatingReport.SafeMode) {
    AlertBanner(
        Icons.Outlined.WarningAmber,
        stringResource(R.string.reports_safe_mode, Durations.compact(s.totalSeconds)),
        Tone.WARNING,
        detail = s.reasons.entries.joinToString(", ") { "${it.key} ${Durations.compact(it.value)}" },
    )
}

@Composable
private fun DataCard(d: HeatingReport.DataQuality) {
    DashboardCard {
        CardHeader(Icons.Outlined.DataUsage, stringResource(R.string.reports_data))
        Text(
            stringResource(R.string.reports_data_detail, pct(d.completenessPct), d.samples, d.expected, d.reboots),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun ErrorBanner(error: ReportsError) {
    val text = when (error) {
        ReportsError.NOT_SIGNED_IN -> R.string.reports_error_sign_in
        ReportsError.PERMISSION_DENIED -> R.string.reports_error_permission
        ReportsError.HTTP -> R.string.reports_error_network
    }
    AlertBanner(Icons.Outlined.WarningAmber, stringResource(text), Tone.DANGER)
}

@Composable
private fun srcLabel(src: String): String = when (src) {
    "thermostat" -> stringResource(R.string.reports_src_thermostat)
    "schedule" -> stringResource(R.string.reports_src_schedule)
    "app" -> stringResource(R.string.reports_src_app)
    else -> src
}

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, d. M. yyyy.", Locale.forLanguageTag("sr-Latn"))

internal fun formatDay(date: String): String =
    runCatching { LocalDate.parse(date).format(DAY_FORMAT).replaceFirstChar { it.uppercase() } }.getOrDefault(date)

private fun num(v: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", v)
private fun temp(v: Double?) = v?.let { num(it, 1) + "°" } ?: "–"
private fun pct(v: Double?) = v?.let { num(it, 0) + " %" } ?: "–"
private fun range(min: Double?, max: Double?) = if (min != null && max != null) "${num(min, 1)}–${num(max, 1)}°" else null

@androidx.compose.ui.tooling.preview.Preview(name = "Report", heightDp = 1400)
@Composable
private fun ReportPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                reportItems(
                    HeatingReport(
                        date = "2026-11-19",
                        pump = HeatingReport.Pump(22320, 9, mapOf("thermostat" to 7, "schedule" to 2), HeatingReport.Cycles(9, 2480, 1200, 4100)),
                        zones = mapOf("ground" to HeatingReport.Zone(HeatingReport.Range(20.6, 21.2, 21.9), 21.3, -0.1, 1800, 22000)),
                        outdoor = HeatingReport.Outdoor(-2.1, 1.4, 4.0, 16.6),
                        pipes = mapOf("supply" to 34.2, "return" to 29.8, "delta" to 4.4),
                        efficiency = HeatingReport.Efficiency(0.373),
                        data = HeatingReport.DataQuality(8611, 8640, 99.7, 0),
                    ),
                )
            }
        }
    }
}
