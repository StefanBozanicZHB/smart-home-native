package zhb.company.smart_home.ui.wall

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import zhb.company.smart_home.MainActivity
import zhb.company.smart_home.data.local.AppPreferences

/**
 * Brings the wall screen back after the tablet restarts or the app is updated, when the wall mode
 * is on. Phones (mode off) do nothing here. Huawei tablets also need the app allowed to start by
 * itself (Settings → Apps → Startup / Pokretanje aplikacija).
 */
class WallStartReceiver : BroadcastReceiver(), KoinComponent {
    private val preferences: AppPreferences by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (preferences.wallMode.first()) app.startActivity(WallRestart.launchIntent(app))
            } finally {
                pending.finish()
            }
        }
    }
}

/** Restart of the wall screen after a crash: the activity is started again a few seconds later. */
object WallRestart {
    private const val DELAY_MS = 3_000L

    fun launchIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    /** Wraps the default crash handler; [isWallOn] is read when the crash happens. */
    fun install(context: Context, isWallOn: () -> Boolean) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (runCatching(isWallOn).getOrDefault(false)) {
                runCatching {
                    val pi = PendingIntent.getActivity(
                        context, 0, launchIntent(context),
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    context.getSystemService(AlarmManager::class.java)
                        ?.set(AlarmManager.RTC, System.currentTimeMillis() + DELAY_MS, pi)
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
