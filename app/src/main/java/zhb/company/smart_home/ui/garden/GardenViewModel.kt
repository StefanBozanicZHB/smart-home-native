package zhb.company.smart_home.ui.garden

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
import zhb.company.smart_home.domain.model.SmartHomeState
import zhb.company.smart_home.ui.format.Readings

class GardenViewModel(private val repository: SmartHomeRepository) : ViewModel() {

    private data class UiExtras(val showLocationDialog: Boolean = false)

    private val _uiExtras = MutableStateFlow(UiExtras())

    val state: StateFlow<GardenState> = combine(repository.state, _uiExtras) { home, extras ->
        mapState(home, extras)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GardenState())

    private fun mapState(home: SmartHomeState, extras: UiExtras) = GardenState(
        yardOnline = home.isNodeOnline(2),
        fountainOnline = home.isNodeOnline(1),
        sprinklerTimerOnline = home.isNodeOnline(0),
        poolOnline = home.isNodeOnline(6),
        temperature = Readings.temperature(home.temperatureOutdoor),
        humidity = Readings.percent(home.humidityOutdoor),
        ledStripOn = home.ledStripTerrace,
        bulbOn = home.bulbTerrace,
        fountainPumpOn = home.fountainPump,
        fountainLightOn = home.fountainLight,
        poolPumpOn = home.poolPump,
        poolHeatingOn = home.poolHeating,
        poolTemperature = Readings.temperature(home.temperaturePool),
        sprinklerActiveIndex = home.sprinklerPower,
        sprinklerAuto = home.sprinklerTimerAuto,
        sprinklerStartTime = home.sprinklerStartTime,
        sprinklerOnMinutes = home.sprinklerDurationOnMinutes,
        sprinklerSets = home.sprinklerSets,
        sprinklers = listOf(home.sprinkler1, home.sprinkler2, home.sprinkler3, home.sprinkler4, home.sprinkler5, home.sprinkler6),
        showLocationDialog = extras.showLocationDialog,
    )

    fun onIntent(intent: GardenIntent) {
        when (intent) {
            is GardenIntent.ToggleLedStrip -> publish(MqttTopics.NODE2_LED_STRIP_CONTROL, intent.on.wire())
            is GardenIntent.ToggleBulb -> publish(MqttTopics.NODE2_BULB_CONTROL, intent.on.wire())
            is GardenIntent.ToggleFountainPump -> publish(MqttTopics.NODE1_FOUNTAIN_PUMP_CONTROL, intent.on.wire())
            is GardenIntent.ToggleFountainLight -> publish(MqttTopics.NODE1_FOUNTAIN_LIGHT_CONTROL, intent.on.wire())
            is GardenIntent.TogglePoolPump -> publish(MqttTopics.NODE6_POOL_PUMP_CONTROL, intent.on.wire())
            is GardenIntent.TogglePoolHeating -> publish(MqttTopics.NODE6_POOL_HEATING_CONTROL, intent.on.wire())
            is GardenIntent.ToggleSprinkler -> {
                if (intent.on) publish(MqttTopics.NODE1_SPRINKLER_ALL_STOP, "0")
                publish(MqttTopics.node0SprinklerControl(intent.index), intent.on.wire())
            }
            GardenIntent.OpenLocationDialog -> _uiExtras.update { it.copy(showLocationDialog = true) }
            GardenIntent.DismissLocationDialog -> _uiExtras.update { it.copy(showLocationDialog = false) }
        }
    }

    private fun Boolean.wire() = if (this) "1" else "0"

    private fun publish(topic: String, payload: String) {
        viewModelScope.launch { repository.publish(topic, payload) }
    }
}
