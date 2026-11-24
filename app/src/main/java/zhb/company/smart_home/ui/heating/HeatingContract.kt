package zhb.company.smart_home.ui.heating

import zhb.company.smart_home.domain.heating.DayType
import zhb.company.smart_home.domain.heating.HeatPumpStatus
import zhb.company.smart_home.domain.heating.Hysteresis
import zhb.company.smart_home.domain.heating.Node5Limits
import zhb.company.smart_home.domain.heating.ScheduleProblem
import zhb.company.smart_home.domain.heating.ScheduleSegment

data class HeatingState(
    /** MQTT node id (shown in the "waiting for node" message). */
    val nodeId: String = "node5",
    val connected: Boolean = false,
    /** Node 5 Last Will status: true online, false offline, null not heard yet. */
    val online: Boolean? = null,
    /** True until the node's retained config has arrived; editing is disabled meanwhile. */
    val loading: Boolean = true,

    val outdoorTemp: String = "--",
    /** Last update of [outdoorTemp] when it stopped updating, else null. */
    val outdoorStaleSince: Long? = null,
    val mainSwitch: Boolean = false,
    val pump: Boolean = false,
    val demand: Boolean = false,
    val flowTemp: String = "--",
    /** Node local time "HH:MM"; null until the node has NTP time. */
    val nodeTime: String? = null,
    val nodeIsWeekend: Boolean? = null,
    val safeMode: SafeModeUi? = null,
    val lastPumpChange: PumpChangeUi? = null,
    /** Running / waiting / idle / off / safe mode, for the status card at the top. */
    val pumpStatus: HeatPumpStatus = HeatPumpStatus.Unknown,
    /** Heat pump pipe temperatures; only the flow pipe is published today (node5-2.0.0). */
    val pipes: PipeTemps = PipeTemps(),
    /** Next scheduled change today for the selected zone, from the program the node runs now. */
    val nextChange: NextChangeUi? = null,
    val zones: List<ZoneUi> = emptyList(),

    val limits: Node5Limits = Node5Limits(),
    val selectedZone: String? = null,
    val selectedDay: DayType = DayType.WORKDAY,
    /** Segments shown in the editor: the draft if there is one, else the node's program. */
    val editorSegments: List<ScheduleSegment> = emptyList(),
    /** The node has no program for the selected day yet (it is in safe mode `no_schedule`). */
    val dayNotSet: Boolean = false,
    val scheduleDirty: Boolean = false,
    /** Some zone/day (not only the one shown) has edits that were not saved to the node. */
    val hasUnsavedDrafts: Boolean = false,
    val showDiscardDraftsConfirm: Boolean = false,
    val scheduleProblem: ScheduleProblem? = null,
    /** The zone changed on the node (another phone, a command) while a draft was open. */
    val remoteChangedWhileEditing: Boolean = false,

    val hysteresis: Hysteresis? = null,
    val minOnSeconds: Int? = null,
    val minOffSeconds: Int? = null,
    val configRev: Long? = null,

    /**
     * The command whose answer is late (over a second); only then does the screen say so.
     * Commands are sent one at a time; a second one while the first waits is ignored.
     */
    val slowCommand: HeatingCommand? = null,
    val showSettingsDialog: Boolean = false,
    val showMainSwitchOffConfirm: Boolean = false,
    val message: HeatingMessage? = null,
)

/** Node 5 commands the heating screen sends. */
enum class HeatingCommand { MAIN_SWITCH, SCHEDULE, HYSTERESIS }

data class ZoneUi(
    val id: String,
    val roomTemp: String,
    val target: String?,
    /** Raw values for the room-vs-target indicator. */
    val roomTempValue: Double? = null,
    val targetTenths: Int? = null,
    val demand: Boolean,
    /** `no_schedule`, `sensor_offline`, `room_temp_stale` or null. */
    val fault: String?,
)

/** Four pipes of the water-to-water heat pump; null = no sensor (yet). Formatted with one decimal. */
data class PipeTemps(
    val wellIn: String? = null,
    val wellOut: String? = null,
    val supply: String? = null,
    val ret: String? = null,
)

data class NextChangeUi(val time: String, val temp: String)

data class SafeModeUi(val reason: String?, val zone: String?)

data class PumpChangeUi(
    val on: Boolean,
    /** `thermostat`, `schedule`, `app`, `safe_mode`, `boot`. */
    val source: String,
    /** Minutes since the change, if the node uptime allows computing it. */
    val minutesAgo: Long?,
)

/** One-shot feedback for a command, shown in a snackbar. */
sealed interface HeatingMessage {
    data object Saved : HeatingMessage
    data class Rejected(val error: String) : HeatingMessage
    data object Timeout : HeatingMessage
    data object NotConnected : HeatingMessage
}

sealed interface HeatingIntent {
    data class ToggleMainSwitch(val on: Boolean) : HeatingIntent
    data object ConfirmMainSwitchOff : HeatingIntent
    data object DismissMainSwitchOff : HeatingIntent

    data class SelectZone(val zone: String) : HeatingIntent
    data class SelectDay(val day: DayType) : HeatingIntent
    data class SetSegmentStart(val index: Int, val minute: Int) : HeatingIntent
    data class ChangeSegmentTemp(val index: Int, val deltaTenths: Int) : HeatingIntent
    data object AddSegment : HeatingIntent
    data class RemoveSegment(val index: Int) : HeatingIntent
    data object SaveSchedule : HeatingIntent
    data object DiscardSchedule : HeatingIntent
    /** Back pressed with unsaved drafts: ask before leaving. */
    data object RequestLeave : HeatingIntent
    data object KeepEditing : HeatingIntent
    data object DiscardAllDrafts : HeatingIntent

    data object OpenSettingsDialog : HeatingIntent
    data object DismissSettingsDialog : HeatingIntent
    data class SaveHysteresis(val hysteresis: Hysteresis) : HeatingIntent
    data object Refresh : HeatingIntent

    data object MessageShown : HeatingIntent
}
