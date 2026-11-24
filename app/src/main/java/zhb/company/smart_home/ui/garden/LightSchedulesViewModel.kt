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
import zhb.company.smart_home.data.garden.Node1Repository
import zhb.company.smart_home.data.garden.Node1Snapshot
import zhb.company.smart_home.data.garden.Node2Repository
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.domain.garden.Node2Config
import zhb.company.smart_home.domain.garden.Node2State
import zhb.company.smart_home.domain.garden.Weekday
import zhb.company.smart_home.domain.model.SmartHomeState
import zhb.company.smart_home.domain.schedule.OutputSchedule
import zhb.company.smart_home.domain.schedule.ScheduleProblem
import zhb.company.smart_home.domain.schedule.ScheduleRules
import zhb.company.smart_home.domain.schedule.ScheduleTime
import zhb.company.smart_home.domain.schedule.TodayWindow
import zhb.company.smart_home.ui.command.awaitNodeState
import zhb.company.smart_home.ui.command.withSlowHint

/** The nodes with scheduled outputs. */
enum class ScheduleNode { NODE1, NODE2 }

/** One scheduled output: its node and its name in that node's config ("neon", "pump", ...). */
data class OutputId(val node: ScheduleNode, val name: String)

data class OutputScheduleUi(
    val id: OutputId,
    val nodeOnline: Boolean,
    /** What the editor shows: the draft, else the node's schedule; null = manual only. */
    val schedule: OutputSchedule?,
    /** The node's schedule. */
    val saved: OutputSchedule?,
    val dirty: Boolean,
    val problem: ScheduleProblem?,
    /** Today's window from the node's state; [todayKnown] false until the state has arrived. */
    val today: TodayWindow?,
    val todayKnown: Boolean,
    val saving: Boolean,
    /** The answer is late (over a second). */
    val slow: Boolean,
)

data class LightSchedulesState(
    val connected: Boolean = false,
    /** Outputs of the nodes that announced schedule support, in the nodes' order. */
    val outputs: List<OutputScheduleUi> = emptyList(),
    val message: ScheduleMessage? = null,
    val showLeaveConfirm: Boolean = false,
) {
    val anyDirty: Boolean get() = outputs.any { it.dirty }
}

sealed interface ScheduleMessage {
    data object Saved : ScheduleMessage
    data object Removed : ScheduleMessage
    data object NotSaved : ScheduleMessage
    data class Rejected(val error: String) : ScheduleMessage
    data object Timeout : ScheduleMessage
    data object NotConnected : ScheduleMessage
}

sealed interface ScheduleIntent {
    data class SetScheduled(val id: OutputId, val scheduled: Boolean) : ScheduleIntent
    data class SetOn(val id: OutputId, val time: ScheduleTime) : ScheduleIntent
    data class SetOff(val id: OutputId, val time: ScheduleTime) : ScheduleIntent
    data class ToggleDay(val id: OutputId, val day: Weekday) : ScheduleIntent
    data class Save(val id: OutputId) : ScheduleIntent
    data class Discard(val id: OutputId) : ScheduleIntent
    data object MessageShown : ScheduleIntent
    data object RequestLeave : ScheduleIntent
    data object KeepEditing : ScheduleIntent
    data object DiscardAll : ScheduleIntent
}

/**
 * Daily schedules of the Node 2 lights and the Node 1 fountain. The outputs come from the nodes'
 * configs (`node2/config` `schedules`, `node1/config` `fountain`); a node whose firmware has no
 * schedules publishes neither, so it is not shown. Each output is edited as a draft and saved on
 * its own (`<output>/schedule/set`).
 */
