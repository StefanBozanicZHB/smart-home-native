package zhb.company.smart_home.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Broker connection config from Firebase (`clients`): the main broker and a backup one. */
@Serializable
data class MqttClientConfigDto(
    val mqttServer: String = "",
    val mqttPort: String = "",
    val mqttUsername: String = "",
    val mqttPassword: String = "",
    val backupMqttServer: String = "",
    val backupMqttPort: String = "",
    val backupMqttUsername: String = "",
    val backupMqttPassword: String = "",
) {
    companion object {
        /**
         * Reads the config leniently: a value may be a string or a number (the port was stored as
         * `"1883"`, now as `1883`); missing or null values are empty. Never fails on the format, so
         * an error message can never echo the stored credentials.
         */
        fun fromJson(o: JsonObject): MqttClientConfigDto {
            fun field(key: String): String = (o[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.trim().orEmpty()
            return MqttClientConfigDto(
                mqttServer = field("mqttServer"),
                mqttPort = field("mqttPort"),
                mqttUsername = field("mqttUsername"),
                mqttPassword = field("mqttPassword"),
                backupMqttServer = field("backupMqttServer"),
                backupMqttPort = field("backupMqttPort"),
                backupMqttUsername = field("backupMqttUsername"),
                backupMqttPassword = field("backupMqttPassword"),
            )
        }
    }
}
