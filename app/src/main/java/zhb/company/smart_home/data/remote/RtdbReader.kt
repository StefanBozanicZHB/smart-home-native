package zhb.company.smart_home.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import zhb.company.smart_home.data.repository.AuthRepository

/**
 * Reads what the RPi writes to Firebase Realtime Database (reports, history) as the signed-in
 * user. The database rules allow these paths only with a valid ID token.
 */
class RtdbReader(private val httpClient: HttpClient, private val auth: AuthRepository) {

    /** The JSON at [path], or [JsonNull] when nothing is stored there. */
    suspend fun get(path: String, params: HttpRequestBuilder.() -> Unit = {}): JsonElement {
        val token = auth.freshIdToken() ?: throw RtdbException(RtdbError.NOT_SIGNED_IN)
        val response = httpClient.get("${FirebaseUrls.RTDB}/$path.json") {
            parameter("auth", token)
            params()
        }
        when (response.status) {
            HttpStatusCode.OK -> Unit
            HttpStatusCode.Unauthorized -> throw RtdbException(RtdbError.PERMISSION_DENIED)
            else -> throw RtdbException(RtdbError.HTTP, "HTTP ${response.status.value}")
        }
        val text = response.bodyAsText()
        return if (text.isBlank()) JsonNull else json.parseToJsonElement(text)
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    }
}

enum class RtdbError { NOT_SIGNED_IN, PERMISSION_DENIED, HTTP }

class RtdbException(val error: RtdbError, message: String? = null) : Exception(message ?: error.name)

/** Any failure as an [RtdbError]; network errors and bad JSON count as [RtdbError.HTTP]. */
fun Throwable.toRtdbError(): RtdbError = (this as? RtdbException)?.error ?: RtdbError.HTTP
