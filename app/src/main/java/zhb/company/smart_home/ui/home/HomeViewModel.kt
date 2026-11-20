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
import zhb.company.smart_home.R
import zhb.company.smart_home.data.heating.CommandOutcome
import zhb.company.smart_home.data.heating.Node5Repository
import zhb.company.smart_home.data.heating.Node5Snapshot
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.MqttTopics
import zhb.company.smart_home.data.mqtt.NodeStatus
import zhb.company.smart_home.data.mqtt.OfflineReason
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.domain.GoodNightUseCase
import zhb.company.smart_home.domain.heating.formatTenths
import zhb.company.smart_home.domain.model.SmartHomeState
import zhb.company.smart_home.ui.format.Readings
import zhb.company.smart_home.ui.system.NodeLabels

class HomeViewModel(
    private val repository: SmartHomeRepository,
    private val goodNightUseCase: GoodNightUseCase,
    private val node5: Node5Repository,
) : ViewModel() {

    private data class UiExtras(val showDevicesOnDialog: Boolean = false, val showUnlockConfirm: Boolean = false)

    private val extras = MutableStateFlow(UiExtras())

    private val _effects = Channel<HomeEffect>()
    val effects = _effects.receiveAsFlow()

    val state: StateFlow<HomeState> = combine(
        repository.state,
        repository.connectionState,
        node5.snapshot,
        extras,
    ) { home, connection, heating, ui ->
        mapState(home, connection, heating, ui)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    private fun mapState(home: SmartHomeState, connection: MqttConnectionState, heating: Node5Snapshot, ui: UiExtras): HomeState {
        val connected = connection is MqttConnectionState.Connected
        val heatingSummary = heatingSummary(home, heating)
        val sprinklerRunning = home.sprinklerAll || home.sprinklerPower != -1
        return HomeState(
            connected = connected,
            alerts = alerts(home, connection, heating),
            sections = HomeLayout.sections(
                HomeSignals(
                    heatingMainSwitch = heatingSummary.mainSwitch,
                    sprinklerAuto = home.sprinklerTimerAuto,
                    sprinklerRunning = sprinklerRunning,
                    poolPumpOn = home.poolPump,
                    poolHeaterOn = home.poolHeating,
                    acOn = home.acOn,
                ),
            ),
            heating = heatingSummary,
            outdoorTemp = Readings.temperature(home.temperatureOutdoor),
            outdoorHumidity = Readings.percent(home.humidityOutdoor),
            groundTemp = Readings.temperature(home.temperatureLivingRoomDown),
            groundHumidity = Readings.percent(home.humidityLivingRoomDown),
            upperTemp = Readings.temperature(home.temperatureLivingRoomUp),
            upperHumidity = Readings.percent(home.humidityLivingRoomUp),
            poolTemp = Readings.temperature(home.temperaturePool),
            sprinklerAuto = home.sprinklerTimerAuto,
            sprinklerStartTime = home.sprinklerStartTime,
            sprinklerRunningZone = home.sprinklerPower.takeIf { it in 1..6 },
            ledStripOn = home.ledStripTerrace,
            fountainOn = home.fountainPump,
            poolPumpOn = home.poolPump,
            poolHeaterOn = home.poolHeating,
            acOn = home.acOn,
            acTemp = home.acTemp,
            acMode = home.acMode,
            yardNodeOnline = home.isNodeOnline(2),
            fountainNodeOnline = home.isNodeOnline(1),
            poolNodeOnline = home.isNodeOnline(6),
            doorNodeOnline = home.isNodeOnline(3),
            devicesOnList = home.listPowerDevice,
            showDevicesOnDialog = ui.showDevicesOnDialog,
            showUnlockConfirm = ui.showUnlockConfirm,
        )
    }

    private fun heatingSummary(home: SmartHomeState, heating: Node5Snapshot): HeatingSummary {
        val live = heating.live
        val zoneIds = heating.config?.zones ?: live?.zones?.keys?.sorted().orEmpty()
        return HeatingSummary(
            online = home.nodeStatus(heating.nodeId) == NodeStatus.ONLINE,
            mainSwitch = heating.config?.mainSwitch ?: live?.mainSwitch ?: false,
            pump = live?.pump ?: false,
            flowTemp = Readings.temperature(live?.flowTemp),
            zones = zoneIds.map { id ->
                val z = live?.zones?.get(id)
                ZoneSummary(
                    id = id,
                    roomTemp = Readings.temperature(z?.roomTemp),
                    target = z?.targetTenths?.let(::formatTenths),
                    demand = z?.demand ?: false,
                )
            },
        )
    }

    /** Most fundamental first: no connection hides everything else, it would all be stale. */
    private fun alerts(home: SmartHomeState, connection: MqttConnectionState, heating: Node5Snapshot): List<HomeAlert> {
        when (connection) {
            is MqttConnectionState.Reconnecting -> return listOf(HomeAlert.Reconnecting(connection.attempt))
            is MqttConnectionState.Disconnected, MqttConnectionState.Paused -> return listOf(HomeAlert.Reconnecting(0))
            MqttConnectionState.Connected -> Unit
            else -> return listOf(HomeAlert.NotConnected)
        }
        val offline = home.nodeHealth.values
            .filter { it.status == NodeStatus.OFFLINE && it.nodeId !in NodeLabels.NOT_IN_USE }
            .sortedBy { it.nodeId }
            .map { HomeAlert.NodeOffline(NodeLabels.of(it.nodeId), lastWill = it.offlineReason == OfflineReason.LAST_WILL) }
        val safe = heating.safeMode
            ?.takeIf { it.active && it.reason != "startup" }
            ?.let { HomeAlert.HeatingSafeMode(it.reason) }
        return offline + listOfNotNull(safe)
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.RequestUnlockFrontDoor -> extras.update { it.copy(showUnlockConfirm = true) }
            HomeIntent.DismissUnlockFrontDoor -> extras.update { it.copy(showUnlockConfirm = false) }
            HomeIntent.ConfirmUnlockFrontDoor -> {
                extras.update { it.copy(showUnlockConfirm = false) }
                publish(MqttTopics.NODE3_DOOR_CONTROL, "1")
            }
            is HomeIntent.ToggleLedStrip -> publish(MqttTopics.NODE2_LED_STRIP_CONTROL, intent.on.wire())
            is HomeIntent.ToggleFountain -> publish(MqttTopics.NODE1_FOUNTAIN_PUMP_CONTROL, intent.on.wire())
            is HomeIntent.TogglePoolPump -> publish(MqttTopics.NODE6_POOL_PUMP_CONTROL, intent.on.wire())
            is HomeIntent.TogglePoolHeater -> publish(MqttTopics.NODE6_POOL_HEATING_CONTROL, intent.on.wire())
            HomeIntent.TurnHeatingOn -> viewModelScope.launch {
                val message = when (node5.setMainSwitch(true)) {
                    is CommandOutcome.Accepted -> R.string.home_heating_turned_on
                    is CommandOutcome.Rejected -> R.string.home_heating_rejected
                    CommandOutcome.Timeout -> R.string.heating_msg_timeout
                    CommandOutcome.NotConnected -> R.string.heating_msg_not_connected
                }
                _effects.send(HomeEffect.ShowToast(message))
            }
            HomeIntent.ShowDevicesOnDialog -> extras.update { it.copy(showDevicesOnDialog = true) }
            HomeIntent.DismissDevicesOnDialog -> extras.update { it.copy(showDevicesOnDialog = false) }
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
