package zhb.company.smart_home.ui.home

/** A small colored on/off status line shown under a room card's reading, e.g. "Radi" / "Uključeno". */
data class RoomStatusBadge(val label: String, val active: Boolean)

data class RoomCardState(
    val title: String,
    val temperature: String,
    val humidity: String,
    val online: Boolean,
    // Extra on/off status lines (e.g. heating's relay/main-switch state). Empty for plain rooms.
    val statusBadges: List<RoomStatusBadge> = emptyList(),
)

data class HomeState(
    val darkTheme: Boolean = false,
    val connected: Boolean = false,
    val mainDoorOnline: Boolean = false,
    val mainDoorLocked: Boolean = true,
    val tempUp: String = "--",
    val tempDown: String = "--",
    val tempOutdoor: String = "--",
    val sprinklerAuto: Boolean = false,
    val sprinklerStartTime: String = "--:--",
    val devicesOnCount: Int = 0,
    val devicesOnList: List<String> = emptyList(),
    val ledStripOn: Boolean = false,
    val sprinklersOn: Boolean = false,
    val fountainOn: Boolean = false,
    val poolPumpOn: Boolean = false,
    val poolHeaterOn: Boolean = false,
    val gateOn: Boolean = false,
    val garden: RoomCardState = RoomCardState("", "--", "--", false),
    val firstFloor: RoomCardState = RoomCardState("", "--", "--", false),
    val secondFloor: RoomCardState = RoomCardState("", "--", "--", false),
    val pool: RoomCardState = RoomCardState("", "--", "--", false),
    val shed: RoomCardState = RoomCardState("", "--", "--", false),
    val heating: RoomCardState = RoomCardState("", "--", "--", false),
    val showDevicesOnDialog: Boolean = false,
    val plusMenuExpanded: Boolean = false,
)

sealed interface HomeIntent {
    data object UnlockFrontDoor : HomeIntent
    data class ToggleLedStrip(val on: Boolean) : HomeIntent
    data class ToggleSprinklers(val on: Boolean) : HomeIntent
    data class ToggleFountain(val on: Boolean) : HomeIntent
    data class TogglePoolPump(val on: Boolean) : HomeIntent
    data class TogglePoolHeater(val on: Boolean) : HomeIntent
    data class ToggleGate(val on: Boolean) : HomeIntent
    data object ShowDevicesOnDialog : HomeIntent
    data object DismissDevicesOnDialog : HomeIntent
    data object Goodnight : HomeIntent
    data object TogglePlusMenu : HomeIntent
}

sealed interface HomeEffect {
    data class ShowToast(@androidx.annotation.StringRes val messageRes: Int) : HomeEffect
}
