package zhb.company.smart_home.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto

/**
 * Fetches the MQTT broker connection config from Firebase Realtime Database,
 * exactly as the Flutter app does (no caching - re-fetched on every login).
 */
class FirebaseRtdbApi(private val httpClient: HttpClient) : MqttConfigSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchMqttClientConfig(idToken: String): Result<MqttClientConfigDto> {
        return runCatching {
            val body: JsonElement = httpClient.get(
                "https://smart-home-bozanic-default-rtdb.firebaseio.com/clients.json?auth=$idToken"
            ).body()

            val firstEntry: JsonObject = when (body) {
                is JsonArray -> body.filterIsInstance<JsonObject>().firstOrNull()
                is JsonObject -> body.values.filterIsInstance<JsonObject>().firstOrNull()
                else -> null
            } ?: throw IllegalStateException("No MQTT client config found in Firebase response")

            json.decodeFromJsonElement<MqttClientConfigDto>(firstEntry)
        }
    }
}
