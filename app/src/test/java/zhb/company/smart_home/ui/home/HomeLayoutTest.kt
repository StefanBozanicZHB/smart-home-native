package zhb.company.smart_home.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import zhb.company.smart_home.ui.home.HomeSection.DEVICES
import zhb.company.smart_home.ui.home.HomeSection.GARDEN_HERO
import zhb.company.smart_home.ui.home.HomeSection.GOODNIGHT
import zhb.company.smart_home.ui.home.HomeSection.HEATING_HERO
import zhb.company.smart_home.ui.home.HomeSection.HEATING_ROW
import zhb.company.smart_home.ui.home.HomeSection.ROOM_TEMPS
import zhb.company.smart_home.ui.home.HomeSection.SPRINKLERS_ROW

class HomeLayoutTest {

    private val idle = HomeSignals(heatingMainSwitch = false, sprinklerAuto = false, sprinklerRunning = false)

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
            listOf(GARDEN_HERO, ROOM_TEMPS, DEVICES, HEATING_ROW, GOODNIGHT),
            HomeLayout.sections(idle.copy(sprinklerAuto = true)),
        )
    }

    @Test
    fun `spring and autumn - both large, heating first`() {
        assertEquals(
            listOf(HEATING_HERO, GARDEN_HERO, ROOM_TEMPS, DEVICES, GOODNIGHT),
            HomeLayout.sections(idle.copy(heatingMainSwitch = true, sprinklerRunning = true)),
        )
    }

    @Test
    fun `nothing active - no large card, both lines`() {
        assertEquals(
            listOf(ROOM_TEMPS, DEVICES, HEATING_ROW, SPRINKLERS_ROW, GOODNIGHT),
            HomeLayout.sections(idle),
        )
    }
}