class LightSchedulesViewModel(
    private val mqtt: SmartHomeRepository,
    private val node1: Node1Repository,
    private val node2: Node2Repository,
) : ViewModel() {

    /** [schedule] null = the user picked "manual" (a draft that removes the schedule). */
    private data class Draft(val schedule: OutputSchedule?)

    private data class Ui(
        val drafts: Map<OutputId, Draft> = emptyMap(),
        val saving: Set<OutputId> = emptySet(),
        val slow: Set<OutputId> = emptySet(),
        val message: ScheduleMessage? = null,
        val showLeaveConfirm: Boolean = false,
    )

    private val ui = MutableStateFlow(Ui())

    private data class Nodes(val home: SmartHomeState, val yard: Node1Snapshot, val lightsConfig: Node2Config?, val lights: Node2State?)

    private val nodes = combine(mqtt.state, node1.snapshot, node2.config, node2.state, ::Nodes)

    val state: StateFlow<LightSchedulesState> = combine(mqtt.connectionState, nodes, ui) { connection, n, u ->
        mapState(connection is MqttConnectionState.Connected, n, u)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LightSchedulesState())

    private fun saved(n: Nodes): Map<OutputId, OutputSchedule?> = buildMap {
        n.lightsConfig?.schedules?.forEach { (name, s) -> put(OutputId(ScheduleNode.NODE2, name), s) }
        n.yard.config?.fountainSchedules?.forEach { (name, s) -> put(OutputId(ScheduleNode.NODE1, name), s) }
    }

    private fun mapState(connected: Boolean, n: Nodes, u: Ui): LightSchedulesState {
        val outputs = saved(n).map { (id, saved) ->
            val draft = u.drafts[id]
            val shown = if (draft != null) draft.schedule else saved
            val todayMap = when (id.node) {
                ScheduleNode.NODE2 -> n.lights?.today
                ScheduleNode.NODE1 -> n.yard.state?.fountain?.today
            }
            OutputScheduleUi(
                id = id,
                nodeOnline = n.home.isNodeOnline(if (id.node == ScheduleNode.NODE1) 1 else 2),
                schedule = shown,
                saved = saved,
                dirty = draft != null && draft.schedule != saved,
                problem = shown?.let(ScheduleRules::validate),
                today = todayMap?.get(id.name),
                todayKnown = todayMap != null,
                saving = id in u.saving,
                slow = id in u.slow,
            )
        }
        return LightSchedulesState(connected, outputs, u.message, u.showLeaveConfirm)
    }

    private fun output(id: OutputId) = state.value.outputs.firstOrNull { it.id == id }

    /** Changes the schedule the editor shows for [id], starting a draft. */
    private fun edit(id: OutputId, change: (OutputSchedule) -> OutputSchedule) {
        val current = output(id)?.schedule ?: return
        ui.update { it.copy(drafts = it.drafts + (id to Draft(change(current)))) }
    }

    fun onIntent(intent: ScheduleIntent) {
        when (intent) {
            is ScheduleIntent.SetScheduled -> {
                val out = output(intent.id) ?: return
                val schedule = if (intent.scheduled) out.saved ?: OutputSchedule.DEFAULT else null
                ui.update { it.copy(drafts = it.drafts + (intent.id to Draft(schedule))) }
            }
            is ScheduleIntent.SetOn -> edit(intent.id) { it.copy(on = intent.time) }
            is ScheduleIntent.SetOff -> edit(intent.id) { it.copy(off = intent.time) }
            is ScheduleIntent.ToggleDay -> edit(intent.id) { s ->
                s.copy(days = if (intent.day in s.days) s.days - intent.day else s.days + intent.day)
            }
            is ScheduleIntent.Save -> save(intent.id)
            is ScheduleIntent.Discard -> ui.update { it.copy(drafts = it.drafts - intent.id) }
            ScheduleIntent.MessageShown -> ui.update { it.copy(message = null) }
            ScheduleIntent.RequestLeave -> ui.update { it.copy(showLeaveConfirm = true) }
            ScheduleIntent.KeepEditing -> ui.update { it.copy(showLeaveConfirm = false) }
            ScheduleIntent.DiscardAll -> ui.update { it.copy(drafts = emptyMap(), showLeaveConfirm = false) }
        }
    }

    private fun save(id: OutputId) {
        val out = output(id) ?: return
        if (!out.dirty || out.problem != null || out.saving || !state.value.connected) return
        val schedule = out.schedule
        ui.update { it.copy(saving = it.saving + id) }
        viewModelScope.launch {
            val outcome = withSlowHint({ ui.update { it.copy(slow = it.slow + id) } }) {
                send(id, schedule).also { result ->
                    // The draft stays until the node's config shows the change, so the editor does not flash back.
                    if (result is CommandOutcome.Accepted) awaitNodeState(nodes) { n -> saved(n)[id] == schedule }
                }
            }
            ui.update { u ->
                val message = when (outcome) {
                    is CommandOutcome.Accepted -> when {
                        !outcome.saved -> ScheduleMessage.NotSaved
                        schedule == null -> ScheduleMessage.Removed
                        else -> ScheduleMessage.Saved
                    }
                    is CommandOutcome.Rejected -> ScheduleMessage.Rejected(outcome.error)
                    CommandOutcome.Timeout -> ScheduleMessage.Timeout
                    CommandOutcome.NotConnected -> ScheduleMessage.NotConnected
                }
                u.copy(
                    drafts = if (outcome is CommandOutcome.Accepted) u.drafts - id else u.drafts,
                    saving = u.saving - id,
                    slow = u.slow - id,
                    message = message,
                )
            }
        }
    }

    private suspend fun send(id: OutputId, schedule: OutputSchedule?): CommandOutcome = when (id.node) {
        ScheduleNode.NODE1 -> node1.setFountainSchedule(id.name, schedule)
        ScheduleNode.NODE2 -> node2.setSchedule(id.name, schedule)
    }
}
