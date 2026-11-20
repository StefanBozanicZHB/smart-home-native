package zhb.company.smart_home.data.mqtt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

enum class NodeStatus { ONLINE, OFFLINE, UNKNOWN }

enum class OfflineReason {
    /** The node published `offline`, or the broker did on its behalf (Last Will). */
    LAST_WILL,

    /** Nothing live from the node within the heartbeat timeout (e.g. the cloud bridge is down). */
    NO_HEARTBEAT,
}

/** `<node>/heartbeat` (every 60 s, not retained). */
data class NodeHeartbeat(
    val firmware: String?,
    val uptimeSeconds: Long?,
    val rssi: Int?,
    val freeHeap: Long?,
    val mqttConnects: Long?,
)

/** `<node>/info` (retained, on every broker connect). */
data class NodeInfo(
    val firmware: String?,
    val gitRevision: String?,
    val ip: String?,
    val host: String?,
    /** RSSI at connect time (dBm). */
    val rssi: Int? = null,
    val mac: String? = null,
    val ssid: String? = null,
)

/** `<node>/boot` (once after boot, not retained): only known if the app was connected at that moment. */
data class NodeBoot(
    val firmware: String?,
    val gitRevision: String?,
    /** ESP reset reason as text, e.g. "Power On", "Hardware Watchdog". */
    val reset: String?,
    /** Wall-clock time (ms) the message arrived. */
    val atMs: Long,
)

/** Wi-Fi signal quality from RSSI (dBm), using the usual ESP8266 thresholds. */
enum class SignalQuality(val bars: Int) {
    EXCELLENT(4), // >= -60 dBm
    GOOD(3), // -61 .. -70
    WEAK(2), // -71 .. -80: occasional drops
    POOR(1); // < -80: unreliable connection

    companion object {
        fun fromRssi(rssi: Int): SignalQuality = when {
            rssi >= -60 -> EXCELLENT
            rssi >= -70 -> GOOD
            rssi >= -80 -> WEAK
            else -> POOR
        }
    }
}

data class NodeHealth(
    val nodeId: String,
    val status: NodeStatus,
    val offlineReason: OfflineReason?,
    /** Wall-clock time (ms) of the last live message from the node, null if none since the app connected. */
    val lastSeenAtMs: Long?,
    val heartbeat: NodeHeartbeat?,
    val info: NodeInfo?,
    /** Wall-clock time (ms) the last [heartbeat] arrived. */
    val heartbeatAtMs: Long? = null,
    val boot: NodeBoot? = null,
) {
    /** Latest known RSSI: from the last heartbeat, else from the connect-time info. */
    val rssi: Int? get() = heartbeat?.rssi ?: info?.rssi

    val signal: SignalQuality? get() = rssi?.let(SignalQuality::fromRssi)

    val firmware: String? get() = heartbeat?.firmware ?: info?.firmware ?: boot?.firmware

    /** Estimated boot time (wall clock, ms) from the last heartbeat's uptime. */
    val bootedAtMs: Long? get() {
        val up = heartbeat?.uptimeSeconds ?: return null
        val at = heartbeatAtMs ?: return null
        return at - up * 1000
    }

    /** Uptime now, extrapolated from the last heartbeat; null if unknown or the node is not online. */
    fun uptimeSecondsAt(nowMs: Long): Long? =
        bootedAtMs?.takeIf { status == NodeStatus.ONLINE }?.let { ((nowMs - it) / 1000).coerceAtLeast(0) }

    /** Booted within [RECENT_RESTART_MS] (watchdog, power cut, crash): worth a warning. */
    fun recentlyRestarted(nowMs: Long): Boolean = uptimeSecondsAt(nowMs)?.let { it * 1000 < RECENT_RESTART_MS } == true

    /** The [boot] message, if it belongs to the current run (not to an earlier boot). */
    val currentBoot: NodeBoot? get() {
        val b = boot ?: return null
        val booted = bootedAtMs ?: return b
        return b.takeIf { kotlin.math.abs(it.atMs - booted) <= BOOT_MATCH_MS }
    }

    /** Broker reconnects since boot (the first connect does not count). */
    val reconnects: Long? get() = heartbeat?.mqttConnects?.let { (it - 1).coerceAtLeast(0) }

    companion object {
        const val RECENT_RESTART_MS = 30 * 60_000L

        /** A boot message arrives within seconds of the boot (Wi-Fi + broker connect). */
        private const val BOOT_MATCH_MS = 2 * 60_000L
    }
}

