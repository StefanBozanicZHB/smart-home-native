package zhb.company.smart_home.ui.garden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.garden.Node1Repository
import zhb.company.smart_home.data.garden.Node1Snapshot
import zhb.company.smart_home.data.garden.Node2Repository
import zhb.company.smart_home.data.garden.Node6Repository
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.domain.model.Reading
import zhb.company.smart_home.domain.garden.IrrigationRules
import zhb.company.smart_home.domain.garden.Node2State
import zhb.company.smart_home.domain.garden.Node6State
import zhb.company.smart_home.domain.model.SmartHomeState
import zhb.company.smart_home.ui.command.awaitNodeState
import zhb.company.smart_home.ui.command.withSlowHint
import zhb.company.smart_home.ui.format.Readings

class GardenViewModel(
    private val repository: SmartHomeRepository,
    private val node1: Node1Repository,
    private val node2: Node2Repository,
    private val node6: Node6Repository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private data class UiExtras(
        val showLocationDialog: Boolean = false,
        /** Requested position per output while its command waits for the answer. */
        val pending: Map<GardenOutput, Boolean> = emptyMap(),
        val slow: Set<GardenOutput> = emptySet(),
        /** A sprinkler command (start or stop) is waiting for Node 1's answer; further ones are ignored. */
        val irrigationBusy: Boolean = false,
        val dialog: GardenDialog? = null,
        val message: GardenMessage? = null,
    )

    private val extras = MutableStateFlow(UiExtras())

    /** Node 2 lights and whether its firmware has schedules (`node2/config`). */
    private val lightsWithSchedules = combine(node2.state, node2.config) { lights, config -> lights to (config != null) }

    val state: StateFlow<GardenState> = combine(repository.state, node1.snapshot, lightsWithSchedules, node6.state, extras) { home, yard, lightsInfo, pool, ui ->
        mapState(home, yard, lightsInfo.first, lightsInfo.second, pool, ui)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GardenState())

    private fun mapState(home: SmartHomeState, yard: Node1Snapshot, lights: Node2State?, lightsScheduled: Boolean, pool: Node6State?, ui: UiExtras): GardenState {
        val config = yard.config
        val live = yard.state
        fun shown(output: GardenOutput, actual: Boolean) = ui.pending[output] ?: actual
        return GardenState(
            yardOnline = home.isNodeOnline(2),
            node1Online = home.isNodeOnline(1),
            node1Ready = config != null && live != null,
            poolOnline = home.isNodeOnline(6),
            temperature = Readings.temperature(home.temperatureOutdoor),
            temperatureStaleSince = home.staleSince(Reading.OUTDOOR_TEMPERATURE),
            outdoorSensorOk = lights?.sensorOk != false,
            ledStripOn = shown(GardenOutput.LED_STRIP, lights?.neon ?: false),
            ledStripLeftSeconds = lights?.neonLeftSeconds,
            ledStripToday = lights?.today?.get("neon"),
            bulbToday = lights?.today?.get("lamp"),
            fountainPumpToday = live?.fountain?.today?.get("pump"),
            fountainLightToday = live?.fountain?.today?.get("light"),
            schedulesSupported = lightsScheduled || config?.fountainSchedules != null,
            bulbOn = shown(GardenOutput.BULB, lights?.lamp ?: false),
            bulbLeftSeconds = lights?.lampLeftSeconds,
            fountainPumpOn = shown(GardenOutput.FOUNTAIN_PUMP, live?.fountain?.pump ?: false),
            fountainPumpLeftSeconds = live?.fountain?.pumpLeftSeconds,
            fountainLightOn = shown(GardenOutput.FOUNTAIN_LIGHT, live?.fountain?.light ?: false),
            fountainLightLeftSeconds = live?.fountain?.lightLeftSeconds,
            poolPumpOn = shown(GardenOutput.POOL_PUMP, pool?.pump ?: false),
            poolPumpLeftSeconds = pool?.pumpLeftSeconds,
            poolHeatingOn = pool?.heating?.let { shown(GardenOutput.POOL_HEATING, it) },
            poolHeatingLeftSeconds = pool?.heatingLeftSeconds,
            poolTemperature = Readings.temperature(home.temperaturePool),
            poolStaleSince = home.staleSince(Reading.POOL_TEMPERATURE),
            poolSensorOk = pool?.sensorOk != false,
            irrigation = IrrigationUi(
                auto = config?.auto ?: live?.auto ?: false,
                zoneMinutes = config?.zones ?: List(6) { 0 },
                pausedUntil = config?.pauseUntil?.takeIf { IrrigationRules.isPaused(it, today()) || live?.paused == true },
                next = live?.next,
                run = live?.run,
                lastRun = yard.lastRun,
                clockKnown = live == null || live.time != null,
                storage = live?.storage,
            ),
            pending = ui.pending.keys,
            slow = ui.slow,
            dialog = ui.dialog,
            message = ui.message,
            showLocationDialog = ui.showLocationDialog,
        )
    }

    fun onIntent(intent: GardenIntent) {
        when (intent) {
            is GardenIntent.SetLedStrip -> switch(GardenOutput.LED_STRIP, intent.on) { node2.setNeon(intent.on, intent.minutes) }
            is GardenIntent.SetBulb -> switch(GardenOutput.BULB, intent.on) { node2.setLamp(intent.on, intent.minutes) }
            is GardenIntent.SetPoolPump -> switch(GardenOutput.POOL_PUMP, intent.on) { node6.setPump(intent.on, intent.minutes) }
            is GardenIntent.SetPoolHeating -> switch(GardenOutput.POOL_HEATING, intent.on) { node6.setHeating(intent.on, intent.minutes) }
            is GardenIntent.SetFountainPump -> switch(GardenOutput.FOUNTAIN_PUMP, intent.on) { node1.setPump(intent.on, intent.minutes) }
            is GardenIntent.SetFountainLight -> switch(GardenOutput.FOUNTAIN_LIGHT, intent.on) { node1.setLight(intent.on, intent.minutes) }
            is GardenIntent.StartZone -> {
                val minutes = state.value.irrigation.zoneMinutes.getOrNull(intent.zone - 1) ?: 0
                if (minutes == 0) {
                    extras.update { it.copy(dialog = GardenDialog.AskMinutes(intent.zone)) }
                } else {
                    startOrConfirm(listOf(intent.zone), null)
                }
            }
            is GardenIntent.StartZoneFor -> {
                extras.update { it.copy(dialog = null) }
                startOrConfirm(listOf(intent.zone), intent.minutes)
            }
            GardenIntent.StartAll -> {
                if (state.value.irrigation.zoneMinutes.all { it == 0 }) {
                    extras.update { it.copy(message = GardenMessage.NoZones) }
                } else {
                    startOrConfirm(null, null)
                }
            }
            GardenIntent.ConfirmReplace -> {
                val pending = extras.value.dialog as? GardenDialog.ConfirmReplace ?: return
                extras.update { it.copy(dialog = null) }
                irrigationCommand(GardenMessage.Started) { node1.startRun(pending.zones, pending.minutes) }
            }
            GardenIntent.DismissDialog -> extras.update { it.copy(dialog = null) }
            GardenIntent.StopRun -> irrigationCommand(GardenMessage.Stopped) { node1.stopRun() }
            GardenIntent.MessageShown -> extras.update { it.copy(message = null) }
            GardenIntent.OpenLocationDialog -> extras.update { it.copy(showLocationDialog = true) }
            GardenIntent.DismissLocationDialog -> extras.update { it.copy(showLocationDialog = false) }
        }
    }

    /** A manual run replaces the active one on the node, so that needs a confirmation. */
    private fun startOrConfirm(zones: List<Int>?, minutes: Int?) {
        if (state.value.irrigation.run != null) {
            extras.update { it.copy(dialog = GardenDialog.ConfirmReplace(zones, minutes)) }
        } else {
            irrigationCommand(GardenMessage.Started) { node1.startRun(zones, minutes) }
        }
    }

    /**
     * Switches one output: it shows [on] at once and only that output waits for the answer. After
     * an accepted command it waits briefly for the node's new state, so the switch does not jump
     * back; on failure it returns to the real state and a message says why.
     */
    private fun switch(output: GardenOutput, on: Boolean, block: suspend () -> CommandOutcome) {
        if (output in extras.value.pending) return
        viewModelScope.launch {
            extras.update { it.copy(pending = it.pending + (output to on)) }
            val outcome = withSlowHint({ extras.update { it.copy(slow = it.slow + output) } }) {
                block().also { if (it is CommandOutcome.Accepted) awaitNodeState(actualStates()) { a -> actual(output, a) == on } }
            }
            val message = messageFor(outcome, success = null)
            extras.update { it.copy(pending = it.pending - output, slow = it.slow - output, message = message ?: it.message) }
        }
    }

    private data class Actual(val yard: Node1Snapshot, val lights: Node2State?, val pool: Node6State?)

    private fun actualStates() = combine(node1.snapshot, node2.state, node6.state, ::Actual)

    private fun actual(output: GardenOutput, a: Actual): Boolean? = when (output) {
        GardenOutput.FOUNTAIN_PUMP -> a.yard.state?.fountain?.pump
        GardenOutput.FOUNTAIN_LIGHT -> a.yard.state?.fountain?.light
        GardenOutput.LED_STRIP -> a.lights?.neon
        GardenOutput.BULB -> a.lights?.lamp
        GardenOutput.POOL_PUMP -> a.pool?.pump
        GardenOutput.POOL_HEATING -> a.pool?.heating
    }

    private fun irrigationCommand(success: GardenMessage, block: suspend () -> CommandOutcome) {
        if (extras.value.irrigationBusy) return
        viewModelScope.launch {
            extras.update { it.copy(irrigationBusy = true) }
            val message = messageFor(block(), success)
            extras.update { it.copy(irrigationBusy = false, message = message ?: it.message) }
        }
    }

    private fun messageFor(outcome: CommandOutcome, success: GardenMessage?): GardenMessage? = when (outcome) {
        is CommandOutcome.Accepted -> if (!outcome.saved) GardenMessage.NotSaved else success
        is CommandOutcome.Rejected -> GardenMessage.Rejected(outcome.error)
        CommandOutcome.Timeout -> GardenMessage.Timeout
        CommandOutcome.NotConnected -> GardenMessage.NotConnected
    }
}
