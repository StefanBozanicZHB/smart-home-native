package zhb.company.smart_home.data.notifications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import zhb.company.smart_home.R
import zhb.company.smart_home.di.APP_SCOPE

/** "U redu je" / "Pravi problem" pressed on a notification. */
class FeedbackReceiver : BroadcastReceiver(), KoinComponent {

    private val log: NotificationLog by inject()
    private val push: PushNotifications by inject()
    private val scope: CoroutineScope by inject(named(APP_SCOPE))

    @SuppressLint("MissingPermission") // checked by push.hasPermission()
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val label = intent.getStringExtra(EXTRA_LABEL)?.takeIf { it == AlertPayload.LABEL_OK || it == AlertPayload.LABEL_PROBLEM } ?: return
        val tag = intent.getStringExtra(EXTRA_TAG)
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: PushNotifications.CHANNEL_SYSTEM
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val now = System.currentTimeMillis()

        // Replace the notification quietly with the chosen label; it disappears after a few seconds.
        if (push.hasPermission()) {
            val text = context.getString(
                if (label == AlertPayload.LABEL_OK) R.string.feedback_done_ok else R.string.feedback_done_problem,
            )
            val n = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setTimeoutAfter(CONFIRM_MS)
                .build()
            NotificationManagerCompat.from(context).notify(tag, 0, n)
        }

        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(WRITE_TIMEOUT_MS) { log.recordFeedback(id, label, now) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "feedback_id"
        const val EXTRA_LABEL = "feedback_label"
        const val EXTRA_TAG = "feedback_tag"
        const val EXTRA_CHANNEL = "feedback_channel"
        const val EXTRA_TITLE = "feedback_title"
        private const val CONFIRM_MS = 4_000L

        /** A broadcast receiver has about 10 s; the label stays queued if this runs out. */
        private const val WRITE_TIMEOUT_MS = 8_000L
    }
}
