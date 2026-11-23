package zhb.company.smart_home.domain.heating

import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Parses Node 5 state payloads and builds command payloads. Every parse function returns null
 * for a payload it cannot use (malformed JSON, missing required fields), so a broken message
 * never wipes the last good state.
 */
object Node5Json {

    // ---- states ------------------------------------------------------------------------------

    fun parseConfig(payload: String): Node5Config? = parseObject(payload)?.let { o ->
        val limits = o.obj("limits")
        val defaults = Node5Limits()
        Node5Config(
            rev = o.long("rev") ?: return null,
            mainSwitch = o.bool("main_switch") ?: return null,
            zones = (o["zones"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                ?: return null,
            minOnSeconds = o.int("min_on_s"),
            minOffSeconds = o.int("min_off_s"),
            limits = Node5Limits(
                tempMinTenths = limits?.tenths("temp_min") ?: defaults.tempMinTenths,
                tempMaxTenths = limits?.tenths("temp_max") ?: defaults.tempMaxTenths,
                stepMaxTenths = limits?.tenths("step_max") ?: defaults.stepMaxTenths,
                segmentsMax = limits?.int("segments_max") ?: defaults.segmentsMax,
            ),
        )
    }

    fun parseZone(payload: String): ZoneSettings? = parseObject(payload)?.let { o ->
        val h = o.obj("hysteresis") ?: return null
        ZoneSettings(
            rev = o.long("rev"),
            hysteresis = Hysteresis(
                lowerTenths = h.tenths("lower") ?: return null,
                upperTenths = h.tenths("upper") ?: return null,
            ),
            workday = parseDay(o["workday"]) ?: return null,
            weekend = parseDay(o["weekend"]) ?: return null,
        )
    }

    fun parseState(payload: String): Node5LiveState? = parseObject(payload)?.let { o ->
        Node5LiveState(
            seq = o.long("seq") ?: return null,
            uptimeSeconds = o.long("up") ?: return null,
            time = o.string("time"),
            weekend = o.bool("weekend"),
            mainSwitch = o.bool("main_switch") ?: return null,
            mainRelay = o.bool("main_relay") ?: false,
            thermoRelay = o.bool("thermo_relay") ?: false,
            pump = o.bool("pump") ?: return null,
            demand = o.bool("demand") ?: false,
            safeReason = o.string("safe"),
            pipes = parsePipes(o),
            configRev = o.long("cfg_rev"),
            zones = o.obj("zones")?.mapNotNull { (id, value) ->
                val z = value as? JsonObject ?: return@mapNotNull null
                id to ZoneLiveState(
                    roomTemp = z.double("room_t"),
                    roomAgeSeconds = z.int("room_age"),
                    targetTenths = z.tenths("target"),
                    demand = z.bool("demand") ?: false,
                    fault = z.string("fault"),
                )
            }?.toMap().orEmpty(),
        )
    }

    fun parsePump(payload: String): PumpEvent? = parseObject(payload)?.let { o ->
        PumpEvent(
            seq = o.long("seq") ?: return null,
            atUptimeSeconds = o.long("at") ?: return null,
            pump = o.bool("pump") ?: return null,
            source = o.string("src") ?: "",
            pipes = parsePipes(o),
        )
    }

    fun parseSafeMode(payload: String): SafeModeState? = parseObject(payload)?.let { o ->
        SafeModeState(
            active = o.bool("active") ?: return null,
            reason = o.string("reason"),
            zone = o.string("zone"),
            atUptimeSeconds = o.long("at"),
        )
    }

    fun parseSensors(payload: String): PipeSensors? = parseObject(payload)?.let { o ->
        val pipes = o.obj("pipes") ?: return null
        PipeSensors(
            rev = o.long("rev"),
            assignments = Pipe.entries.associateWith { pipe ->
                (pipes[pipe.key] as? JsonObject)?.let { a ->
                    val address = a.string("addr") ?: return@let null
                    PipeAssignment(address.uppercase(), a.double("offset")?.let { (it * 100).roundToInt() } ?: 0)
                }
            },
            fallback = o.bool("fallback") ?: false,
            bus = (o["bus"] as? JsonArray)?.mapNotNull { item ->
                val s = item as? JsonObject ?: return@mapNotNull null
                BusSensor(
                    address = s.string("addr")?.uppercase() ?: return@mapNotNull null,
                    rawTemp = s.double("t"),
                    pipe = Pipe.fromKey(s.string("pipe")),
                )
            }.orEmpty(),
        )
    }

    fun parseResult(payload: String): Node5CommandResult? = zhb.company.smart_home.domain.node.NodeCommandResult.parse(payload)

    /** `pipes`: the four pipe temperatures; missing pipes are null. */
    private fun parsePipes(o: JsonObject): PipeReadings {
        val pipes = o.obj("pipes") ?: return PipeReadings()
        return PipeReadings(
            supply = pipes.double(Pipe.SUPPLY.key),
            ret = pipes.double(Pipe.RETURN.key),
            wellIn = pipes.double(Pipe.WELL_IN.key),
            wellOut = pipes.double(Pipe.WELL_OUT.key),
        )
    }

    // ---- commands ----------------------------------------------------------------------------

    fun mainSwitchCommand(on: Boolean, id: String): String = buildJsonObject {
        put("on", on)
        put("id", id)
    }.toString()

    /** Pipe sensor patch: only the pipes in [changes] are sent; a null value removes the sensor. */
    fun sensorsSetCommand(id: String, changes: Map<Pipe, PipeAssignment?>): String = buildJsonObject {
        put(
            "pipes",
            buildJsonObject {
                Pipe.entries.filter { it in changes }.forEach { pipe ->
                    val a = changes[pipe]
                    if (a == null) {
                        put(pipe.key, JsonNull)
                    } else {
                        put(
                            pipe.key,
                            buildJsonObject {
                                put("addr", a.address)
                                put("offset", a.offsetHundredths / 100.0)
                            },
                        )
                    }
                }
            },
        )
        put("id", id)
    }.toString()

    /** Zone patch: keys left null are not sent, so the node keeps their current value. */
    fun zoneSetCommand(
        id: String,
        hysteresis: Hysteresis? = null,
        workday: List<ScheduleSegment>? = null,
        weekend: List<ScheduleSegment>? = null,
    ): String = buildJsonObject {
        if (hysteresis != null) {
            put(
                "hysteresis",
                buildJsonObject {
                    put("lower", tenthsToJson(hysteresis.lowerTenths))
                    put("upper", tenthsToJson(hysteresis.upperTenths))
                },
            )
        }
        if (workday != null) put(DayType.WORKDAY.key, dayToJson(workday))
        if (weekend != null) put(DayType.WEEKEND.key, dayToJson(weekend))
        put("id", id)
    }.toString()

    // ---- helpers -----------------------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true }

    private fun parseObject(payload: String): JsonObject? =
        runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject

    private fun parseDay(element: JsonElement?): List<ScheduleSegment>? {
        val array = element as? JsonArray ?: return null
        return array.map { item ->
            val pair = item as? JsonArray ?: return null
            if (pair.size != 2) return null
            val start = TimeOfDay.parse((pair[0] as? JsonPrimitive)?.takeIf { it.isString }?.content) ?: return null
            val temp = (pair[1] as? JsonPrimitive)?.doubleOrNull ?: return null
            ScheduleSegment(start, (temp * 10).roundToInt())
        }
    }

    private fun dayToJson(day: List<ScheduleSegment>): JsonArray = buildJsonArray {
        day.forEach { segment ->
            add(
                buildJsonArray {
                    add(JsonPrimitive(TimeOfDay.format(segment.startMinute)))
                    add(tenthsToJson(segment.tempTenths))
                },
            )
        }
    }

    /** 215 -> 21.5 (a Double with one decimal prints exactly, e.g. "21.5", "20.0"). */
    private fun tenthsToJson(tenths: Int): JsonPrimitive = JsonPrimitive(tenths / 10.0)

    private fun JsonObject.primitive(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.long(key: String): Long? = primitive(key)?.takeUnless { it.isString }?.longOrNull
    private fun JsonObject.int(key: String): Int? = primitive(key)?.takeUnless { it.isString }?.intOrNull
    private fun JsonObject.double(key: String): Double? = primitive(key)?.takeUnless { it.isString }?.doubleOrNull
    private fun JsonObject.bool(key: String): Boolean? = primitive(key)?.takeUnless { it.isString }?.booleanOrNull
    private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content
    private fun JsonObject.tenths(key: String): Int? = double(key)?.let { (it * 10).roundToInt() }
}
