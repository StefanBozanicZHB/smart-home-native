package zhb.company.smart_home.data.mqtt

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import zhb.company.smart_home.data.local.SecretCipher
import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto

/**
 * The brokers of the last sign-in, kept between app starts so the door widget can connect at once,
 * without first renewing the Firebase token and reading the config (two extra round trips).
 */
interface BrokerCache {
    suspend fun load(): List<MqttEndpoint>?
    suspend fun save(endpoints: List<MqttEndpoint>)
    suspend fun clear()
}

/** Where [EncryptedBrokerCache] keeps its sealed text (the app preferences, left out of backups). */
interface BrokerStore {
    suspend fun sealedBrokers(): String?
    suspend fun saveSealedBrokers(sealed: String)
    suspend fun clearBrokers()
}

/** [BrokerCache] encrypted with the same Keystore key as the session (it holds the broker password). */
class EncryptedBrokerCache(private val store: BrokerStore, private val cipher: SecretCipher) : BrokerCache {

    @Serializable
    private data class Saved(val host: String, val port: Int, val username: String, val password: String)

    override suspend fun load(): List<MqttEndpoint>? {
        val plain = store.sealedBrokers()?.let(cipher::decrypt) ?: return null
        return runCatching { json.decodeFromString<List<Saved>>(plain) }.getOrNull()
            ?.map { MqttEndpoint(it.host, it.port, it.username, it.password) }
            ?.takeIf { it.isNotEmpty() }
    }

    override suspend fun save(endpoints: List<MqttEndpoint>) {
        val saved = endpoints.map { Saved(it.host, it.port, it.username, it.password) }
        store.saveSealedBrokers(cipher.encrypt(json.encodeToString(saved)))
    }

    override suspend fun clear() = store.clearBrokers()

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Primary, then backup broker from the Firebase config; unusable entries are left out. */
fun MqttClientConfigDto.toEndpoints(): List<MqttEndpoint> = listOfNotNull(
    MqttEndpoint.parse(mqttServer, mqttPort, mqttUsername, mqttPassword),
    MqttEndpoint.parse(backupMqttServer, backupMqttPort, backupMqttUsername, backupMqttPassword),
)
