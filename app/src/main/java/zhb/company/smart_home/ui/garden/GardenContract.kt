package zhb.company.smart_home.ui.garden

import zhb.company.smart_home.domain.garden.ActiveRun
import zhb.company.smart_home.domain.garden.NextStart
import zhb.company.smart_home.domain.garden.RunEvent
import zhb.company.smart_home.ui.format.NO_VALUE

/** Sprinklers on Node 1: plan summary, the active run and the last run event. */
data class IrrigationUi(
    val auto: Boolean = false,
    /** Minutes per zone 1..6 from the plan. */
    val zoneMinutes: List<Int> = List(6) { 0 },
    /** "YYYY-MM-DD" while a pause (rain) is set and not over. */
    val pausedUntil: String? = null,
    val next: NextStart? = null,
    val run: ActiveRun? = null,
    val lastRun: RunEvent? = null,
    /** False until the node has its clock (NTP); nothing is scheduled without it. */
    val clockKnown: Boolean = true,
    /** Problem with the stored plan on the node. */
    val storage: String? = null,
)

data class GardenState(
    /** Yard sensor and lights (node2). */
    val yardOnline: Boolean = false,
    /** Fountain and sprinklers (node1). */
    val node1Online: Boolean = false,
    /** Node 1 has published its plan and state. */
    val node1Ready: Boolean = false,
    /** Pool (node6). */
    val poolOnline: Boolean = false,
    val temperature: String = NO_VALUE,
    val humidity: String = NO_VALUE,
    val ledStripOn: Boolean = false,
    val bulbOn: Boolean = false,
    val fountainPumpOn: Boolean = false,
    val fountainPumpLeftSeconds: Int? = null,
    val fountainLightOn: Boolean = false,
    val fountainLightLeftSeconds: Int? = null,
    val poolPumpOn: Boolean = false,
    val poolHeatingOn: Boolean = false,
    val poolTemperature: String = NO_VALUE,
    val irrigation: IrrigationUi = IrrigationUi(),
    /** A Node 1 command is waiting for its answer. */
    val busy: Boolean = false,
    val dialog: GardenDialog? = null,
    val message: GardenMessage? = null,
    val showLocationDialog: Boolean = false,
)

sealed interface GardenDialog {
    /** A manual run would replace the run in progress. */
    data class ConfirmReplace(val zones: List<Int>?, val minutes: Int?) : GardenDialog
    /** The zone has 0 minutes in the plan: ask how long. */
    data class AskMinutes(val zone: Int) : GardenDialog
}

sealed interface GardenMessage {
    data object Started : GardenMessage
    data object Stopped : GardenMessage
    data object NoZones : GardenMessage
    data object NotSaved : GardenMessage
    data class Rejected(val error: String) : GardenMessage
    data object Timeout : GardenMessage
    data object NotConnected : GardenMessage
}

sealed interface GardenIntent {
    data class ToggleLedStrip(val on: Boolean) : GardenIntent
    data class ToggleBulb(val on: Boolean) : GardenIntent
    /** [minutes] switches it off by itself after that time. */
    data class SetFountainPump(val on: Boolean, val minutes: Int? = null) : GardenIntent
    data class SetFountainLight(val on: Boolean, val minutes: Int? = null) : GardenIntent
    data class TogglePoolPump(val on: Boolean) : GardenIntent
    data class TogglePoolHeating(val on: Boolean) : GardenIntent
    /** Water one zone for its planned minutes (asks for minutes if it has none). */
    data class StartZone(val zone: Int) : GardenIntent
    data class StartZoneFor(val zone: Int, val minutes: Int) : GardenIntent
    /** Water every zone with minutes, in order. */
    data object StartAll : GardenIntent
    data object ConfirmReplace : GardenIntent
    data object DismissDialog : GardenIntent
    data object StopRun : GardenIntent
    data object MessageShown : GardenIntent
    data object OpenLocationDialog : GardenIntent
    data object DismissLocationDialog : GardenIntent
}
