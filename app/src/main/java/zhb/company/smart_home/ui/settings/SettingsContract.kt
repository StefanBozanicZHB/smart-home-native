package zhb.company.smart_home.ui.settings

data class SettingsState(
    val darkTheme: Boolean = false,
    /** Push notifications from the house system (FCM topic subscription). */
    val notifications: Boolean = true,
    val sprinklerAuto: Boolean = false,
    val sprinklerStartTime: String = "06:00",
    val sprinklerOnMinutes: Int = 5,
    val sprinklerOffSeconds: Int = 30,
    val sprinklerSets: Int = 1,
)

sealed interface SettingsIntent {
    data class ToggleDarkTheme(val enabled: Boolean) : SettingsIntent
    data class ToggleNotifications(val enabled: Boolean) : SettingsIntent
    /** Applied right away; [start]/[end] are "HH:MM" and already validated. */
    data class SetQuietHours(val enabled: Boolean, val start: String, val end: String) : SettingsIntent
    data class ToggleSprinklerAuto(val enabled: Boolean) : SettingsIntent
    data class SetStartTime(val time: String) : SettingsIntent
    data class SetOnMinutes(val minutes: Int) : SettingsIntent
    data class SetOffSeconds(val seconds: Int) : SettingsIntent
    data class SetSets(val sets: Int) : SettingsIntent
    data object Save : SettingsIntent
}
