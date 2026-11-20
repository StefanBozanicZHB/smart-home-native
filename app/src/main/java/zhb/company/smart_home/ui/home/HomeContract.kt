package zhb.company.smart_home.ui.home

import zhb.company.smart_home.domain.model.AcMode
import zhb.company.smart_home.ui.format.NO_VALUE

/** Something wrong right now; shown at the top of the home screen until it is resolved. */
sealed interface HomeAlert {
    data object NotConnected : HomeAlert
    data class NodeOffline(val label: String, val lastWill: Boolean) : HomeAlert

    /** Heating is in safe mode (pump off); [reason] is the firmware reason code. */
    data class HeatingSafeMode(val reason: String?) : HomeAlert
}

data class ZoneSummary(
    val id: String,
    val roomTemp: String,
    val target: String?,
    val demand: Boolean,
)

data class HeatingSummary(
    val online: Boolean = false,
    val mainSwitch: Boolean = false,
    val pump: Boolean = false,
    val flowTemp: String = NO_VALUE,
    val zones: List<ZoneSummary> = emptyList(),
)

data class HomeState(
    val connected: Boolean = false,
    val alerts: List<HomeAlert> = emptyList(),
    val sections: List<HomeSection> = HomeLayout.sections(
        HomeSignals(false, false, false, false, false, false),
    ),
    val heating: HeatingSummary = HeatingSummary(),

    val outdoorTemp: String = NO_VALUE,
    val outdoorHumidity: String = NO_VALUE,
    val groundTemp: String = NO_VALUE,
    val groundHumidity: String = NO_VALUE,
    val upperTemp: String = NO_VALUE,
    val upperHumidity: String = NO_VALUE,
    val poolTemp: String = NO_VALUE,

    val sprinklerAuto: Boolean = false,
    val sprinklerStartTime: String = "--:--",
    /** Sprinkler zone running now (1-6), null if none. */
    val sprinklerRunningZone: Int? = null,

    val ledStripOn: Boolean = false,
    val fountainOn: Boolean = false,
    val poolPumpOn: Boolean = false,
    val poolHeaterOn: Boolean = false,
    val acOn: Boolean = false,
    val acTemp: Int = 22,
    val acMode: AcMode = AcMode.COOL,

    /** Online state of the nodes behind the home tiles; an offline node's tile is disabled. */
    val yardNodeOnline: Boolean = false,
    val fountainNodeOnline: Boolean = false,
    val poolNodeOnline: Boolean = false,
    val doorNodeOnline: Boolean = false,

    val devicesOnList: List<String> = emptyList(),
    val showDevicesOnDialog: Boolean = false,
    val showUnlockConfirm: Boolean = false,
)

sealed interface HomeIntent {
    data object RequestUnlockFrontDoor : HomeIntent
    data object ConfirmUnlockFrontDoor : HomeIntent
    data object DismissUnlockFrontDoor : HomeIntent
    data class ToggleLedStrip(val on: Boolean) : HomeIntent
    data class ToggleFountain(val on: Boolean) : HomeIntent
    data class TogglePoolPump(val on: Boolean) : HomeIntent
    data class TogglePoolHeater(val on: Boolean) : HomeIntent
    data object TurnHeatingOn : HomeIntent
    data object ShowDevicesOnDialog : HomeIntent
    data object DismissDevicesOnDialog : HomeIntent
    data object Goodnight : HomeIntent
}

sealed interface HomeEffect {
    data class ShowToast(@androidx.annotation.StringRes val messageRes: Int) : HomeEffect
}
