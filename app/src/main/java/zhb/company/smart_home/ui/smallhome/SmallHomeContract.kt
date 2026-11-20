package zhb.company.smart_home.ui.smallhome

data class SmallHomeState(
    val online: Boolean = false,
    val doorOn: Boolean = false,
    val bulb1: Boolean = false,
    val bulb2: Boolean = false,
    val bulb3: Boolean = false,
    val fingerLog: String = "",
    val addIdInput: String = "",
    val deleteIdInput: String = "",
)

sealed interface SmallHomeIntent {
    data class ToggleDoor(val on: Boolean) : SmallHomeIntent
    data class ToggleBulb1(val on: Boolean) : SmallHomeIntent
    data class ToggleBulb2(val on: Boolean) : SmallHomeIntent
    data class ToggleBulb3(val on: Boolean) : SmallHomeIntent
    data class AddIdInputChanged(val value: String) : SmallHomeIntent
    data class DeleteIdInputChanged(val value: String) : SmallHomeIntent
    data object AddFingerprintId : SmallHomeIntent
    data object DeleteFingerprintId : SmallHomeIntent
}
