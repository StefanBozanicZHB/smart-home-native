package zhb.company.smart_home.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SprinklerConfigCodecTest {

    @Test
    fun `decode reads all fields and ignores unknown keys`() {
        val dto = SprinklerConfigCodec.decode("""{"start":"05:30","on":600,"off":30,"sets":2,"auto":1,"extra":true}""")
        assertEquals(SprinklerConfigDto(start = "05:30", on = 600, off = 30, sets = 2, auto = 1), dto)
    }

    @Test
    fun `decode returns null for malformed or incomplete payloads`() {
        assertNull(SprinklerConfigCodec.decode("not json"))
        assertNull(SprinklerConfigCodec.decode("""{"start":"05:30"}"""))
        assertNull(SprinklerConfigCodec.decode(""))
    }

    @Test
    fun `encode converts minutes to seconds and auto to 0 or 1`() {
        val json = SprinklerConfigCodec.encode(
            startTime = "21:00", onDurationMinutes = 5, offDurationSeconds = 15, sets = 3, auto = true,
        )
        assertEquals("""{"start":"21:00","on":300,"off":15,"sets":3,"auto":1}""", json)

        val manual = SprinklerConfigCodec.encode("21:00", 1, 0, 1, auto = false)
        assertEquals(0, SprinklerConfigCodec.decode(manual)?.auto)
    }
}
