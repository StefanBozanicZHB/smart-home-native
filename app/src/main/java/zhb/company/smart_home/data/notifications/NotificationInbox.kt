package zhb.company.smart_home.data.notifications

import android.content.Context
import androidx.core.util.AtomicFile
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One received notifier message, as kept on the phone for the in-app list. */
@Serializable
data class InboxEntry(
    /** The notifier's event id (same as `notification_log/<id>`); generated for messages without one. */
    val key: String,
    val id: String? = null,
    val title: String,
    val body: String? = null,
    val channel: String,
    val tag: String? = null,
    val type: String? = null,
    val event: String? = null,
    val state: String? = null,
    val feedbackOffered: Boolean = false,
    /** When it happened on the RPi side (`at_ms`), else when it arrived. */
    val atMs: Long,
    val receivedMs: Long,
    val feedback: String? = null,
    val feedbackMs: Long? = null,
    /** "notification" (button in the shade) or "app" (this list). */
    val feedbackVia: String? = null,
    /** Extras for opening the matching screen (e.g. a report date). */
    val extras: Map<String, String> = emptyMap(),
) {
    val needsLabel: Boolean get() = feedbackOffered && id != null && feedback == null

    /** Tag of its notification in the shade, as [AlertPayload.notificationTag] gave it (entries that can be labeled have an id). */
    val notificationTag: String? get() = tag ?: id?.let { "id-$it" }
}

/**
 * The last notifications on this phone, newest first, for the in-app list. Every message the
 * notifier sends lands here when it arrives (also with notifications turned off or silenced),
 * and labels given here or on the notification are kept here as well as sent to Firebase
 * through [NotificationLog].
 */
class NotificationInbox(context: Context, private val log: NotificationLog) {

    // Written to a temporary file and swapped in: a write cut short (process killed) keeps the old list.
    private val file = AtomicFile(File(context.filesDir, "notification_inbox.json"))
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<InboxEntry>> = _entries.asStateFlow()

    suspend fun add(payload: AlertPayload, receivedMs: Long) = update { list ->
        val key = payload.id ?: "local-$receivedMs-${payload.title.hashCode()}"
        if (list.any { it.key == key }) return@update list // FCM may deliver a message twice
        val entry = InboxEntry(
            key = key,
            id = payload.id,
            title = payload.title,
            body = payload.body,
            channel = payload.channel,
            tag = payload.tag,
            type = payload.data["type"],
            event = payload.data["event"],
            state = payload.data["state"],
            feedbackOffered = payload.feedback,
            atMs = payload.atMs ?: receivedMs,
            receivedMs = receivedMs,
            extras = payload.data.filterKeys { it in EXTRA_KEYS },
        )
        (listOf(entry) + list).sortedByDescending { it.receivedMs }.take(MAX_ENTRIES)
    }

    /**
     * Label from the notification or from the list. A later label replaces an earlier one (a
     * mis-tap can be corrected); Firebase keeps the last one with its time and source.
     */
    suspend fun label(id: String, label: String, via: String, atMs: Long = System.currentTimeMillis()) {
        update { list -> list.map { if (it.id == id) it.copy(feedback = label, feedbackMs = atMs, feedbackVia = via) else it } }
        log.recordFeedback(id, label, atMs, via)
    }

    suspend fun clear() = update { emptyList() }

    private suspend fun update(change: (List<InboxEntry>) -> List<InboxEntry>) = mutex.withLock {
        val next = change(_entries.value)
        if (next !== _entries.value) {
            _entries.value = next
            save(json.encodeToString(next))
        }
    }

    private fun save(text: String) {
        val out = runCatching { file.startWrite() }.getOrNull() ?: return
        runCatching {
            out.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        }.onFailure { file.failWrite(out) }
    }

    private fun load(): List<InboxEntry> =
        runCatching { json.decodeFromString<List<InboxEntry>>(String(file.readFully(), Charsets.UTF_8)) }.getOrDefault(emptyList())

    companion object {
        const val VIA_NOTIFICATION = "notification"
        const val VIA_APP = "app"
        private const val MAX_ENTRIES = 200
        private val EXTRA_KEYS = setOf("type", "kind", "date", "node", "room")

        /**
         * For a problem entry: the later entry with the same tag that ended it ("back online",
         * "heating normal again"), or null while it lasts / when unknown. [entries] newest first.
         */
        fun resolution(entry: InboxEntry, entries: List<InboxEntry>): InboxEntry? {
            if (!entry.feedbackOffered || entry.tag == null) return null
            return entries.lastOrNull { it.tag == entry.tag && it.receivedMs > entry.receivedMs && !it.feedbackOffered }
                ?.takeIf { end ->
                    // Only if no newer problem with the same tag came first.
                    entries.none { it.tag == entry.tag && it.feedbackOffered && it.receivedMs > entry.receivedMs && it.receivedMs < end.receivedMs }
                }
        }
    }
}
