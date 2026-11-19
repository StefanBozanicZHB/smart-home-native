package zhb.company.smart_home.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import zhb.company.smart_home.domain.model.AcFanSpeed
import zhb.company.smart_home.domain.model.AcMode

class AcCommandBuilderTest {

    /** Reference table from the Flutter app (Parameters.sendCommandIcLivingRoomUp). */
    private val expectedTempCodes = mapOf(
        17 to '0', 18 to '1', 19 to '3', 20 to '2', 21 to '6', 22 to '7', 23 to '5',
        24 to '4', 25 to 'C', 26 to 'D', 27 to '9', 28 to '8', 29 to 'A', 30 to 'B',
    )

    @Test
    fun `every temperature maps to the AC protocol code`() {
        expectedTempCodes.forEach { (temp, code) ->
            val command = AcCommandBuilder.buildCommand(AcFanSpeed.AUTO, temp, AcMode.COOL)
            assertEquals("temp $temp", "B25F${code}0", command)
        }
    }

    @Test
    fun `fan and mode codes are placed around the temperature code`() {
        assertEquals("B21F7C", AcCommandBuilder.buildCommand(AcFanSpeed.MAX, 22, AcMode.HEAT))
        assertEquals("B24F48", AcCommandBuilder.buildCommand(AcFanSpeed.MIN, 24, AcMode.AUTO))
    }

    @Test
    fun `temperature outside 17-30 is clamped`() {
        assertEquals(
            AcCommandBuilder.buildCommand(AcFanSpeed.AUTO, 17, AcMode.COOL),
            AcCommandBuilder.buildCommand(AcFanSpeed.AUTO, 10, AcMode.COOL),
        )
        assertEquals(
            AcCommandBuilder.buildCommand(AcFanSpeed.AUTO, 30, AcMode.COOL),
            AcCommandBuilder.buildCommand(AcFanSpeed.AUTO, 35, AcMode.COOL),
        )
    }
}
