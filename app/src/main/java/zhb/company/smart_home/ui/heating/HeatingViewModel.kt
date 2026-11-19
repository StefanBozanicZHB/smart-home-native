package zhb.company.smart_home.ui.heating

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
import zhb.company.smart_home.domain.HeatingScheduleCodec
import zhb.company.smart_home.domain.model.HeatingSegment
import zhb.company.smart_home.domain.model.HeatingType
import zhb.company.smart_home.domain.model.SmartHomeState

class HeatingViewModel(private val repository: SmartHomeRepository) : ViewModel() {

    private data class UiExtras(
        val showSettingsDialog: Boolean = false,
        val plusMenuExpanded: Boolean = false,
        val pendingWorkday: List<HeatingSegment>? = null,
        val pendingWeekend: List<HeatingSegment>? = null,
    )

    private val _uiExtras = MutableStateFlow(UiExtras())

    val state: StateFlow<HeatingState> = combine(repository.state, _uiExtras) { home, extras ->
        mapState(home, extras)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HeatingState())

    private fun mapState(home: SmartHomeState, extras: UiExtras): HeatingState = HeatingState(
        online = home.isNodeOnline(5),
        tubeTemp = home.temperatureTubeHeating,
        outdoorTemp = home.temperatureOutdoor,
        tempUp = home.temperatureLivingRoomUp,
        humidityUp = home.humidityLivingRoomUp,
        tempDown = home.temperatureLivingRoomDown,
        humidityDown = home.humidityLivingRoomDown,
        pelletsLevelPercent = home.percentPelletsTank,
        isTempRelay = home.isTempRelay,
        isMainSwitch = home.isMainSwitch,
        heatingType = when {
            home.isGas -> HeatingType.GAS
            home.isElectro -> HeatingType.ELECTRIC
            else -> HeatingType.PELLETS
        },
        workdaySegments = extras.pendingWorkday ?: home.workdaySegments,
        weekendSegments = extras.pendingWeekend ?: home.weekendSegments,
        upStep = home.upStep,
        downStep = home.downStep,
        alarmLimitPercent = home.percentAlarmLimit,
        showSettingsDialog = extras.showSettingsDialog,
        plusMenuExpanded = extras.plusMenuExpanded,
    )

    fun onIntent(intent: HeatingIntent) {
        when (intent) {
            is HeatingIntent.ToggleMainSwitch -> publish(MqttTopics.NODE5_MAIN_SWITCH_CONTROL, intent.on.wire())
            is HeatingIntent.SelectHeatingType -> publish(
                MqttTopics.NODE5_HEATING_TYPE_CONTROL,
                when (intent.type) { HeatingType.PELLETS -> "1"; HeatingType.GAS -> "2"; HeatingType.ELECTRIC -> "0" },
            )
            HeatingIntent.OpenSettingsDialog -> _uiExtras.update { it.copy(showSettingsDialog = true) }
            HeatingIntent.DismissSettingsDialog -> _uiExtras.update { it.copy(showSettingsDialog = false) }
            is HeatingIntent.SetUpperHysteresis -> publish(MqttTopics.NODE5_UPPER_LIMIT_CONTROL, (intent.value * 10).toInt().toString())
            is HeatingIntent.SetLowerHysteresis -> publish(MqttTopics.NODE5_LOWER_LIMIT_CONTROL, (intent.value * 10).toInt().toString())
            is HeatingIntent.SetAlarmLimit -> publish(MqttTopics.NODE5_ALARM_LEVEL_CONTROL, intent.percent.toString().padStart(2, '0'))
            HeatingIntent.SetFullLevel -> publish(MqttTopics.NODE5_SET_FULL_LEVEL, "1")
            HeatingIntent.SetEmptyLevel -> publish(MqttTopics.NODE5_SET_EMPTY_LEVEL, "1")
            is HeatingIntent.SetWorkdaySegments -> _uiExtras.update { it.copy(pendingWorkday = intent.segments) }
            HeatingIntent.SaveWorkdaySchedule -> {
                val segments = _uiExtras.value.pendingWorkday ?: state.value.workdaySegments
                publish(MqttTopics.NODE5_SCHEDULE_CONTROL, HeatingScheduleCodec.encode(isWorkday = true, segments = segments))
                _uiExtras.update { it.copy(pendingWorkday = null) }
            }
            is HeatingIntent.SetWeekendSegments -> _uiExtras.update { it.copy(pendingWeekend = intent.segments) }
            HeatingIntent.SaveWeekendSchedule -> {
                val segments = _uiExtras.value.pendingWeekend ?: state.value.weekendSegments
                publish(MqttTopics.NODE5_SCHEDULE_CONTROL, HeatingScheduleCodec.encode(isWorkday = false, segments = segments))
                _uiExtras.update { it.copy(pendingWeekend = null) }
            }
            HeatingIntent.TogglePlusMenu -> _uiExtras.update { it.copy(plusMenuExpanded = !it.plusMenuExpanded) }
        }
    }

    private fun Boolean.wire() = if (this) "1" else "0"

    private fun publish(topic: String, payload: String) {
        viewModelScope.launch { repository.publish(topic, payload) }
    }
}
