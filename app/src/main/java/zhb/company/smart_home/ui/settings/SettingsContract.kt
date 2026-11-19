package zhb.company.smart_home.ui.settings

data class SettingsState(
    val darkTheme: Boolean = false,
    val sprinklerAuto: Boolean = false,
    val sprinklerStartTime: String = "06:00",
    val sprinklerOnMinutes: Int = 5,
    val sprinklerOffSeconds: Int = 30,
    val sprinklerSets: Int = 1,
)

sealed interface SettingsIntent {
    data class ToggleDarkTheme(val enabled: Boolean) : SettingsIntent
    data class ToggleSprinklerAuto(val enabled: Boolean) : SettingsIntent
    data class SetStartTime(val time: String) : SettingsIntent
    data class SetOnMinutes(val minutes: Int) : SettingsIntent
    data class SetOffSeconds(val seconds: Int) : SettingsIntent
    data class SetSets(val sets: Int) : SettingsIntent
    data object Save : SettingsIntent
}
