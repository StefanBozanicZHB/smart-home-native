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
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.data.door.Node3Repository
import zhb.company.smart_home.data.door.Node3Snapshot
import zhb.company.smart_home.data.garden.Node1Repository
import zhb.company.smart_home.data.garden.Node1Snapshot
import zhb.company.smart_home.data.garden.Node2Repository
import zhb.company.smart_home.data.garden.Node6Repository
import zhb.company.smart_home.data.heating.Node5Repository
import zhb.company.smart_home.data.heating.Node5Snapshot
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.NodeStatus
import zhb.company.smart_home.data.mqtt.OfflineReason
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.domain.GoodNightUseCase
import zhb.company.smart_home.domain.garden.Node1Devices
import zhb.company.smart_home.domain.garden.Node2Devices
import zhb.company.smart_home.domain.garden.Node2State
import zhb.company.smart_home.domain.garden.Node6Json
import zhb.company.smart_home.domain.garden.Node6State
import zhb.company.smart_home.domain.heating.HeatPumpStatus
import zhb.company.smart_home.domain.heating.formatTenths
import zhb.company.smart_home.domain.model.SmartHomeState
import zhb.company.smart_home.ui.format.Readings
import zhb.company.smart_home.ui.system.NodeLabels
import zhb.company.smart_home.ui.command.awaitNodeState
import zhb.company.smart_home.ui.command.withSlowHint

