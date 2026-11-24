package zhb.company.smart_home.ui.home

/** Blocks of the home screen, in display order. Which ones appear depends on device state. */
enum class HomeSection {
    /** Large heating card: room temperature vs target per zone, pump. */
    HEATING_HERO,

    /** Large yard card: outdoor reading, sprinklers, (later) soil moisture. */
    GARDEN_HERO,

    /** Pool and A/C tiles with their temperature, while they run. */
    SUMMER_TILES,
    ROOM_TEMPS,
    DEVICES,

    /** One line "Grejanje · isključeno" while the main switch is off. */
    HEATING_ROW,

    /** One line for the sprinklers while nothing outdoor is active. */
    SPRINKLERS_ROW,
    GOODNIGHT,
}

/** Device state the home layout depends on. */
data class HomeSignals(
    val heatingMainSwitch: Boolean,
    val sprinklerAuto: Boolean,
    val sprinklerRunning: Boolean,
    /** The pool pump or the pool heating is on. */
    val poolPumpOn: Boolean,
)

/**
 * Chooses what is large on the home screen from device state, not from the calendar:
 * - heating main switch on → large heating card, otherwise a single line;
 * - sprinklers on auto or running, or the pool pump on → large yard card (summer), otherwise a line;
 * - both active (spring/autumn) → both large, heating first.
 * Alerts are not part of this list: they always come first.
 */
object HomeLayout {
    fun sections(s: HomeSignals): List<HomeSection> {
        val outdoorSeason = s.sprinklerAuto || s.sprinklerRunning || s.poolPumpOn
        return buildList {
            if (s.heatingMainSwitch) add(HomeSection.HEATING_HERO)
            if (outdoorSeason) add(HomeSection.GARDEN_HERO)
            if (s.poolPumpOn) add(HomeSection.SUMMER_TILES)
            add(HomeSection.ROOM_TEMPS)
            add(HomeSection.DEVICES)
            if (!s.heatingMainSwitch) add(HomeSection.HEATING_ROW)
            if (!outdoorSeason) add(HomeSection.SPRINKLERS_ROW)
            add(HomeSection.GOODNIGHT)
        }
    }
}
