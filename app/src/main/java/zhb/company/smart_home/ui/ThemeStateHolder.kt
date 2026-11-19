package zhb.company.smart_home.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import zhb.company.smart_home.data.local.AppPreferences

/** Exposes the persisted dark-theme preference to the Compose root so SmartHomeTheme can react to it. */
class ThemeStateHolder(preferences: AppPreferences) : ViewModel() {
    val isDarkTheme: Flow<Boolean> = preferences.isDarkTheme
}
