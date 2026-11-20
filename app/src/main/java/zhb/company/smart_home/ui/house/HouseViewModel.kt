package zhb.company.smart_home.ui.house

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.mqtt.MqttTopics
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.domain.AcCommandBuilder
import zhb.company.smart_home.domain.model.AcFanSpeed
import zhb.company.smart_home.domain.model.AcMode
import zhb.company.smart_home.ui.format.Readings

class HouseViewModel(private val repository: SmartHomeRepository) : ViewModel() {

    val state: StateFlow<HouseState> = repository.state.map { home ->
        HouseState(
            groundOnline = home.isNodeOnline(4),
            groundTemperature = Readings.temperature(home.temperatureLivingRoomDown),
            groundHumidity = Readings.percent(home.humidityLivingRoomDown),
            upperOnline = home.isNodeOnline(8),
            upperTemperature = Readings.temperature(home.temperatureLivingRoomUp),
            upperHumidity = Readings.percent(home.humidityLivingRoomUp),
            co2 = home.gasLivingRoomUp,
            acOn = home.acOn,
            acMode = home.acMode,
            acFanSpeed = home.acFanSpeed,
            acTemp = home.acTemp,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HouseState())

    fun onIntent(intent: HouseIntent) {
        when (intent) {
            HouseIntent.PowerOn -> {
                repository.setAcPower(true)
                sendAcCommand()
            }
            HouseIntent.PowerOff -> {
                repository.setAcPower(false)
                publish(MqttTopics.NODE8_AC_COMMAND, AcCommandBuilder.POWER_OFF_COMMAND)
            }
            is HouseIntent.SelectMode -> {
                publish(MqttTopics.NODE8_AC_MODE, intent.mode.code, retain = true)
                sendAcCommand(mode = intent.mode)
            }
            is HouseIntent.SelectFanSpeed -> {
                publish(MqttTopics.NODE8_AC_FAN, intent.fanSpeed.code, retain = true)
                sendAcCommand(fanSpeed = intent.fanSpeed)
            }
            HouseIntent.IncreaseTemp -> changeTemp(1)
            HouseIntent.DecreaseTemp -> changeTemp(-1)
        }
    }

    private fun changeTemp(delta: Int) {
        val newTemp = (state.value.acTemp + delta).coerceIn(AcCommandBuilder.MIN_TEMP, AcCommandBuilder.MAX_TEMP)
        publish(MqttTopics.NODE8_AC_TEMP, newTemp.toString(), retain = true)
        sendAcCommand(temp = newTemp)
    }

    private fun sendAcCommand(
        fanSpeed: AcFanSpeed = state.value.acFanSpeed,
        temp: Int = state.value.acTemp,
        mode: AcMode = state.value.acMode,
    ) {
        publish(MqttTopics.NODE8_AC_COMMAND, AcCommandBuilder.buildCommand(fanSpeed, temp, mode))
    }

    private fun publish(topic: String, payload: String, retain: Boolean = false) {
        viewModelScope.launch { repository.publish(topic, payload, retain = retain) }
    }
}
