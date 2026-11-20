package zhb.company.smart_home.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zhb.company.smart_home.ui.home.HomeSection.DEVICES
import zhb.company.smart_home.ui.home.HomeSection.GARDEN_HERO
import zhb.company.smart_home.ui.home.HomeSection.GOODNIGHT
import zhb.company.smart_home.ui.home.HomeSection.HEATING_HERO
import zhb.company.smart_home.ui.home.HomeSection.HEATING_ROW
import zhb.company.smart_home.ui.home.HomeSection.ROOM_TEMPS
import zhb.company.smart_home.ui.home.HomeSection.SPRINKLERS_ROW
import zhb.company.smart_home.ui.home.HomeSection.SUMMER_TILES

class HomeLayoutTest {

    private val idle = HomeSignals(
        heatingMainSwitch = false, sprinklerAuto = false, sprinklerRunning = false,
        poolPumpOn = false, acOn = false,
    )

    @Test
    fun `winter - heating is large, sprinklers are a line`() {
        assertEquals(
            listOf(HEATING_HERO, ROOM_TEMPS, DEVICES, SPRINKLERS_ROW, GOODNIGHT),
            HomeLayout.sections(idle.copy(heatingMainSwitch = true)),
        )
    }

    @Test
    fun `summer - yard is large, heating is a line`() {
        assertEquals(
            listOf(GARDEN_HERO, SUMMER_TILES, ROOM_TEMPS, DEVICES, HEATING_ROW, GOODNIGHT),
            HomeLayout.sections(idle.copy(sprinklerAuto = true, poolPumpOn = true)),
        )
    }

    @Test
    fun `sprinklers on auto alone are summer, without pool or AC tiles`() {
        val sections = HomeLayout.sections(idle.copy(sprinklerAuto = true))
        assertTrue(GARDEN_HERO in sections)
        assertFalse(SUMMER_TILES in sections)
    }

    @Test
    fun `AC on in a heated house shows both, heating first`() {
        val sections = HomeLayout.sections(idle.copy(heatingMainSwitch = true, acOn = true))
        assertEquals(listOf(HEATING_HERO, GARDEN_HERO, SUMMER_TILES), sections.take(3))
        assertFalse(HEATING_ROW in sections)
        assertFalse(SPRINKLERS_ROW in sections)
    }

    @Test
    fun `nothing active - no large card, both lines`() {
        assertEquals(
            listOf(ROOM_TEMPS, DEVICES, HEATING_ROW, SPRINKLERS_ROW, GOODNIGHT),
            HomeLayout.sections(idle),
        )
    }
}
