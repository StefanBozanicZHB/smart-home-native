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
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.data.heating.Node5Repository
import zhb.company.smart_home.data.heating.Node5Snapshot
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.domain.heating.Pipe
import zhb.company.smart_home.domain.heating.PipeAssignment
import zhb.company.smart_home.domain.heating.PipeSensorRules
import zhb.company.smart_home.ui.command.withSlowHint
import zhb.company.smart_home.ui.format.Readings

/** Pipe sensor assignment (node5/sensors, sensors/set): an admin screen next to heating. */
class PipeSensorsViewModel(
    repository: SmartHomeRepository,
    private val node5: Node5Repository,
) : ViewModel() {

    private data class Ui(
        val sending: Boolean = false,
        val slow: Boolean = false,
        val message: HeatingMessage? = null,
        val pickingFor: String? = null,
        val editingOffset: Pipe? = null,
        val offsetError: Boolean = false,
        val confirmRemove: Pipe? = null,
        val addressDetail: String? = null,
    )

    private val ui = MutableStateFlow(Ui())

    val state: StateFlow<PipeSensorsState> = combine(repository.connectionState, node5.snapshot, ui) { connection, snapshot, u ->
        mapState(connection is MqttConnectionState.Connected, snapshot, u)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PipeSensorsState())

    private fun mapState(connected: Boolean, snapshot: Node5Snapshot, u: Ui): PipeSensorsState {
        val sensors = snapshot.sensors
        val live = snapshot.live?.pipes
        val warmest = sensors?.bus?.filter { it.rawTemp != null }?.maxByOrNull { it.rawTemp!! }?.address
        return PipeSensorsState(
            nodeId = snapshot.nodeId,
            connected = connected,
            loading = sensors == null,
            fallback = sensors?.fallback ?: false,
            pipes = Pipe.entries.map { pipe ->
                val a = sensors?.assignment(pipe)
                PipeRowUi(
                    pipe = pipe,
                    address = a?.address,
                    offsetHundredths = a?.offsetHundredths ?: 0,
                    temperature = live?.get(pipe)?.let(Readings::temperature),
                    rawTemperature = a?.let { sensors.bus.firstOrNull { b -> b.address == it.address }?.rawTemp }?.let(Readings::temperature),
                    missingOnBus = a != null && !sensors.onBus(a.address),
                )
            },
            bus = sensors?.bus.orEmpty()
                .sortedByDescending { it.rawTemp ?: Double.NEGATIVE_INFINITY }
                .map { BusRowUi(it.address, Readings.temperature(it.rawTemp), it.pipe, warmest = sensors!!.bus.size > 1 && it.address == warmest) },
            slow = u.sending && u.slow,
            message = u.message,
            pickingFor = u.pickingFor,
            editingOffset = u.editingOffset,
            offsetError = u.offsetError,
            confirmRemove = u.confirmRemove,
            addressDetail = u.addressDetail,
        )
    }

    fun onIntent(intent: PipeSensorsIntent) {
        val sensors = node5.snapshot.value.sensors
        when (intent) {
            PipeSensorsIntent.Refresh -> viewModelScope.launch { node5.requestState() }
            is PipeSensorsIntent.PickSensor -> ui.update { it.copy(pickingFor = intent.address) }
            PipeSensorsIntent.DismissPicker -> ui.update { it.copy(pickingFor = null) }
            is PipeSensorsIntent.AssignTo -> {
                val address = ui.value.pickingFor ?: return
                ui.update { it.copy(pickingFor = null) }
                if (sensors != null) send(PipeSensorRules.assign(sensors, address, intent.pipe))
            }
            is PipeSensorsIntent.EditOffset -> ui.update { it.copy(editingOffset = intent.pipe, offsetError = false) }
            PipeSensorsIntent.DismissOffset -> ui.update { it.copy(editingOffset = null, offsetError = false) }
            is PipeSensorsIntent.SaveOffset -> {
                val pipe = ui.value.editingOffset ?: return
                val hundredths = PipeSensorRules.parseOffset(intent.text)
                if (hundredths == null) {
                    ui.update { it.copy(offsetError = true) }
                    return
                }
                ui.update { it.copy(editingOffset = null, offsetError = false) }
                sensors?.let { PipeSensorRules.setOffset(it, pipe, hundredths) }?.let(::send)
            }
            is PipeSensorsIntent.RequestRemove -> ui.update { it.copy(confirmRemove = intent.pipe) }
            PipeSensorsIntent.DismissRemove -> ui.update { it.copy(confirmRemove = null) }
            PipeSensorsIntent.ConfirmRemove -> {
                val pipe = ui.value.confirmRemove ?: return
                ui.update { it.copy(confirmRemove = null) }
                send(PipeSensorRules.remove(pipe))
            }
            is PipeSensorsIntent.ShowAddress -> ui.update { it.copy(addressDetail = intent.address) }
            PipeSensorsIntent.DismissAddress -> ui.update { it.copy(addressDetail = null) }
            PipeSensorsIntent.MessageShown -> ui.update { it.copy(message = null) }
        }
    }

    /**
     * Sends a sensors/set and reports the node's answer (matched by id) as a message. One at a
     * time: another change while it waits is ignored.
     */
    fun send(changes: Map<Pipe, PipeAssignment?>) {
        if (ui.value.sending) return
        ui.update { it.copy(sending = true) }
        viewModelScope.launch {
            val outcome = withSlowHint({ ui.update { it.copy(slow = true) } }) { node5.updateSensors(changes) }
            val message = when (outcome) {
                is CommandOutcome.Accepted -> HeatingMessage.Saved
                is CommandOutcome.Rejected -> HeatingMessage.Rejected(outcome.error)
                CommandOutcome.Timeout -> HeatingMessage.Timeout
                CommandOutcome.NotConnected -> HeatingMessage.NotConnected
            }
            ui.update { it.copy(sending = false, slow = false, message = message) }
        }
    }
}
