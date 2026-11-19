package zhb.company.smart_home.ui.garden

data class GardenState(
    val online: Boolean = false,
    val temperature: String = "--",
    val humidity: String = "--",
    val ledStripOn: Boolean = false,
    val bulbOn: Boolean = false,
    val fountainPumpOn: Boolean = false,
    val fountainLightOn: Boolean = false,
    val poolPumpOn: Boolean = false,
    val poolHeatingOn: Boolean = false,
    val poolTemperature: String = "--",
    val sprinklerActiveIndex: Int = -1,
    val sprinklerAuto: Boolean = false,
    val sprinklers: List<Boolean> = List(6) { false },
    val showSprinklerDialog: Boolean = false,
    val showLocationDialog: Boolean = false,
    val plusMenuExpanded: Boolean = false,
)

sealed interface GardenIntent {
    data class ToggleLedStrip(val on: Boolean) : GardenIntent
    data class ToggleBulb(val on: Boolean) : GardenIntent
    data class ToggleFountainPump(val on: Boolean) : GardenIntent
    data class ToggleFountainLight(val on: Boolean) : GardenIntent
    data class TogglePoolPump(val on: Boolean) : GardenIntent
    data class TogglePoolHeating(val on: Boolean) : GardenIntent
    data class ToggleSprinkler(val index: Int, val on: Boolean) : GardenIntent
    data object OpenSprinklerDialog : GardenIntent
    data object DismissSprinklerDialog : GardenIntent
    data object OpenLocationDialog : GardenIntent
    data object DismissLocationDialog : GardenIntent
    data object TogglePlusMenu : GardenIntent
}
