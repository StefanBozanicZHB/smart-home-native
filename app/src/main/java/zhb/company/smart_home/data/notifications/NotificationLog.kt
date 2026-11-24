package zhb.company.smart_home.data.notifications

import androidx.core.content.edit
import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import zhb.company.smart_home.data.remote.FirebaseUrls
import zhb.company.smart_home.data.repository.AuthRepository

/**
 * Writes notification receipts and the user's labels to Firebase `notification_log/<id>`
 * (merged with PATCH, so receipt and feedback end up in one record).
 *
 * Writes go through a small persistent queue: a notification may arrive or a button may be
 * pressed without network, and a lost label is lost thesis data. The queue is flushed after every
 * enqueue and on app start.
 */
class NotificationLog(
    context: Context,
    private val httpClient: HttpClient,
    private val auth: AuthRepository,
) {
    @Serializable
    private data class Pending(val id: String, val fields: Map<String, JsonElement>)

    private val prefs = context.getSharedPreferences("notification_log_queue", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun recordReceipt(payload: AlertPayload, receivedMs: Long, appVersion: String) {
        val id = payload.id ?: return
        enqueue(id, payload.receiptFields(receivedMs, appVersion))
        flush()
    }

    /** [via]: where the label was given, [NotificationInbox.VIA_NOTIFICATION] or [NotificationInbox.VIA_APP]. */
    suspend fun recordFeedback(id: String, label: String, atMs: Long, via: String) {
        enqueue(id, mapOf("feedback" to label, "feedback_ms" to atMs, "feedback_via" to via))
        flush()
    }

    /** Sends what is queued; whatever fails stays for the next try. */
    suspend fun flush() = mutex.withLock {
        val queue = load()
        if (queue.isEmpty()) return@withLock
        val left = queue.filterNot { send(it) }
        save(left)
    }

    private suspend fun enqueue(id: String, fields: Map<String, Any>) = mutex.withLock {
        val element = fields.mapValues { (_, v) ->
            when (v) {
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                else -> JsonPrimitive(v.toString())
            }
        }
        save((load() + Pending(id, element)).takeLast(MAX_QUEUE))
    }

    private suspend fun send(item: Pending): Boolean = runCatching {
        val token = auth.freshIdToken() ?: return false
        val response = httpClient.patch("${FirebaseUrls.RTDB}/notification_log/${item.id}.json") {
            parameter("auth", token)
            contentType(ContentType.Application.Json)
            setBody(JsonObject(item.fields).toString())
        }
        if (!response.status.isSuccess()) Log.w(TAG, "notification_log ${item.id}: HTTP ${response.status.value}")
        // 4xx other than auth (e.g. rules) will not get better by retrying: drop it.
        response.status.isSuccess() || response.status.value in 400..499 && response.status.value != 401
    }.getOrElse {
        Log.w(TAG, "notification_log ${item.id}: ${it.message}")
        false
    }

    private fun load(): List<Pending> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString<List<Pending>>(it) }.getOrNull() } ?: emptyList()

    private fun save(items: List<Pending>) {
        prefs.edit { putString(KEY, json.encodeToString(items)) }
    }

    private companion object {
        const val TAG = "NotificationLog"
        const val KEY = "pending"
        const val MAX_QUEUE = 200
    }
}
