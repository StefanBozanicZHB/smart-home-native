package zhb.company.smart_home.data.mqtt

sealed interface MqttConnectionState {
    data object Idle : MqttConnectionState
    data object Connecting : MqttConnectionState
    data object Connected : MqttConnectionState
    data class Failed(val message: String) : MqttConnectionState
    /** Was [Connected], then the broker connection was lost (network drop, broker restart, etc). */
    data class Disconnected(val message: String) : MqttConnectionState
}
