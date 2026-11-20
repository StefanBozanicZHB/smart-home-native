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

/** The four pipes of the water-to-water heat pump. */
enum class Pipe(val key: String) {
    /** Heating side, heat pump → house. */
    SUPPLY("supply"),

    /** Heating side, house → heat pump. */
    RETURN("return"),

    /** Ground water into the heat pump. */
    WELL_IN("well_in"),

    /** Cooled ground water out of the heat pump. */
    WELL_OUT("well_out"),
    ;

    companion object {
        fun fromKey(key: String?): Pipe? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Pipe temperatures in °C with the calibration offset applied; null = no sensor assigned, sensor
 * missing or read failed.
 */
data class PipeReadings(
    val supply: Double? = null,
    val ret: Double? = null,
    val wellIn: Double? = null,
    val wellOut: Double? = null,
) {
    operator fun get(pipe: Pipe): Double? = when (pipe) {
        Pipe.SUPPLY -> supply
        Pipe.RETURN -> ret
        Pipe.WELL_IN -> wellIn
        Pipe.WELL_OUT -> wellOut
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
    val pipes: PipeReadings,
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
    val pipes: PipeReadings,
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
/** Answer on `node5/result`; the same format as every node with the new API. */
typealias Node5CommandResult = zhb.company.smart_home.domain.node.NodeCommandResult

/** A sensor assigned to a pipe; the offset (°C × 100, −500…500) is added to its raw reading. */
data class PipeAssignment(val address: String, val offsetHundredths: Int = 0)

/** A DS18B20 found on the node's OneWire bus. */
data class BusSensor(
    /** 64-bit address, 16 hex digits. */
    val address: String,
    /** Raw reading (no offset) when the node published the list; null if the read failed. */
    val rawTemp: Double?,
    /** Pipe it is assigned to, or null. */
    val pipe: Pipe?,
)

/** `node5/sensors` (retained): pipe sensor assignment and every sensor found on the bus. */
data class PipeSensors(
    val rev: Long?,
    val assignments: Map<Pipe, PipeAssignment?>,
    /** Nothing assigned and exactly one sensor on the bus: the node uses it as the supply pipe. */
    val fallback: Boolean,
    val bus: List<BusSensor>,
) {
    fun assignment(pipe: Pipe): PipeAssignment? = assignments[pipe]

    fun pipeOf(address: String): Pipe? = assignments.entries.firstOrNull { it.value?.address.equals(address, ignoreCase = true) }?.key

    fun onBus(address: String): Boolean = bus.any { it.address.equals(address, ignoreCase = true) }
}
