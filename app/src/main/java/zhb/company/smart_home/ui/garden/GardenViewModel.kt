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
import zhb.company.smart_home.domain.garden.IrrigationRules
import zhb.company.smart_home.domain.garden.Node2State
import zhb.company.smart_home.domain.garden.Node6State
import zhb.company.smart_home.domain.model.SmartHomeState
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
        val busy: Boolean = false,
        val dialog: GardenDialog? = null,
        val message: GardenMessage? = null,
    )

    private val extras = MutableStateFlow(UiExtras())

    val state: StateFlow<GardenState> = combine(repository.state, node1.snapshot, node2.state, node6.state, extras) { home, yard, lights, pool, ui ->
        mapState(home, yard, lights, pool, ui)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GardenState())

    private fun mapState(home: SmartHomeState, yard: Node1Snapshot, lights: Node2State?, pool: Node6State?, ui: UiExtras): GardenState {
        val config = yard.config
        val live = yard.state
        return GardenState(
            yardOnline = home.isNodeOnline(2),
            node1Online = home.isNodeOnline(1),
            node1Ready = config != null && live != null,
            poolOnline = home.isNodeOnline(6),
            temperature = Readings.temperature(home.temperatureOutdoor),
            outdoorSensorOk = lights?.sensorOk != false,
            ledStripOn = lights?.neon ?: false,
            ledStripLeftSeconds = lights?.neonLeftSeconds,
            bulbOn = lights?.lamp ?: false,
            bulbLeftSeconds = lights?.lampLeftSeconds,
            fountainPumpOn = live?.fountain?.pump ?: false,
            fountainPumpLeftSeconds = live?.fountain?.pumpLeftSeconds,
            fountainLightOn = live?.fountain?.light ?: false,
            fountainLightLeftSeconds = live?.fountain?.lightLeftSeconds,
            poolPumpOn = pool?.pump ?: false,
            poolPumpLeftSeconds = pool?.pumpLeftSeconds,
            poolHeatingOn = pool?.heating,
            poolHeatingLeftSeconds = pool?.heatingLeftSeconds,
            poolTemperature = Readings.temperature(home.temperaturePool),
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
            busy = ui.busy,
            dialog = ui.dialog,
            message = ui.message,
            showLocationDialog = ui.showLocationDialog,
        )
    }

    fun onIntent(intent: GardenIntent) {
        when (intent) {
            is GardenIntent.SetLedStrip -> command(null) { node2.setNeon(intent.on, intent.minutes) }
            is GardenIntent.SetBulb -> command(null) { node2.setLamp(intent.on, intent.minutes) }
            is GardenIntent.SetPoolPump -> command(null) { node6.setPump(intent.on, intent.minutes) }
            is GardenIntent.SetPoolHeating -> command(null) { node6.setHeating(intent.on, intent.minutes) }
            is GardenIntent.SetFountainPump -> command(null) { node1.setPump(intent.on, intent.minutes) }
            is GardenIntent.SetFountainLight -> command(null) { node1.setLight(intent.on, intent.minutes) }
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
                command(GardenMessage.Started) { node1.startRun(pending.zones, pending.minutes) }
            }
            GardenIntent.DismissDialog -> extras.update { it.copy(dialog = null) }
            GardenIntent.StopRun -> command(GardenMessage.Stopped) { node1.stopRun() }
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
            command(GardenMessage.Started) { node1.startRun(zones, minutes) }
        }
    }

    private fun command(success: GardenMessage?, block: suspend () -> CommandOutcome) {
        viewModelScope.launch {
            extras.update { it.copy(busy = true) }
            val message = when (val outcome = block()) {
                is CommandOutcome.Accepted -> if (!outcome.saved) GardenMessage.NotSaved else success
                is CommandOutcome.Rejected -> GardenMessage.Rejected(outcome.error)
                CommandOutcome.Timeout -> GardenMessage.Timeout
                CommandOutcome.NotConnected -> GardenMessage.NotConnected
            }
            extras.update { it.copy(busy = false, message = message ?: it.message) }
        }
    }
}
