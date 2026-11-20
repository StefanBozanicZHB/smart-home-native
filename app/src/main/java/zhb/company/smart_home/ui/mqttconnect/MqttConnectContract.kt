package zhb.company.smart_home.ui.mqttconnect

data class MqttConnectState(
    val isConnecting: Boolean = true,
    val isFailed: Boolean = false,
)

sealed interface MqttConnectIntent {
    data object Retry : MqttConnectIntent
}

sealed interface MqttConnectEffect {
    data object NavigateToHome : MqttConnectEffect
    /** No signed-in session (e.g. the app process was restarted): sign in again. */
    data object NavigateToAuth : MqttConnectEffect
}
