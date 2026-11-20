package zhb.company.smart_home.data.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import zhb.company.smart_home.BuildConfig
import zhb.company.smart_home.MainActivity
import zhb.company.smart_home.R
import zhb.company.smart_home.data.local.AppPreferences

/**
 * Receives the notifier's FCM messages. They are data-only, so they arrive here in the
 * foreground and in the background alike, and the app builds every notification itself: same
 * channel and tag as the notifier asks for, plus the feedback buttons on problem notifications.
 * Each arrival is recorded in Firebase (receipt time, for the RPi -> phone latency).
 */
class HomeAlertMessagingService : FirebaseMessagingService() {

    private val push: PushNotifications by inject()
    private val log: NotificationLog by inject()
    private val inbox: NotificationInbox by inject()
    private val preferences: AppPreferences by inject()

    override fun onMessageReceived(message: RemoteMessage) {
        val receivedMs = System.currentTimeMillis()
        val n = message.notification
        val payload = AlertPayload.parse(message.data, n?.title, n?.body, n?.channelId, n?.tag) ?: return
        // Into the in-app list first: it keeps the message even if notifications are off.
        runBlocking { inbox.add(payload, receivedMs) }
        if (push.hasPermission()) {
            val quiet = runBlocking { preferences.quietHours.first() }
            val now = java.time.LocalTime.now()
            show(this, payload, silent = quiet.silence(payload, now.hour * 60 + now.minute))
        }
        // This runs on a worker thread FCM gives the app for a short while; a write that does not
        // make it in time stays queued and goes out with the next one.
        runBlocking { withTimeoutOrNull(RECEIPT_TIMEOUT_MS) { log.recordReceipt(payload, receivedMs, BuildConfig.VERSION_NAME) } }
    }

    override fun onNewToken(token: String) {
        // Topic messaging only: the SDK moves topic subscriptions to the new token itself.
    }

    companion object {
        private const val RECEIPT_TIMEOUT_MS = 8_000L

        @SuppressLint("MissingPermission") // callers check PushNotifications.hasPermission()
        fun show(context: Context, p: AlertPayload, silent: Boolean = false) {
            val base = (p.tag ?: p.title).hashCode()
            val open = PendingIntent.getActivity(
                context,
                base,
                // The notifier's data as extras, so a tap can open the matching screen (reports).
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .apply { p.data.forEach { (k, v) -> putExtra(k, v) } },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = NotificationCompat.Builder(context, p.channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(p.title)
                .setContentText(p.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(p.body))
                .setAutoCancel(true)
                .setContentIntent(open)
                .setWhen(p.atMs ?: System.currentTimeMillis())
                .setShowWhen(true)
                .setSilent(silent)
            if (p.feedback && p.id != null) {
                builder.addAction(0, context.getString(R.string.feedback_ok), feedbackIntent(context, p, AlertPayload.LABEL_OK, base + 1))
                builder.addAction(0, context.getString(R.string.feedback_problem), feedbackIntent(context, p, AlertPayload.LABEL_PROBLEM, base + 2))
            }
            NotificationManagerCompat.from(context).notify(p.tag, 0, builder.build())
        }

        private fun feedbackIntent(context: Context, p: AlertPayload, label: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, FeedbackReceiver::class.java)
                    .putExtra(FeedbackReceiver.EXTRA_ID, p.id)
                    .putExtra(FeedbackReceiver.EXTRA_LABEL, label)
                    .putExtra(FeedbackReceiver.EXTRA_TAG, p.tag)
                    .putExtra(FeedbackReceiver.EXTRA_CHANNEL, p.channel)
                    .putExtra(FeedbackReceiver.EXTRA_TITLE, p.title),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
