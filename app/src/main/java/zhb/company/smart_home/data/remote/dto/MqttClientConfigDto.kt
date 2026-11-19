package zhb.company.smart_home.data.remote.dto

import kotlinx.serialization.Serializable

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
)
