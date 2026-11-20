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
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.domain.garden.IrrigationDraft
import zhb.company.smart_home.domain.garden.IrrigationProblem
import zhb.company.smart_home.domain.garden.IrrigationRules
import zhb.company.smart_home.domain.garden.NextStart
import zhb.company.smart_home.domain.garden.Node1ConfigChange
import zhb.company.smart_home.domain.garden.Node1Limits
import zhb.company.smart_home.domain.garden.PauseUntil
import zhb.company.smart_home.domain.garden.Soak
import zhb.company.smart_home.domain.garden.Weekday

data class IrrigationPlanState(
    val connected: Boolean = false,
    /** Null until `node1/config` has arrived. */
    val draft: IrrigationDraft? = null,
    val limits: Node1Limits = Node1Limits(),
    val dirty: Boolean = false,
    val problems: List<IrrigationProblem> = emptyList(),
    val saving: Boolean = false,
    /** Active pause (rain), "YYYY-MM-DD"; applied at once, not part of the draft. */
    val pausedUntil: String? = null,
    val next: NextStart? = null,
    val message: PlanMessage? = null,
    val showLeaveConfirm: Boolean = false,
) {
    val canSave: Boolean get() = connected && dirty && problems.isEmpty() && !saving
}

sealed interface PlanMessage {
    data object Saved : PlanMessage
    data object NotSaved : PlanMessage
    data object Paused : PlanMessage
    data object PauseCleared : PlanMessage
    data class Rejected(val error: String) : PlanMessage
    data object Timeout : PlanMessage
    data object NotConnected : PlanMessage
}

sealed interface PlanIntent {
    data class SetAuto(val on: Boolean) : PlanIntent
    data class ToggleDay(val day: Weekday) : PlanIntent
    data object EveryDay : PlanIntent
    data object AddStart : PlanIntent
    data class ChangeStart(val index: Int, val time: String) : PlanIntent
    data class RemoveStart(val index: Int) : PlanIntent
    data class SetZone(val zone: Int, val minutes: Int) : PlanIntent
    data class SetSoakEnabled(val on: Boolean) : PlanIntent
    data class SetSoak(val soak: Soak) : PlanIntent
    data object Save : PlanIntent
    data object Discard : PlanIntent
    /** Pause scheduled watering for [days] days starting today (1 = today only). */
    data class PauseFor(val days: Int) : PlanIntent
    data object ClearPause : PlanIntent
    data object MessageShown : PlanIntent
    data object RequestLeave : PlanIntent
    data object KeepEditing : PlanIntent
}