class HomeViewModel(
    private val repository: SmartHomeRepository,
    private val goodNightUseCase: GoodNightUseCase,
    private val node5: Node5Repository,
    private val node1: Node1Repository,
    private val node2: Node2Repository,
    private val node3: Node3Repository,
    private val node6: Node6Repository,
) : ViewModel() {

    private data class UiExtras(
        val showDevicesOnDialog: Boolean = false,
        val showUnlockConfirm: Boolean = false,
        /** Requested position per tile while its command waits for the answer. */
        val pending: Map<HomeTile, Boolean> = emptyMap(),
        val slow: Set<HomeTile> = emptySet(),
    )

    private val extras = MutableStateFlow(UiExtras())

    private val _effects = Channel<HomeEffect>()
    val effects = _effects.receiveAsFlow()

    val state: StateFlow<HomeState> = combine(
        repository.state,
        repository.connectionState,
        node5.snapshot,
        node1.snapshot,
        node2.state,
        node3.snapshot,
        node6.state,
        extras,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        mapState(
            values[0] as SmartHomeState,
            values[1] as MqttConnectionState,
            values[2] as Node5Snapshot,
            values[3] as Node1Snapshot,
            values[4] as Node2State?,
            values[5] as Node3Snapshot,
            values[6] as Node6State?,
            values[7] as UiExtras,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    private fun mapState(
        home: SmartHomeState,
        connection: MqttConnectionState,
        heating: Node5Snapshot,
        yard: Node1Snapshot,
        lights: Node2State?,
        door: Node3Snapshot,
        pool: Node6State?,
        ui: UiExtras,
    ): HomeState {
        val connected = connection is MqttConnectionState.Connected
        val heatingSummary = heatingSummary(home, connected, heating)
        val yardState = yard.state
        val sprinklerAuto = yard.config?.auto ?: yardState?.auto ?: false
        val sprinklerRunning = yardState?.run != null
        return HomeState(
            connected = connected,
            alerts = alerts(home, connection, heating),
            sections = HomeLayout.sections(
                HomeSignals(
                    heatingMainSwitch = heatingSummary.mainSwitch,
                    sprinklerAuto = sprinklerAuto,
                    sprinklerRunning = sprinklerRunning,
                ),
            ),
            heating = heatingSummary,
            outdoorTemp = Readings.temperature(home.temperatureOutdoor),
            groundTemp = Readings.temperature(home.temperatureLivingRoomDown),
            groundHumidity = Readings.percent(home.humidityLivingRoomDown),
            upperTemp = Readings.temperature(home.temperatureLivingRoomUp),
            upperHumidity = Readings.percent(home.humidityLivingRoomUp),
            poolTemp = Readings.temperature(home.temperaturePool),
            staleSince = home.staleReadings.associateWith { home.readingAt.getValue(it) },
            sprinklerAuto = sprinklerAuto,
            sprinklerStartTime = yardState?.next?.time ?: "--:--",
            sprinklerRunningZone = yardState?.run?.zone,
            ledStripOn = ui.pending[HomeTile.LED_STRIP] ?: lights?.neon ?: false,
            fountainOn = ui.pending[HomeTile.FOUNTAIN] ?: yardState?.fountain?.pump ?: false,
            poolPumpOn = ui.pending[HomeTile.POOL_PUMP] ?: pool?.pump ?: false,
            poolHeatingOn = pool?.heating?.let { ui.pending[HomeTile.POOL_HEATING] ?: it },
            slowTiles = ui.slow,
            yardNodeOnline = home.isNodeOnline(2),
            fountainNodeOnline = home.isNodeOnline(1),
            poolNodeOnline = home.isNodeOnline(6),
            doorNodeOnline = home.isNodeOnline(3),
            frontDoor = FrontDoorUi(
                open = door.state?.lockOpen ?: false,
                readerOk = door.state?.readerOk != false,
                lastAccess = door.lastAccess,
                lastAccessSecondsAgo = door.lastAccess?.secondsBefore(door.state),
            ),
            devicesOnList = Node1Devices.onLabels(yardState) + Node2Devices.onLabels(lights) + Node6Json.onLabels(pool),
            showDevicesOnDialog = ui.showDevicesOnDialog,
            showUnlockConfirm = ui.showUnlockConfirm,
        )
    }

    private fun heatingSummary(home: SmartHomeState, connected: Boolean, heating: Node5Snapshot): HeatingSummary {
        val live = heating.live
        val zoneIds = heating.config?.zones ?: live?.zones?.keys?.sorted().orEmpty()
        return HeatingSummary(
            online = home.nodeStatus(heating.nodeId) == NodeStatus.ONLINE,
            status = HeatPumpStatus.from(connected, heating.config, live, heating.pump, heating.safeMode),
            mainSwitch = heating.config?.mainSwitch ?: live?.mainSwitch ?: false,
            pump = live?.pump ?: false,
            flowTemp = Readings.temperature(live?.pipes?.supply),
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
            .filter { it.status == NodeStatus.OFFLINE && NodeLabels.isActive(it.nodeId) }
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
                viewModelScope.launch {
                    val message = when (node3.openDoor()) {
                        is CommandOutcome.Accepted -> R.string.home_door_opened
                        is CommandOutcome.Rejected -> R.string.garden_msg_failed
                        CommandOutcome.Timeout -> R.string.heating_msg_timeout
                        CommandOutcome.NotConnected -> R.string.heating_msg_not_connected
                    }
                    _effects.send(HomeEffect.ShowToast(message))
                }
            }
            is HomeIntent.ToggleLedStrip -> switch(HomeTile.LED_STRIP, intent.on) { node2.setNeon(intent.on) }
            is HomeIntent.ToggleFountain -> switch(HomeTile.FOUNTAIN, intent.on) { node1.setPump(intent.on) }
            is HomeIntent.TogglePoolPump -> switch(HomeTile.POOL_PUMP, intent.on) { node6.setPump(intent.on) }
            is HomeIntent.TogglePoolHeating -> switch(HomeTile.POOL_HEATING, intent.on) { node6.setHeating(intent.on) }
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
                val result = goodNightUseCase()
                _effects.send(
                    when {
                        result.alreadyOff -> HomeEffect.ShowToast(R.string.goodnight_toast_already_off)
                        result.failed.isEmpty() -> HomeEffect.ShowToast(R.string.goodnight_toast_off)
                        else -> HomeEffect.GoodnightIncomplete(result.failed)
                    },
                )
            }
        }
    }

    /**
     * The tile shows [on] at once; only that tile waits for the answer (a second tap meanwhile is
     * ignored). On failure it returns to the real state with a message.
     */
    private fun switch(tile: HomeTile, on: Boolean, send: suspend () -> CommandOutcome) {
        if (tile in extras.value.pending) return
        extras.update { it.copy(pending = it.pending + (tile to on)) }
        viewModelScope.launch {
            val outcome = withSlowHint({ extras.update { it.copy(slow = it.slow + tile) } }) {
                send().also { if (it is CommandOutcome.Accepted) awaitNodeState(actualStates()) { a -> actual(tile, a) == on } }
            }
            extras.update { it.copy(pending = it.pending - tile, slow = it.slow - tile) }
            if (outcome !is CommandOutcome.Accepted) _effects.send(HomeEffect.ShowToast(R.string.garden_msg_failed))
        }
    }

    private data class Actual(val yard: Node1Snapshot, val lights: Node2State?, val pool: Node6State?)

    private fun actualStates() = combine(node1.snapshot, node2.state, node6.state, ::Actual)

    private fun actual(tile: HomeTile, a: Actual): Boolean? = when (tile) {
        HomeTile.LED_STRIP -> a.lights?.neon
        HomeTile.FOUNTAIN -> a.yard.state?.fountain?.pump
        HomeTile.POOL_PUMP -> a.pool?.pump
        HomeTile.POOL_HEATING -> a.pool?.heating
    }
}
