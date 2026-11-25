package zhb.company.smart_home.widget

import android.app.KeyguardManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import zhb.company.smart_home.R
import zhb.company.smart_home.data.door.DoorOpener
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.data.repository.AuthRepository
import zhb.company.smart_home.di.APP_SCOPE

/**
 * 1x1 home screen button for the front door. Plain RemoteViews (no Glance session, no WorkManager):
 * it is drawn once when added and never refreshed, so between taps it costs nothing. A tap goes to
 * [DoorWidgetClickReceiver], which is not exported, so other apps cannot open the door through it.
 */
class DoorWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val tap = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, DoorWidgetClickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_door).apply {
            setOnClickPendingIntent(R.id.door_button, tap)
        }
        manager.updateAppWidget(ids, views)
    }
}

/** Sends the open command once per tap and shows the node's answer as a short message. */
class DoorWidgetClickReceiver : BroadcastReceiver(), KoinComponent {
    private val opener: DoorOpener by inject()
    private val auth: AuthRepository by inject()
    private val scope: CoroutineScope by inject(named(APP_SCOPE))

    override fun onReceive(context: Context, intent: Intent) {
        // Home screen widgets need an unlocked phone; this also covers a lock screen that shows widgets.
        if (context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) return
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                // A broadcast may run about 10 s; the opener needs at most connect (5 s) + answer (5 s) in the worst case.
                val outcome = withTimeoutOrNull(BUDGET_MS) { opener.open() } ?: CommandOutcome.Timeout
                val message = when {
                    outcome is CommandOutcome.NotConnected && !auth.hasSavedSession() -> R.string.door_widget_signed_out
                    outcome is CommandOutcome.Accepted -> R.string.home_door_opened
                    outcome is CommandOutcome.Rejected -> R.string.garden_msg_failed
                    outcome is CommandOutcome.Timeout -> R.string.heating_msg_timeout
                    outcome is CommandOutcome.NotConnected -> R.string.heating_msg_not_connected
                    else -> null // a previous tap is still being handled
                }
                if (message != null) {
                    android.os.Handler(app.mainLooper).post { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val BUDGET_MS = 9_000L
    }
}