/** The watering plan on Node 1: edited as a draft, saved as one `config/set` with only the changes. */
class IrrigationPlanViewModel(
    private val mqtt: SmartHomeRepository,
    private val node1: Node1Repository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private data class Ui(
        /** Null = follow the node's config. */
        val draft: IrrigationDraft? = null,
        /** Saved with this revision; the draft is dropped once the node's config reaches it. */
        val awaitRev: Long? = null,
        val saving: Boolean = false,
        val message: PlanMessage? = null,
        val showLeaveConfirm: Boolean = false,
    )

    private val ui = MutableStateFlow(Ui())

    val state: StateFlow<IrrigationPlanState> = combine(mqtt.connectionState, node1.snapshot, ui) { connection, yard, u ->
        mapState(connection is MqttConnectionState.Connected, yard, u)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IrrigationPlanState())

    init {
        viewModelScope.launch {
            node1.snapshot.collect { yard ->
                val rev = yard.config?.rev ?: return@collect
                ui.update { u -> if (u.awaitRev != null && rev >= u.awaitRev) u.copy(draft = null, awaitRev = null) else u }
            }
        }
    }

    private fun mapState(connected: Boolean, yard: Node1Snapshot, u: Ui): IrrigationPlanState {
        val config = yard.config ?: return IrrigationPlanState(connected = connected, message = u.message)
        val draft = (u.draft ?: IrrigationDraft.from(config)).copy(pauseUntil = config.pauseUntil)
        return IrrigationPlanState(
            connected = connected,
            draft = draft,
            limits = config.limits,
            dirty = !IrrigationRules.changes(config, draft).isEmpty,
            problems = IrrigationRules.validate(draft, config.limits),
            saving = u.saving,
            pausedUntil = config.pauseUntil?.takeIf { IrrigationRules.isPaused(it, today()) },
            next = yard.state?.next,
            message = u.message,
            showLeaveConfirm = u.showLeaveConfirm,
        )
    }

    private fun edit(change: (IrrigationDraft) -> IrrigationDraft) {
        val current = state.value.draft ?: return
        ui.update { it.copy(draft = change(current)) }
    }

    fun onIntent(intent: PlanIntent) {
        when (intent) {
            is PlanIntent.SetAuto -> edit { it.copy(auto = intent.on) }
            is PlanIntent.ToggleDay -> edit { d -> d.copy(days = if (intent.day in d.days) d.days - intent.day else d.days + intent.day) }
            PlanIntent.EveryDay -> edit { it.copy(days = Weekday.entries.toSet()) }
            PlanIntent.AddStart -> edit { d -> IrrigationRules.addStart(d.starts, state.value.limits)?.let { d.copy(starts = it) } ?: d }
            is PlanIntent.ChangeStart -> edit { d ->
                d.copy(starts = IrrigationRules.sortedStarts(d.starts.mapIndexed { i, s -> if (i == intent.index) intent.time else s }))
            }
            is PlanIntent.RemoveStart -> edit { d -> d.copy(starts = d.starts.filterIndexed { i, _ -> i != intent.index }) }
            is PlanIntent.SetZone -> edit { d -> d.copy(zones = d.zones.mapIndexed { i, m -> if (i == intent.zone - 1) intent.minutes else m }) }
            is PlanIntent.SetSoakEnabled -> edit { d -> d.copy(soak = if (intent.on) DEFAULT_SOAK else Soak()) }
            is PlanIntent.SetSoak -> edit { it.copy(soak = intent.soak) }
            PlanIntent.Discard -> ui.update { it.copy(draft = null, showLeaveConfirm = false) }
            PlanIntent.Save -> save()
            is PlanIntent.PauseFor -> send(PlanMessage.Paused, Node1ConfigChange(pauseUntil = PauseUntil(IrrigationRules.pauseFor(today(), intent.days))))
            PlanIntent.ClearPause -> send(PlanMessage.PauseCleared, Node1ConfigChange(pauseUntil = PauseUntil(null)))
            PlanIntent.MessageShown -> ui.update { it.copy(message = null) }
            PlanIntent.RequestLeave -> ui.update { it.copy(showLeaveConfirm = true) }
            PlanIntent.KeepEditing -> ui.update { it.copy(showLeaveConfirm = false) }
        }
    }

    private fun save() {
        val s = state.value
        val config = node1.snapshot.value.config ?: return
        val draft = s.draft ?: return
        if (!s.canSave) return
        send(PlanMessage.Saved, IrrigationRules.changes(config, draft), dropDraft = true)
    }

    private fun send(success: PlanMessage, change: Node1ConfigChange, dropDraft: Boolean = false) {
        viewModelScope.launch {
            ui.update { it.copy(saving = true) }
            val outcome = node1.updateConfig(change)
            ui.update { u ->
                when (outcome) {
                    is CommandOutcome.Accepted -> u.copy(
                        saving = false,
                        message = if (outcome.saved) success else PlanMessage.NotSaved,
                        awaitRev = if (dropDraft) outcome.rev ?: 0 else u.awaitRev,
                    )
                    is CommandOutcome.Rejected -> u.copy(saving = false, message = PlanMessage.Rejected(outcome.error))
                    CommandOutcome.Timeout -> u.copy(saving = false, message = PlanMessage.Timeout)
                    CommandOutcome.NotConnected -> u.copy(saving = false, message = PlanMessage.NotConnected)
                }
            }
            // The node may already have republished its config before the answer.
            val rev = node1.snapshot.value.config?.rev
            ui.update { u -> if (u.awaitRev != null && rev != null && rev >= u.awaitRev) u.copy(draft = null, awaitRev = null) else u }
        }
    }

    companion object {
        val DEFAULT_SOAK = Soak(onMinutes = 5, offMinutes = 10)
    }
}
