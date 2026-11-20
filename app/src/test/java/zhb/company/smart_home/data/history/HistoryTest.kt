package zhb.company.smart_home.data.history

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import zhb.company.smart_home.ui.history.xTicks

class HistoryTest {

    private val zone = ZoneId.of("Europe/Belgrade")

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `csv keeps gaps as null`() {
        assertEquals(listOf(21.5f, null, 21.25f, 0f), HistoryParser.csv("21.5,,21.25,0"))
        assertEquals(emptyList<Float?>(), HistoryParser.csv(""))
    }

    @Test
    fun `day document parses time axis and series, short series are padded`() {
        val doc = HistoryParser.parse(
            HistoryPeriod.DAY,
            obj("""{"v":1,"period":"day","count":3,"start_ms":1000,"step_ms":600000,"updated_ms":5,
                "series":{"pump":{"unit":"bool","label":"Pumpa","avg":"0,0.5"},"x":{"unit":"C"}}}"""),
            zone,
        )!!
        assertEquals(listOf(1000L, 601000L, 1201000L), doc.times)
        assertEquals(1801000L, doc.endMs)
        assertEquals(listOf(0f, 0.5f, null), doc["pump"]!!.avg)
        assertNull("series without avg is dropped", doc["x"])
        assertEquals(5L, doc.updatedMs)
    }

    @Test
    fun `month document uses local days, 23 hours on the clock change`() {
        val doc = HistoryParser.parse(
            HistoryPeriod.MONTH,
            obj("""{"count":2,"first_day":"2026-03-28","series":{"pump":{"unit":"bool","avg":"0.1,0.2","on_h":"2.4,4.8"}}}"""),
            zone,
        )!!
        assertEquals(LocalDate.of(2026, 3, 28).atStartOfDay(zone).toInstant().toEpochMilli(), doc.times[0])
        assertEquals(23 * 3_600_000L, doc.endMs - doc.times[1])
        assertEquals(listOf(2.4f, 4.8f), doc["pump"]!!.onHours)
    }

    @Test
    fun `missing count or axis gives null`() {
        assertNull(HistoryParser.parse(HistoryPeriod.DAY, obj("""{"start_ms":1,"step_ms":2}""")))
        assertNull(HistoryParser.parse(HistoryPeriod.MONTH, obj("""{"count":3}""")))
    }

    private fun day(pump: List<Float?>, outdoor: List<Float?>, startMs: Long): HistoryDoc {
        val times = pump.indices.map { startMs + it * 600_000L }
        return HistoryDoc(
            HistoryPeriod.DAY, times, times.last() + 600_000L,
            mapOf("pump" to HistorySeries("bool", "", pump), "outdoor_t" to HistorySeries("C", "", outdoor)),
            null,
        )
    }

    @Test
    fun `hourly pump minutes from 10 minute shares`() {
        val start = 1_800_000_000_000L / 3_600_000L * 3_600_000L // whole hour
        // hour 1: 6 points at 50 % -> 30 min; hour 2: two known points (100 %, 0 %) -> 10 min
        val pump = List(6) { 0.5f } + listOf(1f, 0f, null, null, null, null)
        val outdoor = List(6) { 2f } + listOf(4f, 6f, null, null, null, null)
        val data = HistoryCharts.pumpRuns(HistoryPeriod.DAY, day(pump, outdoor, start), null)!!
        assertEquals(BarUnit.MINUTES, data.barUnit)
        assertEquals(listOf(30f, 10f), data.bars)
        assertEquals(listOf(2f, 5f), data.line)
        assertEquals(listOf(start, start + 3_600_000L), data.starts)
    }

    @Test
    fun `daily pump hours and availability from the month document`() {
        val times = (0 until 30).map { LocalDate.of(2026, 11, 1).plusDays(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() }
        val month = HistoryDoc(
            HistoryPeriod.MONTH, times, times.last() + 86_400_000L,
            mapOf(
                "pump" to HistorySeries("bool", "", List(30) { 0.25f }, onHours = List(30) { it.toFloat() }),
                "online_node4" to HistorySeries("seen", "", List(29) { 1f } + listOf<Float?>(null)),
            ),
            null,
        )
        val week = HistoryCharts.pumpRuns(HistoryPeriod.WEEK, null, month)!!
        assertEquals(BarUnit.HOURS, week.barUnit)
        assertEquals((23..29).map { it.toFloat() }, week.bars)
        assertEquals(30, HistoryCharts.pumpRuns(HistoryPeriod.MONTH, null, month)!!.bars.size)
        assertEquals(List(7) { null }, week.line)

        val avail = HistoryCharts.availability(month, "node4", zone = zone)
        assertEquals(7, avail.size)
        assertEquals(LocalDate.of(2026, 11, 30), avail.last().first)
        assertNull(avail.last().second)
        assertEquals(1f, avail.first().second)
        assertEquals(emptyList<Pair<LocalDate, Float?>>(), HistoryCharts.availability(month, "node5"))
    }

    @Test
    fun `latest value skips trailing gaps`() {
        val doc = day(listOf(0f, 1f, null), listOf(null, null, null), 0)
        assertEquals(600_000L to 1f, HistoryCharts.latest(doc, "pump"))
        assertNull(HistoryCharts.latest(doc, "outdoor_t"))
    }

    @Test
    fun `x ticks follow the spacing of the points`() {
        val midnight = LocalDate.of(2026, 11, 20).atStartOfDay(zone).toInstant().toEpochMilli()
        val tenMin = (0 until 144).map { midnight + it * 600_000L }
        assertEquals(listOf("00", "06", "12", "18"), xTicks(tenMin, tenMin.last() + 600_000L, zone).map { it.label })
        val hourly = (0 until 168).map { midnight + it * 3_600_000L }
        assertEquals(7, xTicks(hourly, hourly.last() + 3_600_000L, zone).size)
        val days = (0 until 30).map { LocalDate.of(2026, 11, 1).plusDays(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() }
        val ticks = xTicks(days, days.last() + 86_400_000L, zone)
        assertEquals(29, ticks.last().index)
        assertEquals("30.11.", ticks.last().label)
        assertNotNull(ticks.firstOrNull())
    }

    @Test
    fun `delta and hours per degree day`() {
        val doc = HistoryDoc(
            HistoryPeriod.MONTH, listOf(0L, 1L, 2L), 3L,
            mapOf(
                "pipe_supply" to HistorySeries("C", "", listOf(34f, null, 30f)),
                "pipe_return" to HistorySeries("C", "", listOf(30f, 29f, 27.5f)),
                "pump" to HistorySeries("bool", "", listOf(0.3f, 0.2f, 0f), onHours = listOf(6f, 4f, 0f)),
                "outdoor_t" to HistorySeries("C", "", listOf(8f, 13f, null)),
            ),
            null,
        )
        assertEquals(listOf(4f, null, 2.5f), HistoryCharts.delta(doc, "pipe_supply", "pipe_return"))
        assertNull(HistoryCharts.delta(doc, "pipe_well_in", "pipe_well_out"))
        val dd = HistoryCharts.hoursPerDegreeDay(doc, 30)!!
        // day 3 has no outdoor value and is left out: 10 h over (10 + 5) degree-days
        assertEquals(10f, dd.runHours)
        assertEquals(15f, dd.degreeDays)
        assertEquals(10f / 15f, dd.ratio!!, 1e-6f)
    }
}
