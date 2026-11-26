package zhb.company.smart_home

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
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
import zhb.company.smart_home.data.wall.WallMode
import zhb.company.smart_home.ui.wall.WallHost
import zhb.company.smart_home.ui.ThemeStateHolder
import zhb.company.smart_home.ui.navigation.PendingNavigation
import zhb.company.smart_home.ui.navigation.SmartHomeNavGraph
import zhb.company.smart_home.ui.theme.SmartHomeTheme

/** Scrims behind three-button navigation (AndroidX defaults); gesture navigation stays transparent. */
private val LIGHT_NAV_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_NAV_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : FragmentActivity() {
    private val preferences: AppPreferences by inject()
    private val push: PushNotifications by inject()
    private val pendingNavigation: PendingNavigation by inject()
    private val wallMode: WallMode by inject()

    /** Last touch anywhere (wall mode: back to the wall after a while, brighten the night screen). */
    private val lastTouchMs = mutableLongStateOf(System.currentTimeMillis())

    // Result is not needed: without the permission Android simply does not show notifications,
    // and the settings dialog explains it.
    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askForNotificationPermissionOnce()
        if (savedInstanceState == null) pendingNavigation.handle(intent)
        setContent {
            val themeViewModel: ThemeStateHolder = koinViewModel()
            val darkTheme by themeViewModel.isDarkTheme.collectAsState(initial = false)
            // The app's own light/dark switch, not the system setting, decides the status bar
            // icon color; otherwise dark icons end up on the dark theme's background.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { darkTheme },
                )
                onDispose {}
            }
            val wall by wallMode.enabled.collectAsState()
            SmartHomeTheme(darkTheme = darkTheme) {
                when (wall) {
                    // The setting is read in a moment; until then only the background.
                    null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    true -> WallHost(this, darkTheme, lastTouchMs)
                    false -> SmartHomeNavGraph()
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) lastTouchMs.longValue = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingNavigation.handle(intent)
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
