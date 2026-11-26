package zhb.company.smart_home.domain.wall

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zhb.company.smart_home.data.history.HistoryDoc
import zhb.company.smart_home.data.history.HistoryPeriod
import zhb.company.smart_home.data.history.HistorySeries
import zhb.company.smart_home.data.history.HistorySeriesNames

class HeatingDayTest {
    private val min = 60_000L
    private val step = 10 * min

    /** A day document with 10-minute points starting at [start]. */
    private fun day(start: Long, pump: List<Float?>, outdoor: List<Float?> = emptyList()) = HistoryDoc(
        period = HistoryPeriod.DAY,
        times = pump.indices.map { start + it * step },
        endMs = start + pump.size * step,
        series = mapOf(
            HistorySeriesNames.PUMP to HistorySeries("", "", pump),
            HistorySeriesNames.OUTDOOR_T to HistorySeries("°C", "", List(pump.size) { outdoor.getOrNull(it) }),
        ),
        updatedMs = null,
    )

    @Test
    fun `consecutive running points merge into one run`() {
        val doc = day(0, listOf(0f, 1f, 1f, 0.2f, 0.8f, 0f))
        val today = HeatingDay.today(doc, dayStartMs = 0, nowMs = 60 * min, runningSinceMs = null)
        assertEquals(listOf(PumpRun(10 * min, 30 * min), PumpRun(40 * min, 50 * min)), today.runs)
        assertEquals(30, today.totalMinutes)
        assertEquals(2, today.cycles)
    }

    @Test
    fun `points before midnight are cut off`() {
        val doc = day(-20 * min, listOf(1f, 1f, 1f, 0f))
        val today = HeatingDay.today(doc, dayStartMs = 0, nowMs = 40 * min, runningSinceMs = null)
        assertEquals(listOf(PumpRun(0, 10 * min)), today.runs)
    }

    @Test
    fun `run in progress extends the history to now`() {
        val doc = day(0, listOf(0f, 1f))
        val today = HeatingDay.today(doc, dayStartMs = 0, nowMs = 35 * min, runningSinceMs = 12 * min)
        assertEquals(listOf(PumpRun(10 * min, 35 * min)), today.runs)
        assertEquals(25, today.totalMinutes)
    }

    @Test
    fun `no history and nothing running is an empty day`() {
        val today = HeatingDay.today(null, 0, 60 * min, null)
        assertEquals(0, today.cycles)
        assertEquals(0, today.totalMinutes)
    }

    @Test
    fun `season follows the main switch and the last week`() {
        val today = LocalDate.of(2026, 11, 26)
        assertTrue(HeatingDay.outOfSeason(mainSwitchOn = false, running = false, lastRunDay = today, monthKnown = true, today = today))
        assertFalse(HeatingDay.outOfSeason(true, running = false, lastRunDay = today.minusDays(3), monthKnown = true, today = today))
        assertTrue(HeatingDay.outOfSeason(true, running = false, lastRunDay = today.minusDays(8), monthKnown = true, today = today))
        assertFalse(HeatingDay.outOfSeason(true, running = false, lastRunDay = null, monthKnown = false, today = today))
        assertFalse(HeatingDay.outOfSeason(true, running = true, lastRunDay = null, monthKnown = true, today = today))
    }

    @Test
    fun `outdoor range uses today's points only`() {
        val doc = day(-20 * min, listOf(0f, 0f, 0f, 0f), outdoor = listOf(-5f, -4f, 2f, 7.5f))
        assertEquals(2.0 to 7.5, HeatingDay.outdoorRange(doc, dayStartMs = 0))
    }
}
