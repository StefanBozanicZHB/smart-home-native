package zhb.company.smart_home.domain.garden

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Parses Node 1 payloads and builds its commands. A parse function returns null for a payload it
 * cannot use, so a broken message never wipes the last good state. Commands carry only the keys
 * the firmware knows (it rejects unknown keys) plus the `id`.
 */
object Node1Json {

    // ---- states ------------------------------------------------------------------------------

    fun parseConfig(payload: String): Node1Config? = parseObject(payload)?.let { o ->
        val limits = o.obj("limits")
        val d = Node1Limits()
        val soak = o.obj("soak")
        Node1Config(
            rev = o.long("rev") ?: return null,
            auto = o.bool("auto") ?: return null,
            days = o.strings("days")?.mapNotNull(Weekday::fromKey)?.toSet() ?: return null,
            starts = o.strings("starts") ?: return null,
            zones = o.ints("zones") ?: return null,
            soak = Soak(soak?.int("on") ?: 0, soak?.int("off") ?: 0),
            pauseUntil = o.string("pause_until"),
            limits = Node1Limits(
                zones = limits?.int("zones") ?: d.zones,
                startsMax = limits?.int("starts_max") ?: d.startsMax,
                zoneMinutesMax = limits?.int("zone_minutes_max") ?: d.zoneMinutesMax,
                soakOnMax = limits?.int("soak_on_max") ?: d.soakOnMax,
                soakOffMax = limits?.int("soak_off_max") ?: d.soakOffMax,
            ),
        )
    }

    fun parseState(payload: String): Node1State? = parseObject(payload)?.let { o ->
        val fountain = o.obj("fountain")
        Node1State(
            seq = o.long("seq") ?: return null,
            uptimeSeconds = o.long("up") ?: return null,
            time = o.string("time"),
            day = Weekday.fromKey(o.string("day")),
            auto = o.bool("auto") ?: false,
            valve = o.int("valve"),
            run = o.obj("run")?.let(::parseRun),
            next = o.obj("next")?.let { n ->
                n.string("time")?.let { NextStart(Weekday.fromKey(n.string("day")), it, n.int("in_days") ?: 0) }
            },
            paused = o.bool("paused") ?: false,
            fountain = FountainState(
                pump = fountain?.bool("pump") ?: false,
                pumpLeftSeconds = fountain?.int("pump_left"),
                light = fountain?.bool("light") ?: false,
                lightLeftSeconds = fountain?.int("light_left"),
            ),
            configRev = o.long("cfg_rev"),
            storage = o.string("storage"),
        )
    }

    private fun parseRun(r: JsonObject): ActiveRun? = ActiveRun(
        source = RunSource.fromKey(r.string("src")),
        zone = r.int("zone") ?: return null,
        phase = RunPhase.fromKey(r.string("phase")),
        phaseLeftSeconds = r.int("phase_left"),
        zones = r.ints("zones") ?: emptyList(),
        leftSeconds = r.ints("left") ?: emptyList(),
    )

    fun parseRunEvent(payload: String): RunEvent? = parseObject(payload)?.let { o ->
        RunEvent(
            seq = o.long("seq") ?: return null,
            atUptimeSeconds = o.long("at") ?: return null,
            event = RunEventType.fromKey(o.string("event")) ?: return null,
            source = RunSource.fromKey(o.string("src")),
            start = o.string("start"),
            zones = o.ints("zones") ?: emptyList(),
            minutes = o.ints("minutes") ?: emptyList(),
            reason = SkipReason.fromKey(o.string("reason")),
        )
    }

    // ---- commands ----------------------------------------------------------------------------

    fun configSetCommand(id: String, change: Node1ConfigChange): String = buildJsonObject {
        change.auto?.let { put("auto", it) }
        change.days?.let { days -> put("days", buildJsonArray { Weekday.entries.filter { it in days }.forEach { add(JsonPrimitive(it.key)) } }) }
        change.starts?.let { starts -> put("starts", buildJsonArray { starts.forEach { add(JsonPrimitive(it)) } }) }
        change.zones?.let { zones -> put("zones", buildJsonArray { zones.forEach { add(JsonPrimitive(it)) } }) }
        change.soak?.let { soak ->
            put(
                "soak",
                buildJsonObject {
                    put("on", soak.onMinutes)
                    put("off", soak.offMinutes)
                },
            )
        }
        change.pauseUntil?.let { p -> if (p.date == null) put("pause_until", JsonNull) else put("pause_until", p.date) }
        put("id", id)
    }.toString()

    /** Manual run: [zones] null = every zone with minutes; [minutes] null = each zone's configured minutes. */
    fun runStartCommand(id: String, zones: List<Int>? = null, minutes: Int? = null): String = buildJsonObject {
        zones?.let { z -> put("zones", buildJsonArray { z.forEach { add(JsonPrimitive(it)) } }) }
        minutes?.let { put("minutes", it) }
        put("id", id)
    }.toString()

    fun runStopCommand(id: String): String = buildJsonObject { put("id", id) }.toString()

    /** Fountain pump or light; [minutes] (1-1440) switches it off by itself after that time. */
    fun fountainCommand(id: String, on: Boolean, minutes: Int? = null): String = buildJsonObject {
        put("on", on)
        if (on && minutes != null) put("minutes", minutes)
        put("id", id)
    }.toString()

    // ---- helpers -----------------------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true }

    private fun parseObject(payload: String): JsonObject? =
        runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject

    private fun JsonObject.prim(key: String) = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.string(key: String) = prim(key)?.takeIf { it.isString }?.content
    private fun JsonObject.bool(key: String) = prim(key)?.takeUnless { it.isString }?.booleanOrNull
    private fun JsonObject.int(key: String) = prim(key)?.takeUnless { it.isString }?.intOrNull
    private fun JsonObject.long(key: String) = prim(key)?.takeUnless { it.isString }?.longOrNull
    private fun JsonObject.strings(key: String) =
        (this[key] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return null }
    private fun JsonObject.ints(key: String) =
        (this[key] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.intOrNull ?: return null }
}