/**
 * Online status of nodes with the new firmware (zhb_mqtt / zhb_heartbeat libraries: node1, node4,
 * node5 today). A node counts as "new" once it has published `<node>/status`, `heartbeat`, `info`
 * or `boot`, so a migrated node is picked up without changes here. The retained `status` alone is
 * not enough: it can be missing (e.g. a bridge that does not forward retained messages, or a
 * broker restarted without persistence) while heartbeats keep arriving. Legacy nodes keep the
 * old rule in [SmartHomeRepository].
 *
 * Rules:
 * - `status` = `offline` (sent by the broker as Last Will) → OFFLINE;
 * - otherwise ONLINE while live messages keep arriving (`heartbeat` every 60 s, or any other
 *   non-retained message), OFFLINE after [heartbeatTimeoutMs] of silence. This also catches the
 *   case where the app talks to the cloud broker and the bridge is down, so the local broker's
 *   Last Will never reaches the app;
 * - a retained `online` received on connect counts as seen at that moment: if the node is really
 *   gone, the missing heartbeat turns it OFFLINE after the timeout.
 *
 * Not thread-safe on its own: [SmartHomeRepository] calls it inside its state update.
 * Time is passed in, so the rules are testable without a real clock.
 */
class NodeStatusTracker(
    private val heartbeatTimeoutMs: Long = DEFAULT_HEARTBEAT_TIMEOUT_MS,
) {
    private data class Entry(
        val lastWill: Boolean = false,
        val lastSeenAtMs: Long? = null,
        val heartbeat: NodeHeartbeat? = null,
        val heartbeatAtMs: Long? = null,
        val info: NodeInfo? = null,
        val boot: NodeBoot? = null,
    )

    private val entries = LinkedHashMap<String, Entry>()

    /** Feeds one MQTT message; returns true if it concerned a tracked node. */
    fun onMessage(topic: String, payload: String, retained: Boolean, nowMs: Long): Boolean {
        val match = TOPIC.matchEntire(topic) ?: return false
        val nodeId = match.groupValues[1]
        val leaf = match.groupValues[2]
        val current = entries[nodeId]

        if (leaf == "status") {
            entries[nodeId] = when (payload) {
                "online" -> (current ?: Entry()).copy(lastWill = false, lastSeenAtMs = nowMs)
                "offline" -> (current ?: Entry()).copy(lastWill = true)
                else -> return current != null
            }
            return true
        }
        // Other topics only matter for nodes that have announced themselves (`status` above, or
        // one of the common new-firmware topics); legacy topics are left to the old rule.
        if (current == null && !(leaf in NEW_FIRMWARE_LEAVES && parseObject(payload)?.str("fw") != null)) return false
        var next = current ?: Entry()
        val heartbeat = if (leaf == "heartbeat") parseHeartbeat(payload) else null
        if (heartbeat != null) next = next.copy(heartbeat = heartbeat, heartbeatAtMs = nowMs)
        if (leaf == "info") next = next.copy(info = parseInfo(payload) ?: next.info)
        val boot = if (leaf == "boot" && !retained) parseBoot(payload, nowMs) else null
        if (boot != null) next = next.copy(boot = boot)
        if (!retained) next = next.copy(lastSeenAtMs = nowMs, lastWill = false)
        entries[nodeId] = next
        return true
    }

    fun snapshot(nowMs: Long): Map<String, NodeHealth> = entries.mapValues { (id, e) ->
        val (status, reason) = when {
            e.lastWill -> NodeStatus.OFFLINE to OfflineReason.LAST_WILL
            e.lastSeenAtMs == null -> NodeStatus.UNKNOWN to null
            nowMs - e.lastSeenAtMs > heartbeatTimeoutMs -> NodeStatus.OFFLINE to OfflineReason.NO_HEARTBEAT
            else -> NodeStatus.ONLINE to null
        }
        NodeHealth(id, status, reason, e.lastSeenAtMs, e.heartbeat, e.info, e.heartbeatAtMs, e.boot)
    }

    /** The app lost its broker connection: nothing is known until retained messages arrive again. */
    fun clear() {
        entries.clear()
    }

    companion object {
        /** Topics only the new firmware publishes; any of them with a `fw` field marks a node as new. */
        private val NEW_FIRMWARE_LEAVES = setOf("heartbeat", "info", "boot")

        /** Three missed 60 s heartbeats. */
        const val DEFAULT_HEARTBEAT_TIMEOUT_MS = 180_000L

        /** `bozaSub/kuca/<node>/<leaf>`; legacy topics are deeper or use other leaf names. */
        private val TOPIC = Regex("""^bozaSub/kuca/([A-Za-z0-9_]+)/([A-Za-z0-9_]+)$""")
        private val json = Json { ignoreUnknownKeys = true }

        private fun parseObject(payload: String): JsonObject? =
            runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject

        private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

        private fun parseHeartbeat(payload: String) = parseObject(payload)?.let {
            NodeHeartbeat(it.str("fw"), it.long("up"), it.int("rssi"), it.long("heap"), it.long("mqtt"))
        }

        private fun parseInfo(payload: String) = parseObject(payload)?.let {
            NodeInfo(it.str("fw"), it.str("git"), it.str("ip"), it.str("host"), it.int("rssi"), it.str("mac"), it.str("ssid"))
        }

        private fun parseBoot(payload: String, nowMs: Long) = parseObject(payload)?.let {
            NodeBoot(it.str("fw"), it.str("git"), it.str("reset"), nowMs)
        }
    }
}
