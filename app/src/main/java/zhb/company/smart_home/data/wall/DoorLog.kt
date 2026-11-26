package zhb.company.smart_home.data.wall

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import zhb.company.smart_home.data.door.Node3Topics
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.domain.door.AccessEvent
import zhb.company.smart_home.domain.door.Node3Json

/** One access event with the phone time it happened at (estimated for a retained one). */
data class DoorLogEntry(val atMs: Long, val event: AccessEvent)

/**
 * The last front door access events for the wall. The node keeps only the last event (retained
 * `node3/access`), so this collects the ones that arrive while the app runs, [MAX] at most.
 * A live event is dated when it arrives. A retained one (on connect) has only the node's uptime,
 * so it is dated from the last `node3/state` (also uptime) when that came from the same run, and
 * left undated otherwise. The card UID is never kept for display.
 */
class DoorLog(mqtt: SmartHomeRepository, private val clock: () -> Long = System::currentTimeMillis) {
    private val _entries = MutableStateFlow<List<DoorLogEntry>>(emptyList())
    val entries: StateFlow<List<DoorLogEntry>> = _entries.asStateFlow()

    /** The last node state: its uptime and the phone time it arrived. */
    @Volatile
    private var stateClock: Pair<Long, Long>? = null

    /** A retained event that came before the state it is dated from (the broker sends them in any order). */
    @Volatile
    private var pendingRetained: AccessEvent? = null

    init {
        mqtt.addMessageListener { topic, payload, retained ->
            when (topic) {
                STATE_TOPIC -> Node3Json.parseState(payload)?.let { state ->
                    stateClock = state.uptimeSeconds to clock()
                    pendingRetained?.let { pendingRetained = null; add(it, retained = true) }
                }
                ACCESS_TOPIC -> Node3Json.parseAccess(payload)?.let { add(it, retained) }
            }
        }
    }

    private fun add(event: AccessEvent, retained: Boolean) {
        val at = if (!retained) {
            clock()
        } else {
            val (up, receivedMs) = stateClock ?: run { pendingRetained = event; return }
            val ago = up - event.atUptimeSeconds
            if (ago < 0) return // the state is from another run of the node
            receivedMs - ago * 1000
        }
        val clean = event.copy(uidEnd = null)
        _entries.update { list ->
            // The retained event comes again with every reconnect: same run, same seq.
            if (list.any { it.event.seq == clean.seq && it.event.atUptimeSeconds == clean.atUptimeSeconds }) return@update list
            (list + DoorLogEntry(at, clean)).sortedByDescending { it.atMs }.take(MAX)
        }
    }

    companion object {
        const val MAX = 3
        private const val STATE_TOPIC = Node3Topics.BASE + Node3Topics.STATE
        private const val ACCESS_TOPIC = Node3Topics.BASE + Node3Topics.ACCESS
    }
}
