package zhb.company.smart_home.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.local.AppPreferences
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.MqttTopics
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.GoodNightUseCase
import zhb.company.smart_home.domain.model.SmartHomeState

class HomeViewModel(
    private val repository: SmartHomeRepository,
    private val preferences: AppPreferences,
    private val goodNightUseCase: GoodNightUseCase,
) : ViewModel() {

    private val _uiExtras = MutableStateFlow(UiExtras())
    private data class UiExtras(val showDevicesOnDialog: Boolean = false, val plusMenuExpanded: Boolean = false)

    private val _effects = Channel<HomeEffect>()
    val effects = _effects.receiveAsFlow()

    val state: StateFlow<HomeState> = combine(
        repository.state,
        repository.connectionState,
        preferences.isDarkTheme,
        _uiExtras,
    ) { home, connection, darkTheme, extras ->
        mapState(home, connection is MqttConnectionState.Connected, darkTheme, extras)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    private fun mapState(home: SmartHomeState, connected: Boolean, darkTheme: Boolean, extras: UiExtras): HomeState = HomeState(
        darkTheme = darkTheme,
        connected = connected,
        mainDoorOnline = home.isNodeOnline(3),
        mainDoorLocked = !home.mainDoor,
        tempUp = home.temperatureLivingRoomUp,
        tempDown = home.temperatureLivingRoomDown,
        tempOutdoor = home.temperatureOutdoor,
        sprinklerAuto = home.sprinklerTimerAuto,
        sprinklerStartTime = home.sprinklerStartTime,
        devicesOnCount = home.listPowerDevice.size,
        devicesOnList = home.listPowerDevice,
        ledStripOn = home.ledStripTerrace,
        sprinklersOn = home.sprinklerAll || home.sprinklerPower != -1,
        fountainOn = home.fountainPump,
        poolPumpOn = home.poolPump,
        poolHeaterOn = home.poolHeating,
        gateOn = home.mainDoor,
        garden = RoomCardState("garden", home.temperatureOutdoor, home.humidityOutdoor, home.isNodeOnline(2)),
        firstFloor = RoomCardState("firstFloor", home.temperatureLivingRoomDown, home.humidityLivingRoomDown, home.isNodeOnline(4)),
        secondFloor = RoomCardState("secondFloor", home.temperatureLivingRoomUp, home.humidityLivingRoomUp, home.isNodeOnline(8)),
        pool = RoomCardState("pool", home.temperaturePool, "--", home.isNodeOnline(6)),
        shed = RoomCardState("shed", "--", "--", home.isNodeOnline(7)),
        heating = RoomCardState(
            title = "heating",
            temperature = home.temperatureTubeHeating,
            humidity = "--",
            online = home.isNodeOnline(5),
            statusBadges = listOf(
                RoomStatusBadge(if (home.isTempRelay) "Radi" else "Ne radi", home.isTempRelay),
                RoomStatusBadge(if (home.isMainSwitch) "Uključeno" else "Isključeno", home.isMainSwitch),
            ),
        ),
        showDevicesOnDialog = extras.showDevicesOnDialog,
        plusMenuExpanded = extras.plusMenuExpanded,
    )

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.UnlockFrontDoor -> publish(MqttTopics.NODE3_DOOR_CONTROL, "1")
            is HomeIntent.ToggleLedStrip -> publish(MqttTopics.NODE2_LED_STRIP_CONTROL, intent.on.wire())
            is HomeIntent.ToggleSprinklers -> publish(MqttTopics.NODE1_ALL_SPRINKLERS_CONTROL, intent.on.wire())
            is HomeIntent.ToggleFountain -> publish(MqttTopics.NODE1_FOUNTAIN_PUMP_CONTROL, intent.on.wire())
            is HomeIntent.TogglePoolPump -> publish(MqttTopics.NODE6_POOL_PUMP_CONTROL, intent.on.wire())
            is HomeIntent.TogglePoolHeater -> publish(MqttTopics.NODE6_POOL_HEATING_CONTROL, intent.on.wire())
            is HomeIntent.ToggleGate -> publish(MqttTopics.NODE3_DOOR_CONTROL, intent.on.wire())
            HomeIntent.ShowDevicesOnDialog -> _uiExtras.update { it.copy(showDevicesOnDialog = true) }
            HomeIntent.DismissDevicesOnDialog -> _uiExtras.update { it.copy(showDevicesOnDialog = false) }
            HomeIntent.TogglePlusMenu -> _uiExtras.update { it.copy(plusMenuExpanded = !it.plusMenuExpanded) }
            HomeIntent.Goodnight -> viewModelScope.launch {
                val turnedOff = goodNightUseCase()
                _effects.send(HomeEffect.ShowToast(if (turnedOff) R.string.goodnight_toast_off else R.string.goodnight_toast_already_off))
            }
        }
    }

    private fun Boolean.wire() = if (this) "1" else "0"

    private fun publish(topic: String, payload: String) {
        viewModelScope.launch { repository.publish(topic, payload) }
    }
}
