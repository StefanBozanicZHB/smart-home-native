package zhb.company.smart_home.ui.settings

data class SettingsState(
    val darkTheme: Boolean = false,
    /** Push notifications from the house system (FCM topic subscription). */
    val notifications: Boolean = true,
)

sealed interface SettingsIntent {
    data class ToggleDarkTheme(val enabled: Boolean) : SettingsIntent
    data class ToggleNotifications(val enabled: Boolean) : SettingsIntent
    /** Applied right away; [start]/[end] are "HH:MM" and already validated. */
    data class SetQuietHours(val enabled: Boolean, val start: String, val end: String) : SettingsIntent
}
