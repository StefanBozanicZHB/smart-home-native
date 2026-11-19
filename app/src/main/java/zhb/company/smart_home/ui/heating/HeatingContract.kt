package zhb.company.smart_home.ui.heating

import zhb.company.smart_home.domain.model.HeatingSegment
import zhb.company.smart_home.domain.model.HeatingType

data class HeatingState(
    val online: Boolean = false,
    val tubeTemp: String = "--",
    val outdoorTemp: String = "--",
    val tempUp: String = "--",
    val humidityUp: String = "--",
    val tempDown: String = "--",
    val humidityDown: String = "--",
    val pelletsLevelPercent: Double = 0.0,
    val isTempRelay: Boolean = false,
    val isMainSwitch: Boolean = false,
    val heatingType: HeatingType = HeatingType.PELLETS,
    val workdaySegments: List<HeatingSegment> = emptyList(),
    val weekendSegments: List<HeatingSegment> = emptyList(),
    val upStep: Double = 0.0,
    val downStep: Double = 0.0,
    val alarmLimitPercent: Int = 0,
    val showSettingsDialog: Boolean = false,
    val plusMenuExpanded: Boolean = false,
)

sealed interface HeatingIntent {
    data class ToggleMainSwitch(val on: Boolean) : HeatingIntent
    data class SelectHeatingType(val type: HeatingType) : HeatingIntent
    data object OpenSettingsDialog : HeatingIntent
    data object DismissSettingsDialog : HeatingIntent
    data class SetUpperHysteresis(val value: Double) : HeatingIntent
    data class SetLowerHysteresis(val value: Double) : HeatingIntent
    data class SetAlarmLimit(val percent: Int) : HeatingIntent
    data object SetFullLevel : HeatingIntent
    data object SetEmptyLevel : HeatingIntent
    data class SetWorkdaySegments(val segments: List<HeatingSegment>) : HeatingIntent
    data object SaveWorkdaySchedule : HeatingIntent
    data class SetWeekendSegments(val segments: List<HeatingSegment>) : HeatingIntent
    data object SaveWeekendSchedule : HeatingIntent
    data object TogglePlusMenu : HeatingIntent
}
