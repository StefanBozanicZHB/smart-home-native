package zhb.company.smart_home.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationsTest {
    @Test
    fun `compact durations`() {
        assertEquals("0 s", Durations.compact(-5))
        assertEquals("45 s", Durations.compact(45))
        assertEquals("12 min", Durations.compact(750))
        assertEquals("1 h", Durations.compact(3_600))
        assertEquals("3 h 20 min", Durations.compact(12_000))
        assertEquals("2 d", Durations.compact(172_800))
        assertEquals("5 d 5 h", Durations.compact(450_000))
    }

    @Test
    fun `kilobytes with one decimal`() {
        assertEquals("26.9 KB", Durations.kilobytes(27_560))
    }
}
