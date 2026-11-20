package zhb.company.smart_home.data.notifications

/**
 * Quiet hours: between [startMinute] and [endMinute] (minutes after midnight, may wrap past
 * midnight, e.g. 22:00-07:00) notifications that are not urgent are posted silently: they still
 * appear in the shade, but without sound, vibration or a heads-up.
 *
 * Urgent = a problem (the notifier offers feedback buttons) on the node or heating channel: a node
 * is down, heating is in safe mode, a room is too cold. "Back online", "resolved", reports and
 * system notices (logger, RPi, broker) wait quietly until morning.
 */
data class QuietHours(val enabled: Boolean, val startMinute: Int, val endMinute: Int) {

    fun isQuietAt(minuteOfDay: Int): Boolean {
        if (!enabled || startMinute == endMinute) return false
        return if (startMinute < endMinute) minuteOfDay in startMinute until endMinute
        else minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    fun silence(payload: AlertPayload, minuteOfDay: Int): Boolean = isQuietAt(minuteOfDay) && !isUrgent(payload)

    companion object {
        const val DEFAULT_START = "22:00"
        const val DEFAULT_END = "07:00"

        private val URGENT_CHANNELS = setOf(PushNotifications.CHANNEL_NODES, PushNotifications.CHANNEL_HEATING)

        fun isUrgent(p: AlertPayload): Boolean = p.feedback && p.channel in URGENT_CHANNELS

        /** "HH:MM" -> minutes after midnight, or null if invalid. */
        fun parseTime(text: String): Int? {
            val m = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(text.trim()) ?: return null
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            return if (h in 0..23 && min in 0..59) h * 60 + min else null
        }

        fun of(enabled: Boolean, start: String, end: String) = QuietHours(
            enabled,
            parseTime(start) ?: parseTime(DEFAULT_START)!!,
            parseTime(end) ?: parseTime(DEFAULT_END)!!,
        )
    }
}
