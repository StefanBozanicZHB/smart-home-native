package zhb.company.smart_home.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingsTest {

    @Test
    fun `temperatures get one decimal`() {
        assertEquals("21.5", Readings.temperature("21.48"))
        assertEquals("3.5", Readings.temperature("3.50"))
        assertEquals("-2.0", Readings.temperature("-2"))
        assertEquals("34.3", Readings.temperature(34.25))
    }

    @Test
    fun `invalid temperatures show a placeholder`() {
        assertEquals(NO_VALUE, Readings.temperature("nan"))
        assertEquals(NO_VALUE, Readings.temperature("--"))
        assertEquals(NO_VALUE, Readings.temperature(null as String?))
        assertEquals(NO_VALUE, Readings.temperature(Double.NaN))
    }

    @Test
    fun `percentages are whole numbers`() {
        assertEquals("45", Readings.percent("45.2"))
        assertEquals("81", Readings.percent("80.6"))
        assertEquals(NO_VALUE, Readings.percent("--"))
    }

    @Test
    fun `update time shows the date only when it is not today`() {
        val zone = java.time.ZoneId.of("Europe/Belgrade")
        val at = java.time.ZonedDateTime.of(2026, 11, 23, 14, 32, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("14:32", Readings.updatedAt(at, java.time.LocalDate.of(2026, 11, 23), zone))
        assertEquals("23. 11. 14:32", Readings.updatedAt(at, java.time.LocalDate.of(2026, 11, 24), zone))
    }
}
