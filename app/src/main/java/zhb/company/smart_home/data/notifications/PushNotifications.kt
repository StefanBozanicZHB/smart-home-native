package zhb.company.smart_home.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import zhb.company.smart_home.R
import zhb.company.smart_home.data.local.AppPreferences

/**
 * Push notifications from the house system. The RPi notifier (DAS repo, `05_eksperimenti/notifier`)
 * sends FCM messages to the topic [TOPIC]; this class keeps the topic subscription in line with
 * the user's setting and owns the notification channels the notifier names in its messages.
 */
class PushNotifications(
    private val context: Context,
    private val preferences: AppPreferences,
    private val scope: CoroutineScope,
    private val log: NotificationLog,
) {
    /** Creates the channels and follows the on/off setting (subscribe / unsubscribe the topic). */
    fun start() {
        createChannels()
        // Receipts and labels that could not be sent earlier (no network).
        scope.launch { log.flush() }
        scope.launch {
            preferences.notificationsEnabled.distinctUntilChanged().collect(::applySubscription)
        }
    }

    /** Android 13+ needs the runtime permission; older versions always allow notifications. */
    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun applySubscription(enabled: Boolean) {
        val messaging = runCatching { FirebaseMessaging.getInstance() }.getOrElse {
            Log.w(TAG, "Firebase is not initialized, push notifications are off", it)
            return
        }
        // The SDK queues the request and retries it when the network is back.
        val task = if (enabled) messaging.subscribeToTopic(TOPIC) else messaging.unsubscribeFromTopic(TOPIC)
        task.addOnCompleteListener {
            if (it.isSuccessful) Log.i(TAG, "Topic $TOPIC: ${if (enabled) "subscribed" else "unsubscribed"}")
            else Log.w(TAG, "Topic $TOPIC change failed", it.exception)
        }
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_NODES,
                    context.getString(R.string.notification_channel_nodes),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = context.getString(R.string.notification_channel_nodes_description) },
                NotificationChannel(
                    CHANNEL_HEATING,
                    context.getString(R.string.notification_channel_heating),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = context.getString(R.string.notification_channel_heating_description) },
                NotificationChannel(
                    CHANNEL_REPORTS,
                    context.getString(R.string.notification_channel_reports),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.notification_channel_reports_description) },
                NotificationChannel(
                    CHANNEL_SYSTEM,
                    context.getString(R.string.notification_channel_system),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.notification_channel_system_description) },
            ),
        )
    }

    companion object {
        private const val TAG = "PushNotifications"

        /** Must match `[fcm] topic` in the notifier config. */
        const val TOPIC = "home-alerts"

        /** Channel ids the notifier puts in `android.notification.channel_id`. */
        const val CHANNEL_NODES = "node_status"
        const val CHANNEL_HEATING = "heating_alerts"
        const val CHANNEL_SYSTEM = "system_status"
        const val CHANNEL_REPORTS = "reports"

        /** The channels this app creates; a notification on any other id would not be shown. */
        val CHANNELS = setOf(CHANNEL_NODES, CHANNEL_HEATING, CHANNEL_SYSTEM, CHANNEL_REPORTS)
    }
}
