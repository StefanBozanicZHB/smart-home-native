package zhb.company.smart_home.ui.smallhome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.mqtt.MqttTopics
import zhb.company.smart_home.data.mqtt.SmartHomeRepository

class SmallHomeViewModel(private val repository: SmartHomeRepository) : ViewModel() {

    private data class UiExtras(val addIdInput: String = "", val deleteIdInput: String = "")

    private val _uiExtras = MutableStateFlow(UiExtras())

    val state: StateFlow<SmallHomeState> = combine(repository.state, _uiExtras) { home, extras ->
        SmallHomeState(
            online = home.isNodeOnline(7),
            doorOn = home.doorSmallHome,
            bulb1 = home.bulbSmallHome1,
            bulb2 = home.bulbSmallHome2,
            bulb3 = home.bulbSmallHome3,
            fingerLog = home.fingerSensorStatus,
            addIdInput = extras.addIdInput,
            deleteIdInput = extras.deleteIdInput,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SmallHomeState())

    fun onIntent(intent: SmallHomeIntent) {
        when (intent) {
            is SmallHomeIntent.ToggleDoor -> publish(MqttTopics.NODE7_DOOR_CONTROL, intent.on.wire())
            is SmallHomeIntent.ToggleBulb1 -> publish(MqttTopics.node7BulbControl(1), intent.on.wire())
            is SmallHomeIntent.ToggleBulb2 -> publish(MqttTopics.node7BulbControl(2), intent.on.wire())
            is SmallHomeIntent.ToggleBulb3 -> publish(MqttTopics.node7BulbControl(3), intent.on.wire())
            is SmallHomeIntent.AddIdInputChanged -> _uiExtras.update { it.copy(addIdInput = intent.value) }
            is SmallHomeIntent.DeleteIdInputChanged -> _uiExtras.update { it.copy(deleteIdInput = intent.value) }
            SmallHomeIntent.AddFingerprintId -> {
                val id = _uiExtras.value.addIdInput
                if (id.isNotBlank()) publish(MqttTopics.NODE7_FINGER_ADD_CONTROL, id)
                _uiExtras.update { it.copy(addIdInput = "") }
            }
            SmallHomeIntent.DeleteFingerprintId -> {
                val id = _uiExtras.value.deleteIdInput
                if (id.isNotBlank()) publish(MqttTopics.NODE7_FINGER_DEL_CONTROL, id)
                _uiExtras.update { it.copy(deleteIdInput = "") }
            }
        }
    }

    private fun Boolean.wire() = if (this) "1" else "0"

    private fun publish(topic: String, payload: String) {
        viewModelScope.launch { repository.publish(topic, payload) }
    }
}
