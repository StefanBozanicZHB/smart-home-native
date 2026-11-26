package zhb.company.smart_home.domain.wall

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SunTimesTest {
    private val belgrade = ZoneId.of("Europe/Belgrade")

    @Test
    fun `late November in Belgrade matches published times within a few minutes`() {
        // Published for Belgrade on 2026-11-26: sunrise about 06:50, sunset about 16:03.
        val sun = assertNotNull(SunTimes.of(LocalDate.of(2026, 11, 26), belgrade))
        val rise = sun.sunrise.toLocalTime().toSecondOfDay() / 60
        val set = sun.sunset.toLocalTime().toSecondOfDay() / 60
        assertEquals(6 * 60 + 50.0, rise.toDouble(), 5.0)
        assertEquals(16 * 60 + 3.0, set.toDouble(), 5.0)
    }

    @Test
    fun `summer time is applied`() {
        // Belgrade, 2026-06-21: sunrise about 04:51, sunset about 20:28 (CEST).
        val sun = assertNotNull(SunTimes.of(LocalDate.of(2026, 6, 21), belgrade))
        assertEquals(4 * 60 + 51.0, (sun.sunrise.toLocalTime().toSecondOfDay() / 60).toDouble(), 5.0)
        assertEquals(20 * 60 + 28.0, (sun.sunset.toLocalTime().toSecondOfDay() / 60).toDouble(), 5.0)
    }

    @Test
    fun `day length is sunset minus sunrise`() {
        val sun = assertNotNull(SunTimes.of(LocalDate.of(2026, 11, 26), belgrade))
        assertEquals(sun.sunset.toEpochSecond() - sun.sunrise.toEpochSecond(), sun.dayLength.seconds)
    }

    @Test
    fun `polar night has no sun times`() {
        assertNull(SunTimes.of(LocalDate.of(2026, 12, 21), belgrade, lat = 80.0, lon = 15.0))
    }

    private fun <T> assertNotNull(value: T?): T {
        assertNotNull(null, value)
        return value!!
    }
}
