package zhb.company.smart_home.domain.heating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleRulesTest {

    private val limits = Node5Limits()
    private fun seg(hm: String, tenths: Int) = ScheduleSegment(TimeOfDay.parse(hm)!!, tenths)

    @Test
    fun `time of day parse and format`() {
        assertEquals(390, TimeOfDay.parse("06:30"))
        assertNull(TimeOfDay.parse("6:30"))
        assertNull(TimeOfDay.parse("24:00"))
        assertNull(TimeOfDay.parse("12:60"))
        assertEquals("06:30", TimeOfDay.format(390))
        assertEquals("24:00", TimeOfDay.format(MINUTES_PER_DAY))
    }

    @Test
    fun `tenths formatting`() {
        assertEquals("21.5", formatTenths(215))
        assertEquals("0.0", formatTenths(0))
        assertEquals("5.0", formatTenths(50))
        assertEquals("-0.5", formatTenths(-5))
    }

    @Test
    fun `valid day passes`() {
        assertNull(ScheduleRules.validateDay(listOf(seg("00:00", 195), seg("06:00", 215), seg("22:30", 195)), limits))
    }

    @Test
    fun `day rules match the firmware`() {
        assertEquals(ScheduleProblem.SegmentCount(8), ScheduleRules.validateDay(emptyList(), limits))
        assertEquals(
            ScheduleProblem.SegmentCount(8),
            ScheduleRules.validateDay((0 until 9).map { ScheduleSegment(it * 60, 200) }, limits),
        )
        assertEquals(ScheduleProblem.FirstNotMidnight, ScheduleRules.validateDay(listOf(seg("01:00", 200)), limits))
        assertEquals(
            ScheduleProblem.StartsNotRising(2),
            ScheduleRules.validateDay(listOf(seg("00:00", 200), seg("08:00", 200), seg("08:00", 210)), limits),
        )
        assertEquals(
            ScheduleProblem.TempOutOfRange(1, 50, 300),
            ScheduleRules.validateDay(listOf(seg("00:00", 200), seg("08:00", 301)), limits),
        )
        assertNull(ScheduleRules.validateDay(listOf(seg("00:00", 50), seg("23:59", 300)), limits))
    }

    @Test
    fun `hysteresis must be within 0 and step max`() {
        assertNull(ScheduleRules.validateHysteresis(Hysteresis(0, 20), limits))
        assertEquals(ScheduleProblem.HysteresisOutOfRange(20), ScheduleRules.validateHysteresis(Hysteresis(21, 1), limits))
        assertEquals(ScheduleProblem.HysteresisOutOfRange(20), ScheduleRules.validateHysteresis(Hysteresis(1, -1), limits))
    }

    @Test
    fun `adding a segment splits the longest one on a half hour`() {
        val day = listOf(seg("00:00", 195), seg("06:00", 215), seg("22:00", 195))
        assertEquals(
            listOf(seg("00:00", 195), seg("06:00", 215), seg("14:00", 215), seg("22:00", 195)),
            ScheduleRules.addSegment(day, limits),
        )
        assertEquals(ScheduleRules.defaultDay(limits), ScheduleRules.addSegment(emptyList(), limits))
    }

    @Test
    fun `adding stops at the segment limit`() {
        val full = (0 until 8).map { ScheduleSegment(it * 60, 200) }
        assertEquals(full, ScheduleRules.addSegment(full, limits))
    }

    @Test
    fun `removing keeps the first segment at midnight`() {
        val day = listOf(seg("00:00", 195), seg("06:00", 215), seg("22:00", 195))
        assertEquals(listOf(seg("00:00", 215), seg("22:00", 195)), ScheduleRules.removeSegment(day, 0))
        assertEquals(listOf(seg("00:00", 195), seg("22:00", 195)), ScheduleRules.removeSegment(day, 1))
        assertEquals(listOf(seg("00:00", 195)), ScheduleRules.removeSegment(listOf(seg("00:00", 195)), 0))
    }

    @Test
    fun `first start cannot move and temperatures are clamped`() {
        val day = listOf(seg("00:00", 195), seg("06:00", 215))
        assertEquals(day, ScheduleRules.setStart(day, 0, 60))
        assertEquals(listOf(seg("00:00", 195), seg("07:15", 215)), ScheduleRules.setStart(day, 1, 435))
        assertEquals(300, ScheduleRules.setTemp(day, 1, 999, limits)[1].tempTenths)
        assertEquals(50, ScheduleRules.setTemp(day, 0, 10, limits)[0].tempTenths)
    }
}
