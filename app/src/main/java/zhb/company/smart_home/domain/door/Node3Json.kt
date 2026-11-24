package zhb.company.smart_home.domain.door

import zhb.company.smart_home.domain.node.bool
import zhb.company.smart_home.domain.node.int
import zhb.company.smart_home.domain.node.long
import zhb.company.smart_home.domain.node.parseJsonObject
import zhb.company.smart_home.domain.node.string

/** Retained `node3/state` (front door, `node3-2.0.0`, smart-home-mcu docs/mqtt_topics.md, section Node 3). */
data class Node3State(
    val seq: Long,
    val uptimeSeconds: Long,
    /** The lock relay is energised (open for 3 s). */
    val lockOpen: Boolean,
    /** Number of known cards built into the firmware. */
    val tags: Int?,
    /** The RC522 card reader answered its last self-check; null if not reported. */
    val readerOk: Boolean?,
)

enum class AccessMethod(val key: String) {
    TAG("tag"), BUTTON("button"), APP("app");

    companion object {
        fun fromKey(key: String?): AccessMethod? = entries.firstOrNull { it.key == key }
    }
}

/** Retained `node3/access`: the last access attempt. */
data class AccessEvent(
    val seq: Long,
    val atUptimeSeconds: Long,
    val granted: Boolean,
    val method: AccessMethod?,
    /** Card name for known cards, null otherwise. */
    val name: String?,
    /** Last 4 hex digits of the card UID (cards only). */
    val uidEnd: String?,
) {
    /**
     * Seconds between this event and [state], when both come from the same run of the node
     * (uptime of the state not before the event); null otherwise.
     */
    fun secondsBefore(state: Node3State?): Long? =
        state?.uptimeSeconds?.minus(atUptimeSeconds)?.takeIf { it >= 0 }
}

object Node3Json {
    fun parseState(payload: String): Node3State? = parseJsonObject(payload)?.let { o ->
        Node3State(
            seq = o.long("seq") ?: return null,
            uptimeSeconds = o.long("up") ?: return null,
            lockOpen = when (o.string("lock")) {
                "open" -> true
                "locked" -> false
                else -> return null
            },
            tags = o.int("tags"),
            readerOk = o.bool("reader_ok"),
        )
    }

    fun parseAccess(payload: String): AccessEvent? = parseJsonObject(payload)?.let { o ->
        AccessEvent(
            seq = o.long("seq") ?: return null,
            atUptimeSeconds = o.long("at") ?: return null,
            granted = when (o.string("result")) {
                "granted" -> true
                "denied" -> false
                else -> return null
            },
            method = AccessMethod.fromKey(o.string("method")),
            name = o.string("name"),
            uidEnd = o.string("uid_end"),
        )
    }
}
