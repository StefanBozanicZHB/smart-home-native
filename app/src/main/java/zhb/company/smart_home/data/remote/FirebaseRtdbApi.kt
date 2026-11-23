package zhb.company.smart_home.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto

/**
 * Fetches the MQTT broker connection config from Firebase Realtime Database,
 * re-fetched on every sign-in (no caching).
 */
class FirebaseRtdbApi(private val httpClient: HttpClient) : MqttConfigSource {

    override suspend fun fetchMqttClientConfig(idToken: String): Result<MqttClientConfigDto> {
        return runCatching {
            val response = httpClient.get("${FirebaseUrls.RTDB}/clients.json?auth=$idToken")
            // e.g. 401 {"error":"Permission denied"} when the database rules do not allow the read
            check(response.status.isSuccess()) { "Firebase clients: HTTP ${response.status.value} ${response.bodyAsText().take(100)}" }
            val body: JsonElement = response.body()

            val firstEntry: JsonObject = when (body) {
                is JsonArray -> body.filterIsInstance<JsonObject>().firstOrNull()
                is JsonObject -> body.values.filterIsInstance<JsonObject>().firstOrNull()
                else -> null
            } ?: throw IllegalStateException("No MQTT client config found in Firebase response")

            MqttClientConfigDto.fromJson(firstEntry)
        }
    }
}
