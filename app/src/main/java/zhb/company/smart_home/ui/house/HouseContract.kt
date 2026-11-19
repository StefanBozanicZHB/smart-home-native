package zhb.company.smart_home.ui.house

import zhb.company.smart_home.domain.model.AcFanSpeed
import zhb.company.smart_home.domain.model.AcMode

data class HouseState(
    val groundOnline: Boolean = false,
    val groundTemperature: String = "--",
    val groundHumidity: String = "--",
    val upperOnline: Boolean = false,
    val upperTemperature: String = "--",
    val upperHumidity: String = "--",
    val co2: String = "--",
    val acOn: Boolean = false,
    val acMode: AcMode = AcMode.COOL,
    val acFanSpeed: AcFanSpeed = AcFanSpeed.AUTO,
    val acTemp: Int = 22,
    val plusMenuExpanded: Boolean = false,
) {
    val online: Boolean get() = groundOnline && upperOnline
}

sealed interface HouseIntent {
    data object PowerOn : HouseIntent
    data object PowerOff : HouseIntent
    data class SelectMode(val mode: AcMode) : HouseIntent
    data class SelectFanSpeed(val fanSpeed: AcFanSpeed) : HouseIntent
    data object IncreaseTemp : HouseIntent
    data object DecreaseTemp : HouseIntent
    data object TogglePlusMenu : HouseIntent
}
