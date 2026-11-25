package zhb.company.smart_home.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin
import zhb.company.smart_home.data.history.HistoryDoc
import zhb.company.smart_home.data.history.HistoryMeta
import zhb.company.smart_home.data.history.HistoryPeriod
import zhb.company.smart_home.data.history.HistorySeries
import zhb.company.smart_home.data.remote.RtdbError
import zhb.company.smart_home.ui.theme.SmartHomeTheme

/** Made-up history for previews: a day with a daily rhythm, a week and a month. */
internal object PreviewHistory {
    private val zone = ZoneId.of("Europe/Belgrade")
    private val midnight = LocalDate.of(2026, 11, 20).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun wave(n: Int, perCycle: Int, base: Float, amp: Float, phase: Float = 0f) =
        List(n) { i -> base + amp * sin(2 * PI * (i.toFloat() / perCycle + phase)).toFloat() }

    private fun gaps(v: List<Float>, from: Int, to: Int): List<Float?> = v.mapIndexed { i, x -> if (i in from until to) null else x }

    val day: HistoryDoc = run {
        val n = 144
        val room = wave(n, n, 21.3f, 0.5f, 0.6f)
        HistoryDoc(
            HistoryPeriod.DAY, List(n) { midnight - 6 * 3_600_000L + it * 600_000L }, midnight + 18 * 3_600_000L,
            mapOf(
                "room_ground_t" to HistorySeries("C", "", gaps(room, 70, 76)),
                "room_ground_h" to HistorySeries("pct", "", wave(n, n, 45f, 4f)),
                "room_upper_t" to HistorySeries("C", "", wave(n, n, 22.2f, 0.7f, 0.5f)),
                "room_upper_h" to HistorySeries("pct", "", wave(n, n, 40f, 3f, 0.1f)),
                "bedroom_ground_t" to HistorySeries("C", "", wave(n, n, 20.4f, 0.5f, 0.3f)),
                "bedroom_ground_h" to HistorySeries("pct", "", wave(n, n, 48f, 3f, 0.2f)),
                "outdoor_t" to HistorySeries("C", "", wave(n, n, 3.5f, 3f, 0.7f)),
                "outdoor_h" to HistorySeries("pct", "", wave(n, n, 78f, 12f, 0.2f)),
                "target_ground" to HistorySeries("C", "", List(n) { if (it in 36..138) 21.5f else 19.5f }),
                "pump" to HistorySeries("bool", "", List(n) { i -> if ((i / 9) % 3 == 0) 1f else if ((i / 9) % 3 == 1) 0.3f else 0f }),
                "pipe_supply" to HistorySeries("C", "", List(n) { i -> if ((i / 9) % 3 != 2) 34f + (i % 9) * 0.2f else null }),
                "pipe_return" to HistorySeries("C", "", List(n) { i -> if ((i / 9) % 3 != 2) 29.5f + (i % 9) * 0.1f else null }),
                "pipe_well_in" to HistorySeries("C", "", List(n) { i -> if ((i / 9) % 3 != 2) 11.2f else null }),
                "pipe_well_out" to HistorySeries("C", "", List(n) { i -> if ((i / 9) % 3 != 2) 8.1f - (i % 9) * 0.05f else null }),
            ),
            midnight,
        )
    }

    val week: HistoryDoc = run {
        val n = 168
        val t = wave(n, 24, 22.0f, 0.6f)
        HistoryDoc(
            HistoryPeriod.WEEK, List(n) { midnight - 6 * 86_400_000L + it * 3_600_000L }, midnight + 86_400_000L,
            mapOf(
                "room_upper_t" to HistorySeries("C", "", t, min = t.map { it - 0.4f }, max = t.map { it + 0.4f }),
                "room_upper_h" to HistorySeries("pct", "", wave(n, 24, 41f, 3f)),
            ),
            midnight,
        )
    }

    val month: HistoryDoc = run {
        val first = LocalDate.of(2026, 10, 22)
        val times = (0 until 30).map { first.plusDays(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() }
        HistoryDoc(
            HistoryPeriod.MONTH, times, times.last() + 86_400_000L,
            mapOf(
                "pump" to HistorySeries("bool", "", List(30) { 0.3f }, onHours = wave(30, 9, 7f, 3f)),
                "outdoor_t" to HistorySeries("C", "", wave(30, 9, 4f, 3f, 0.5f)),
                "online_node4" to HistorySeries("seen", "", List(30) { if (it == 26) 0.987f else 1f }),
                "online_node5" to HistorySeries("seen", "", List(29) { 1f } + listOf<Float?>(0.3f)),
            ),
            midnight,
        )
    }

    val state = HistoryState(
        docs = mapOf(HistoryPeriod.DAY to day, HistoryPeriod.WEEK to week, HistoryPeriod.MONTH to month),
        meta = HistoryMeta(System.currentTimeMillis(), System.currentTimeMillis() - 60_000, 120, 14_000),
        metaLoaded = true,
    )
}

@Preview(name = "History cards", heightDp = 1100)
@Preview(name = "History cards - dark", heightDp = 1100, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryCardsPreview() {
    SmartHomeTheme(darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                zhb.company.smart_home.ui.components.DashboardCard { RoomTrend(PreviewHistory.state) }
                PumpRunCard(PreviewHistory.state)
                ClimateHistoryCard(PreviewHistory.state)
                AvailabilityCard(PreviewHistory.state, listOf("node4" to "Dnevni boravak (prizemlje)", "node5" to "Grejanje"))
            }
        }
    }
}

@Preview(name = "History cards - no access", heightDp = 400)
@Composable
private fun HistoryErrorPreview() {
    SmartHomeTheme(darkTheme = false) {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PumpRunCard(HistoryState(error = RtdbError.PERMISSION_DENIED))
                ClimateHistoryCard(HistoryState())
            }
        }
    }
}

@Preview(name = "Heating history cards", heightDp = 1300)
@Composable
private fun HeatingHistoryPreview() {
    SmartHomeTheme(darkTheme = false) {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RoomTargetCard(PreviewHistory.state, "ground", "Prizemlje")
                PumpRunCard(PreviewHistory.state)
                PipeDeltaCard(PreviewHistory.state)
            }
        }
    }
}
