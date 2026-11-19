package zhb.company.smart_home.domain.heating

/*
 * Node 5 (heat pump thermostat, firmware node5-2.0.0) data model.
 * API: smart-home-mcu/docs/mqtt_topics.md, section "Node 5".
 *
 * Like the firmware, temperatures that the app edits are kept in tenths of a degree (Int), and
 * times of day in minutes since midnight, so a 0.1 °C step never drifts through floating point.
 * Measured temperatures (room, flow pipe) are kept as Double for display only.
 */

/** Value limits published by the node in `node5/config`. Defaults match node5-2.0.0. */
data class Node5Limits(
    val tempMinTenths: Int = 50,
    val tempMaxTenths: Int = 300,
    val stepMaxTenths: Int = 20,
    val segmentsMax: Int = 8,
)

/** `node5/config` (retained). */
data class Node5Config(
    val rev: Long,
    val mainSwitch: Boolean,
    val zones: List<String>,
    val minOnSeconds: Int?,
    val minOffSeconds: Int?,
    val limits: Node5Limits,
)

enum class DayType(val key: String) {
    WORKDAY("workday"),
    WEEKEND("weekend"),
}

/** One schedule segment: [tempTenths] applies from [startMinute] until the next segment starts. */
data class ScheduleSegment(val startMinute: Int, val tempTenths: Int)

/** The zone demands heat below `target - lower` and stops above `target + upper`. */
data class Hysteresis(val lowerTenths: Int, val upperTenths: Int)

/** `node5/zones/<zone>` (retained). A day that is not set yet is an empty list. */
data class ZoneSettings(
    val rev: Long?,
    val hysteresis: Hysteresis,
    val workday: List<ScheduleSegment>,
    val weekend: List<ScheduleSegment>,
) {
    fun day(day: DayType): List<ScheduleSegment> = when (day) {
        DayType.WORKDAY -> workday
        DayType.WEEKEND -> weekend
    }
}

/** `node5/state` → `zones.<zone>`. */
data class ZoneLiveState(
    val roomTemp: Double?,
    val roomAgeSeconds: Int?,
    val targetTenths: Int?,
    val demand: Boolean,
    /** `no_schedule`, `sensor_offline`, `room_temp_stale` or null. */
    val fault: String?,
)

/** `node5/state` (retained, every 10 s and on every change). */
data class Node5LiveState(
    val seq: Long,
    val uptimeSeconds: Long,
    /** Node local time "HH:MM"; null until NTP. */
    val time: String?,
    /** Day type the schedule uses now; null until NTP. */
    val weekend: Boolean?,
    val mainSwitch: Boolean,
    val mainRelay: Boolean,
    val thermoRelay: Boolean,
    /** Both relays closed = heat pump running. */
    val pump: Boolean,
    val demand: Boolean,
    /** Safe mode reason, null in normal operation. */
    val safeReason: String?,
    val flowTemp: Double?,
    val configRev: Long?,
    val zones: Map<String, ZoneLiveState>,
)

/** `node5/pump` (retained): the last pump change. */
data class PumpEvent(
    val seq: Long,
    val atUptimeSeconds: Long,
    val pump: Boolean,
    /** `thermostat`, `schedule`, `app`, `safe_mode` or `boot`. */
    val source: String,
    val flowTemp: Double?,
)

/** `node5/safe_mode` (retained). */
data class SafeModeState(
    val active: Boolean,
    /** `startup`, `no_time`, `mqtt_down`, `no_schedule`, `sensor_offline`, `room_temp_stale`. */
    val reason: String?,
    val zone: String?,
    val atUptimeSeconds: Long?,
)

/** `node5/result`: the answer to one command. [command] is relative, e.g. `zones/ground/set`. */
data class Node5CommandResult(
    val command: String,
    val id: String?,
    val ok: Boolean,
    val rev: Long?,
    val error: String?,
)
