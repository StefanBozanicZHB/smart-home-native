package zhb.company.smart_home.domain.garden

/**
 * Node 1 (fountain and sprinklers, `node1-2.0.0`) as described in smart-home-mcu
 * docs/mqtt_topics.md, section Node 1. Durations in minutes unless the name says seconds.
 */
enum class Weekday(val key: String) {
    MON("mon"), TUE("tue"), WED("wed"), THU("thu"), FRI("fri"), SAT("sat"), SUN("sun");

    companion object {
        fun fromKey(key: String?): Weekday? = entries.firstOrNull { it.key == key }
    }
}

/** Soak cycles: water [onMinutes], pause [offMinutes], until the zone's minutes are done; 0 = off. */
data class Soak(val onMinutes: Int = 0, val offMinutes: Int = 0) {
    val enabled: Boolean get() = onMinutes > 0 && offMinutes > 0
}

data class Node1Limits(
    val zones: Int = 6,
    val startsMax: Int = 4,
    val zoneMinutesMax: Int = 60,
    val soakOnMax: Int = 60,
    val soakOffMax: Int = 120,
)

/** Retained `node1/config`. */
data class Node1Config(
    val rev: Long,
    val auto: Boolean,
    val days: Set<Weekday>,
    /** "HH:MM", sorted. */
    val starts: List<String>,
    /** Minutes per zone 1..6 (index 0 = zone 1); 0 = not watered by the schedule. */
    val zones: List<Int>,
    val soak: Soak,
    /** "YYYY-MM-DD": no scheduled watering up to and including that date; null = no pause. */
    val pauseUntil: String?,
    val limits: Node1Limits = Node1Limits(),
)

enum class RunPhase(val key: String) {
    ON("on"), SOAK("soak");

    companion object {
        fun fromKey(key: String?): RunPhase? = entries.firstOrNull { it.key == key }
    }
}

enum class RunSource(val key: String) {
    SCHEDULE("schedule"), APP("app");

    companion object {
        fun fromKey(key: String?): RunSource? = entries.firstOrNull { it.key == key }
    }
}

/** The active run in `node1/state`. */
data class ActiveRun(
    val source: RunSource?,
    val zone: Int,
    val phase: RunPhase?,
    val phaseLeftSeconds: Int?,
    val zones: List<Int>,
    /** Seconds of watering still due per zone in [zones]. */
    val leftSeconds: List<Int>,
) {
    val totalLeftSeconds: Int get() = leftSeconds.sum()
}

data class NextStart(val day: Weekday?, val time: String, val inDays: Int)

data class FountainState(
    val pump: Boolean,
    val pumpLeftSeconds: Int?,
    val light: Boolean,
    val lightLeftSeconds: Int?,
)

/** Retained `node1/state`. */
data class Node1State(
    val seq: Long,
    val uptimeSeconds: Long,
    /** Node clock "HH:MM", null until NTP. */
    val time: String?,
    val day: Weekday?,
    val auto: Boolean,
    /** Open valve (zone 1-6), null = all closed. */
    val valve: Int?,
    val run: ActiveRun?,
    val next: NextStart?,
    val paused: Boolean,
    val fountain: FountainState,
    val configRev: Long?,
    /** Problem with the stored configuration, null if none. */
    val storage: String?,
)

enum class RunEventType(val key: String) {
    STARTED("started"), FINISHED("finished"), STOPPED("stopped"), REPLACED("replaced"), SKIPPED("skipped");

    companion object {
        fun fromKey(key: String?): RunEventType? = entries.firstOrNull { it.key == key }
    }
}

enum class SkipReason(val key: String) {
    BUSY("busy"), NO_ZONES("no_zones"), PAUSED("paused");

    companion object {
        fun fromKey(key: String?): SkipReason? = entries.firstOrNull { it.key == key }
    }
}

/** Retained `node1/run`: the last run event. */
data class RunEvent(
    val seq: Long,
    val atUptimeSeconds: Long,
    val event: RunEventType,
    val source: RunSource?,
    /** Start time for scheduled runs. */
    val start: String?,
    val zones: List<Int>,
    val minutes: List<Int>,
    val reason: SkipReason?,
)

/** Changes for `node1/config/set`; null fields are not sent. */
data class Node1ConfigChange(
    val auto: Boolean? = null,
    val days: Set<Weekday>? = null,
    val starts: List<String>? = null,
    val zones: List<Int>? = null,
    val soak: Soak? = null,
    /** Sent when not null: [PauseUntil.date] null clears the pause. */
    val pauseUntil: PauseUntil? = null,
) {
    val isEmpty: Boolean get() = this == Node1ConfigChange()
}

data class PauseUntil(val date: String?)
