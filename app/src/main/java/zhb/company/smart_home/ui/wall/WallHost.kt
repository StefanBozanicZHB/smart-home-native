package zhb.company.smart_home.ui.wall

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.time.LocalTime
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.wall.TabletReporter
import zhb.company.smart_home.ui.navigation.Destination
import zhb.company.smart_home.ui.navigation.SmartHomeNavGraph

/** Back to the wall after this long without a touch on the normal screens. */
private const val BACK_TO_WALL_MS = 2 * 60_000L

/** A touch brightens the night screen for this long. */
private const val NIGHT_WAKE_MS = 30_000L

/** Night (dimmed screen) from [NIGHT_START] to [NIGHT_END] o'clock. */
private const val NIGHT_START = 23
private const val NIGHT_END = 6

/** Window brightness at night (0..1); the clock stays readable. */
private const val NIGHT_BRIGHTNESS = 0.05f

/**
 * The app in wall mode: the wall screen, or the normal screens for a while after the settings icon
 * is tapped. Keeps the window landscape, full screen and always on. [lastTouchMs] is updated by the
 * activity on every touch.
 */
@Composable
fun WallHost(activity: Activity, darkTheme: Boolean, lastTouchMs: LongState) {
    val repository: SmartHomeRepository = koinInject()
    val reporter: TabletReporter = koinInject()
    var showApp by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            now = System.currentTimeMillis()
        }
    }
    // Normal screens return to the wall when nobody touches them.
    if (showApp && now - lastTouchMs.longValue > BACK_TO_WALL_MS) showApp = false

    val hour = LocalTime.now().hour
    val nightHours = hour >= NIGHT_START || hour < NIGHT_END
    // Reads [now], so it is checked again every few seconds (night starts, a touch wears off).
    val night = !showApp && nightHours && now - lastTouchMs.longValue > NIGHT_WAKE_MS
    LaunchedEffect(night) { reporter.night.value = night }

    DisposableEffect(Unit) {
        val window = activity.window
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
    DisposableEffect(night) {
        val window = activity.window
        window.attributes = window.attributes.apply {
            screenBrightness = if (night) NIGHT_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        onDispose {}
    }

    if (showApp) {
        SmartHomeNavGraph(startDestination = if (repository.hasSession) Destination.Home.route else Destination.Auth.route)
    } else {
        WallScreen(darkTheme = darkTheme, night = night, onOpenApp = { showApp = true })
    }
}
