package zhb.company.smart_home.data.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.android.ext.android.inject
import zhb.company.smart_home.MainActivity
import zhb.company.smart_home.R

/**
 * Receives the notifier's FCM messages.
 *
 * In the background the system shows them itself (they carry a `notification` part with the
 * channel and tag). In the foreground FCM hands them here, so the same notification is built
 * by hand: same channel and tag, so "back online" still replaces "offline" for that node.
 */
class HomeAlertMessagingService : FirebaseMessagingService() {

    private val push: PushNotifications by inject()

    @SuppressLint("MissingPermission") // checked by push.hasPermission()
    override fun onMessageReceived(message: RemoteMessage) {
        if (!push.hasPermission()) return
        val n = message.notification
        val data = message.data
        val title = n?.title ?: data["title"] ?: return
        val body = n?.body ?: data["body"]
        val channel = n?.channelId ?: if (data["type"] == "node_status") PushNotifications.CHANNEL_NODES else PushNotifications.CHANNEL_SYSTEM
        val tag = n?.tag ?: data["node"]?.let { "node-$it" }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setWhen(data["at_ms"]?.toLongOrNull() ?: System.currentTimeMillis())
            .setShowWhen(true)
            .build()
        // FCM's own display uses id 0 with the tag, so the same (tag, id) pair replaces it.
        NotificationManagerCompat.from(this).notify(tag, 0, notification)
    }

    override fun onNewToken(token: String) {
        // Topic messaging only: the SDK moves topic subscriptions to the new token itself.
    }
}
