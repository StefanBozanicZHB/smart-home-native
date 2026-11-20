package zhb.company.smart_home.data.notifications

/**
 * One notifier message, read from FCM `data` (the notifier sends data-only messages, so the app
 * always builds the notification and can add the feedback buttons). Older messages with a
 * `notification` part pass their title, body, channel and tag as [fallback] values.
 */
data class AlertPayload(
    val id: String?,
    val title: String,
    val body: String?,
    val channel: String,
    /** Same tag replaces the previous notification (e.g. "back online" replaces "offline"). */
    val tag: String?,
    /** Show "U redu je" / "Pravi problem" (implicit labels for the thesis, RQ4). */
    val feedback: Boolean,
    val atMs: Long?,
    val data: Map<String, String>,
) {
    /** Fields stored in Firebase `notification_log/<id>` when the message arrives. */
    fun receiptFields(receivedMs: Long, appVersion: String): Map<String, Any> = buildMap {
        put("received_ms", receivedMs)
        put("app_version", appVersion)
        put("feedback_offered", feedback)
        for (key in LOGGED_KEYS) {
            val v = data[key]?.takeIf { it.isNotEmpty() } ?: continue
            put(key, if (key.endsWith("_ms")) v.toLongOrNull() ?: v else v)
        }
    }

    companion object {
        const val LABEL_OK = "ok"
        const val LABEL_PROBLEM = "problem"

        private val LOGGED_KEYS = listOf("type", "event", "node", "room", "state", "reason", "since_ms", "at_ms", "sent_ms", "title")

        /** RTDB keys may not contain . $ # [ ] / */
        private val SAFE_ID = Regex("""^[A-Za-z0-9_-]{1,128}$""")

        fun parse(
            data: Map<String, String>,
            fallbackTitle: String? = null,
            fallbackBody: String? = null,
            fallbackChannel: String? = null,
            fallbackTag: String? = null,
        ): AlertPayload? {
            val title = data["title"]?.takeIf { it.isNotEmpty() } ?: fallbackTitle ?: return null
            val channel = data["channel_id"]?.takeIf { it.isNotEmpty() } ?: fallbackChannel ?: when (data["type"]) {
                "node_status" -> PushNotifications.CHANNEL_NODES
                "heating" -> PushNotifications.CHANNEL_HEATING
                "report" -> PushNotifications.CHANNEL_REPORTS
                else -> PushNotifications.CHANNEL_SYSTEM
            }
            val id = data["id"]?.takeIf { SAFE_ID.matches(it) }
            return AlertPayload(
                id = id,
                title = title,
                body = data["body"]?.takeIf { it.isNotEmpty() } ?: fallbackBody,
                channel = channel,
                tag = data["tag"]?.takeIf { it.isNotEmpty() } ?: fallbackTag ?: data["node"]?.let { "node-$it" },
                feedback = data["feedback"] == "1" && id != null,
                atMs = data["at_ms"]?.toLongOrNull(),
                data = data,
            )
        }
    }
}
