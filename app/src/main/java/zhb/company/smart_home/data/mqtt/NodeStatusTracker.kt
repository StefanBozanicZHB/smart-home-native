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
) {
    /** Latest known RSSI: from the last heartbeat, else from the connect-time info. */
    val rssi: Int? get() = heartbeat?.rssi ?: info?.rssi

    val signal: SignalQuality? get() = rssi?.let(SignalQuality::fromRssi)
}

/**
 * Online status of nodes with the new firmware (zhb_mqtt / zhb_heartbeat libraries, node4 and
 * node5 today). A node counts as "new" once it has published `<node>/status`, so a migrated node
 * is picked up without changes here. Legacy nodes keep the old rule in [SmartHomeRepository].
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
        val info: NodeInfo? = null,
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
        // Everything else only matters for nodes that have announced themselves with `status`.
        current ?: return false
        var next = current
        if (leaf == "heartbeat") next = next.copy(heartbeat = parseHeartbeat(payload) ?: next.heartbeat)
        if (leaf == "info") next = next.copy(info = parseInfo(payload) ?: next.info)
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
        NodeHealth(id, status, reason, e.lastSeenAtMs, e.heartbeat, e.info)
    }

    /** The app lost its broker connection: nothing is known until retained messages arrive again. */
    fun clear() {
        entries.clear()
    }

    companion object {
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
            NodeInfo(it.str("fw"), it.str("git"), it.str("ip"), it.str("host"), it.int("rssi"))
        }
    }
}
