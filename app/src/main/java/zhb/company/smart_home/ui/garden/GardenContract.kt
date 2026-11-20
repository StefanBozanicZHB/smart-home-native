package zhb.company.smart_home.ui.garden

import zhb.company.smart_home.ui.format.NO_VALUE

data class GardenState(
    /** Yard sensor and lights (node2). */
    val yardOnline: Boolean = false,
    /** Fountain and sprinkler valves (node1). */
    val fountainOnline: Boolean = false,
    /** Sprinkler timer (node0). */
    val sprinklerTimerOnline: Boolean = false,
    /** Pool (node6). */
    val poolOnline: Boolean = false,
    val temperature: String = NO_VALUE,
    val humidity: String = NO_VALUE,
    val ledStripOn: Boolean = false,
    val bulbOn: Boolean = false,
    val fountainPumpOn: Boolean = false,
    val fountainLightOn: Boolean = false,
    val poolPumpOn: Boolean = false,
    val poolHeatingOn: Boolean = false,
    val poolTemperature: String = NO_VALUE,
    /** Zone running now (1-6), -1 if none. */
    val sprinklerActiveIndex: Int = -1,
    val sprinklerAuto: Boolean = false,
    val sprinklerStartTime: String = "--:--",
    val sprinklerOnMinutes: Int = 0,
    val sprinklerSets: Int = 0,
    val sprinklers: List<Boolean> = List(6) { false },
    val showLocationDialog: Boolean = false,
)

sealed interface GardenIntent {
    data class ToggleLedStrip(val on: Boolean) : GardenIntent
    data class ToggleBulb(val on: Boolean) : GardenIntent
    data class ToggleFountainPump(val on: Boolean) : GardenIntent
    data class ToggleFountainLight(val on: Boolean) : GardenIntent
    data class TogglePoolPump(val on: Boolean) : GardenIntent
    data class TogglePoolHeating(val on: Boolean) : GardenIntent
    data class ToggleSprinkler(val index: Int, val on: Boolean) : GardenIntent
    data object OpenLocationDialog : GardenIntent
    data object DismissLocationDialog : GardenIntent
}
