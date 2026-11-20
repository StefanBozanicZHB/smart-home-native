package zhb.company.smart_home

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.data.local.AppPreferences
import zhb.company.smart_home.data.notifications.PushNotifications
import zhb.company.smart_home.ui.ThemeStateHolder
import zhb.company.smart_home.ui.navigation.SmartHomeNavGraph
import zhb.company.smart_home.ui.theme.SmartHomeTheme

class MainActivity : FragmentActivity() {
    private val preferences: AppPreferences by inject()
    private val push: PushNotifications by inject()

    // Result is not needed: without the permission Android simply does not show notifications,
    // and the settings dialog explains it.
    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askForNotificationPermissionOnce()
        setContent {
            val themeViewModel: ThemeStateHolder = koinViewModel()
            val darkTheme by themeViewModel.isDarkTheme.collectAsState(initial = false)
            SmartHomeTheme(darkTheme = darkTheme) {
                SmartHomeNavGraph()
            }
        }
    }

    /** Asks once, on the first start with notifications on; later only from the settings switch. */
    private fun askForNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || push.hasPermission()) return
        lifecycleScope.launch {
            if (!preferences.notificationsEnabled.first()) return@launch
            if (preferences.markNotificationPermissionAsked()) return@launch
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
