package zhb.company.smart_home.data.mqtt

sealed interface MqttConnectionState {
    /** No session: before sign-in or after sign-out. */
    data object Idle : MqttConnectionState

    /** First connect after sign-in (config fetch + broker connect). */
    data object Connecting : MqttConnectionState
    data object Connected : MqttConnectionState

    /** First connect failed; the user retries from the connect screen. */
    data class Failed(val message: String) : MqttConnectionState

    /** Was connected, the connection dropped; an automatic reconnect follows when possible. */
    data class Disconnected(val message: String) : MqttConnectionState

    /** Automatic reconnect in progress; [attempt] counts from 1 since the connection was lost. */
    data class Reconnecting(val attempt: Int) : MqttConnectionState

    /** Disconnected on purpose while the app is in the background; resumes when it returns. */
    data object Paused : MqttConnectionState
}
