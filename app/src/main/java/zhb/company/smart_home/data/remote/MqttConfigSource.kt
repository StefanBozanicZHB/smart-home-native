package zhb.company.smart_home.data.remote

import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto

/** Supplies the MQTT broker config for a signed-in user (production: [FirebaseRtdbApi]). */
fun interface MqttConfigSource {
    suspend fun fetchMqttClientConfig(idToken: String): Result<MqttClientConfigDto>
}
