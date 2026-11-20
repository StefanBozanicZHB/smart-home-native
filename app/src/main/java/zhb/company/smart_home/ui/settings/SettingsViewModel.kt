package zhb.company.smart_home.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.local.AppPreferences
import zhb.company.smart_home.data.notifications.QuietHours

/** App settings on the System tab: theme, notifications, quiet hours. Applied right away. */
class SettingsViewModel(private val preferences: AppPreferences) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(
        preferences.isDarkTheme,
        preferences.notificationsEnabled,
    ) { darkTheme, notifications ->
        SettingsState(darkTheme = darkTheme, notifications = notifications)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    val quietHours: StateFlow<QuietHours?> = preferences.quietHours
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.ToggleDarkTheme -> viewModelScope.launch { preferences.setDarkTheme(intent.enabled) }
            is SettingsIntent.ToggleNotifications -> viewModelScope.launch { preferences.setNotificationsEnabled(intent.enabled) }
            is SettingsIntent.SetQuietHours -> viewModelScope.launch {
                runCatching { preferences.setQuietHours(intent.enabled, intent.start, intent.end) }
            }
        }
    }
}
