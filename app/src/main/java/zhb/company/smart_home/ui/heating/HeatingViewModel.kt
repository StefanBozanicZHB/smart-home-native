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
import zhb.company.smart_home.data.mqtt.NodeStatus
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.domain.heating.DayType
import zhb.company.smart_home.domain.heating.HeatPumpStatus
import zhb.company.smart_home.domain.heating.Hysteresis
import zhb.company.smart_home.domain.heating.ScheduleRules
import zhb.company.smart_home.domain.heating.ScheduleSegment
import zhb.company.smart_home.domain.heating.formatTenths
import zhb.company.smart_home.domain.heating.TimeOfDay
import zhb.company.smart_home.ui.format.Readings
import zhb.company.smart_home.domain.model.SmartHomeState

class HeatingViewModel(
    private val repository: SmartHomeRepository,
    private val node5: Node5Repository,
) : ViewModel() {

    private data class DraftKey(val nodeId: String, val zone: String, val day: DayType)

    /** An unsaved day program, plus the zone revision it was started from. */
    private data class Draft(val segments: List<ScheduleSegment>, val baseRev: Long?)

    private data class UiExtras(
        val selectedZone: String? = null,
        val selectedDay: DayType? = null,
        val drafts: Map<DraftKey, Draft> = emptyMap(),
        val sending: Boolean = false,
        val showSettingsDialog: Boolean = false,
        val showMainSwitchOffConfirm: Boolean = false,
        val showDiscardDraftsConfirm: Boolean = false,
        val message: HeatingMessage? = null,
    )

    private val extras = MutableStateFlow(UiExtras())

    val state: StateFlow<HeatingState> = combine(
        repository.state,
        repository.connectionState,
        node5.snapshot,
        extras,
    ) { home, connection, snapshot, ui ->
        mapState(home, connection is MqttConnectionState.Connected, snapshot, ui)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HeatingState())

    private fun mapState(home: SmartHomeState, connected: Boolean, snapshot: Node5Snapshot, ui: UiExtras): HeatingState {
        val config = snapshot.config
        val live = snapshot.live
        val limits = config?.limits ?: HeatingState().limits
        val zoneIds = config?.zones ?: live?.zones?.keys?.sorted().orEmpty()
        val zone = ui.selectedZone?.takeIf { it in zoneIds } ?: zoneIds.firstOrNull()
        // Default to the day type the node is running now, so the editor opens on today's program.
        val day = ui.selectedDay ?: if (live?.weekend == true) DayType.WEEKEND else DayType.WORKDAY
        val zoneSettings = zone?.let { snapshot.zones[it] }
        val nodeDay = zoneSettings?.day(day).orEmpty()
        val draft = zone?.let { ui.drafts[DraftKey(snapshot.nodeId, it, day)] }
        val segments = draft?.segments ?: nodeDay.ifEmpty { ScheduleRules.defaultDay(limits) }

        return HeatingState(
            nodeId = snapshot.nodeId,
            connected = connected,
            online = when (home.nodeStatus(snapshot.nodeId)) {
                NodeStatus.ONLINE -> true
                NodeStatus.OFFLINE -> false
                NodeStatus.UNKNOWN -> null
            },
            loading = config == null || zoneSettings == null,
            outdoorTemp = Readings.temperature(home.temperatureOutdoor),
            mainSwitch = config?.mainSwitch ?: live?.mainSwitch ?: false,
            pump = live?.pump ?: false,
            demand = live?.demand ?: false,
            flowTemp = Readings.temperature(live?.pipes?.supply),
            pipes = live?.pipes?.let { p ->
                PipeTemps(
                    wellIn = p.wellIn?.let(Readings::temperature),
                    wellOut = p.wellOut?.let(Readings::temperature),
                    supply = p.supply?.let(Readings::temperature),
                    ret = p.ret?.let(Readings::temperature),
                )
            } ?: PipeTemps(),
            nextChange = nextChange(snapshot, zone),
            nodeTime = live?.time,
            nodeIsWeekend = live?.weekend,
            safeMode = when {
                snapshot.safeMode?.active == true -> SafeModeUi(snapshot.safeMode.reason, snapshot.safeMode.zone)
                snapshot.safeMode == null && live?.safeReason != null -> SafeModeUi(live.safeReason, null)
                else -> null
            },
            pumpStatus = HeatPumpStatus.from(connected, config, live, snapshot.pump, snapshot.safeMode),
            lastPumpChange = snapshot.pump?.let { p ->
                val ago = live?.uptimeSeconds?.minus(p.atUptimeSeconds)?.takeIf { it >= 0 }?.div(60)
                PumpChangeUi(on = p.pump, source = p.source, minutesAgo = ago)
            },
            zones = zoneIds.map { id ->
                val z = live?.zones?.get(id)
                ZoneUi(
                    id = id,
                    roomTemp = Readings.temperature(z?.roomTemp),
                    target = z?.targetTenths?.let(::formatTenths),
                    roomTempValue = z?.roomTemp,
                    targetTenths = z?.targetTenths,
                    demand = z?.demand ?: false,
                    fault = z?.fault,
                )
            },
            limits = limits,
            selectedZone = zone,
            selectedDay = day,
            editorSegments = segments,
            dayNotSet = zoneSettings != null && nodeDay.isEmpty(),
            scheduleDirty = draft != null && draft.segments != nodeDay,
            hasUnsavedDrafts = ui.drafts.any { (key, d) -> key.nodeId == snapshot.nodeId && d.segments != snapshot.zones[key.zone]?.day(key.day).orEmpty() },
            showDiscardDraftsConfirm = ui.showDiscardDraftsConfirm,
            scheduleProblem = ScheduleRules.validateDay(segments, limits),
            // Only a draft that really differs can conflict; our own saves move baseRev forward (rebaseDrafts).
            remoteChangedWhileEditing = draft != null && draft.segments != nodeDay && draft.baseRev != zoneSettings?.rev,
            hysteresis = zoneSettings?.hysteresis,
            minOnSeconds = config?.minOnSeconds,
            minOffSeconds = config?.minOffSeconds,
            configRev = config?.rev,
            sending = ui.sending,
            showSettingsDialog = ui.showSettingsDialog,
            showMainSwitchOffConfirm = ui.showMainSwitchOffConfirm,
            message = ui.message,
        )
    }

    /** Next change today in the program the node runs now (its own clock and day type), for the selected zone. */
    private fun nextChange(snapshot: Node5Snapshot, zone: String?): NextChangeUi? {
        val live = snapshot.live ?: return null
        val now = TimeOfDay.parse(live.time) ?: return null
        val dayType = when (live.weekend) {
            true -> DayType.WEEKEND
            false -> DayType.WORKDAY
            null -> return null
        }
        val program = zone?.let { snapshot.zones[it] }?.day(dayType) ?: return null
        val next = ScheduleRules.nextChange(program, now) ?: return null
        return NextChangeUi(TimeOfDay.format(next.startMinute), formatTenths(next.tempTenths))
    }

    fun onIntent(intent: HeatingIntent) {
        when (intent) {
            is HeatingIntent.ToggleMainSwitch ->
                // Turning off stops the heat pump immediately (no minimum run time), so ask first.
                if (intent.on) runCommand { node5.setMainSwitch(true) }
                else extras.update { it.copy(showMainSwitchOffConfirm = true) }
            HeatingIntent.ConfirmMainSwitchOff -> {
                extras.update { it.copy(showMainSwitchOffConfirm = false) }
                runCommand { node5.setMainSwitch(false) }
            }
            HeatingIntent.DismissMainSwitchOff -> extras.update { it.copy(showMainSwitchOffConfirm = false) }

            is HeatingIntent.SelectZone -> extras.update { it.copy(selectedZone = intent.zone) }
            is HeatingIntent.SelectDay -> extras.update { it.copy(selectedDay = intent.day) }
            is HeatingIntent.SetSegmentStart -> editDraft { day -> ScheduleRules.setStart(day, intent.index, intent.minute) }
            is HeatingIntent.ChangeSegmentTemp -> editDraft { day ->
                val current = day.getOrNull(intent.index)?.tempTenths ?: return@editDraft day
                ScheduleRules.setTemp(day, intent.index, current + intent.deltaTenths, state.value.limits)
            }
            HeatingIntent.AddSegment -> editDraft { day -> ScheduleRules.addSegment(day, state.value.limits) }
            is HeatingIntent.RemoveSegment -> editDraft { day -> ScheduleRules.removeSegment(day, intent.index) }
            HeatingIntent.SaveSchedule -> saveSchedule()
            HeatingIntent.DiscardSchedule -> currentDraftKey()?.let { key -> extras.update { it.copy(drafts = it.drafts - key) } }

            HeatingIntent.OpenSettingsDialog -> extras.update { it.copy(showSettingsDialog = true) }
            HeatingIntent.DismissSettingsDialog -> extras.update { it.copy(showSettingsDialog = false) }
            is HeatingIntent.SaveHysteresis -> {
                val zone = state.value.selectedZone ?: return
                extras.update { it.copy(showSettingsDialog = false) }
                runCommand(onAccepted = { accepted -> rebaseDrafts(zone, accepted.rev) }) { node5.updateZone(zone, hysteresis = intent.hysteresis) }
            }
            HeatingIntent.Refresh -> viewModelScope.launch { node5.requestState() }
            HeatingIntent.MessageShown -> extras.update { it.copy(message = null) }
            HeatingIntent.RequestLeave -> extras.update { it.copy(showDiscardDraftsConfirm = true) }
            HeatingIntent.KeepEditing -> extras.update { it.copy(showDiscardDraftsConfirm = false) }
            HeatingIntent.DiscardAllDrafts -> extras.update { it.copy(drafts = emptyMap(), showDiscardDraftsConfirm = false) }
        }
    }

    private fun currentDraftKey(): DraftKey? {
        val s = state.value
        val zone = s.selectedZone ?: return null
        return DraftKey(s.nodeId, zone, s.selectedDay)
    }

    /** Applies [change] to the selected day, starting a draft from what the editor shows now. */
    private fun editDraft(change: (List<ScheduleSegment>) -> List<ScheduleSegment>) {
        val s = state.value
        if (s.loading) return
        val key = currentDraftKey() ?: return
        val baseRev = node5.snapshot.value.zones[key.zone]?.rev
        extras.update { ui ->
            val existing = ui.drafts[key]
            val updated = change(existing?.segments ?: s.editorSegments)
            ui.copy(drafts = ui.drafts + (key to Draft(updated, existing?.baseRev ?: baseRev)))
        }
    }

    private fun saveSchedule() {
        val s = state.value
        val key = currentDraftKey() ?: return
        if (s.scheduleProblem != null || s.loading) return
        val segments = s.editorSegments
        runCommand(onAccepted = { accepted ->
            extras.update { it.copy(drafts = it.drafts - key) }
            rebaseDrafts(key.zone, accepted.rev)
        }) {
            when (key.day) {
                DayType.WORKDAY -> node5.updateZone(key.zone, workday = segments)
                DayType.WEEKEND -> node5.updateZone(key.zone, weekend = segments)
            }
        }
    }

    /**
     * Our own accepted change raised the zone revision; other open drafts of that zone were started
     * from the same program, so they must not be reported as "changed on the node".
     */
    private fun rebaseDrafts(zone: String, rev: Long?) {
        if (rev == null) return
        extras.update { ui ->
            ui.copy(drafts = ui.drafts.mapValues { (key, draft) -> if (key.zone == zone) draft.copy(baseRev = rev) else draft })
        }
    }

    private fun runCommand(onAccepted: (CommandOutcome.Accepted) -> Unit = {}, command: suspend () -> CommandOutcome) {
        if (extras.value.sending) return
        extras.update { it.copy(sending = true) }
        viewModelScope.launch {
            val outcome = command()
            if (outcome is CommandOutcome.Accepted) onAccepted(outcome)
            val message = when (outcome) {
                is CommandOutcome.Accepted -> HeatingMessage.Saved
                is CommandOutcome.Rejected -> HeatingMessage.Rejected(outcome.error)
                CommandOutcome.Timeout -> HeatingMessage.Timeout
                CommandOutcome.NotConnected -> HeatingMessage.NotConnected
            }
            extras.update { it.copy(sending = false, message = message) }
        }
    }
}

/** Hysteresis values the settings dialog starts from when the node has not sent them yet. */
internal val DefaultHysteresis = Hysteresis(lowerTenths = 1, upperTenths = 1)
