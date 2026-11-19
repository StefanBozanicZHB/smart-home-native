package zhb.company.smart_home.ui.mqttconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.repository.AuthRepository

class MqttConnectViewModel(
    private val repository: SmartHomeRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MqttConnectState())
    val state: StateFlow<MqttConnectState> = _state.asStateFlow()

    private val _effects = Channel<MqttConnectEffect>()
    val effects = _effects.receiveAsFlow()

    init {
        connect()
    }

    fun onIntent(intent: MqttConnectIntent) {
        when (intent) {
            MqttConnectIntent.Retry -> connect()
        }
    }

    private fun connect() {
        val idToken = authRepository.idToken ?: return
        viewModelScope.launch {
            _state.update { it.copy(isConnecting = true, isFailed = false) }
            repository.connect(idToken)
            when (repository.connectionState.value) {
                is MqttConnectionState.Connected -> {
                    _state.update { it.copy(isConnecting = false, isFailed = false) }
                    _effects.send(MqttConnectEffect.NavigateToHome)
                }
                else -> _state.update { it.copy(isConnecting = false, isFailed = true) }
            }
        }
    }
}
