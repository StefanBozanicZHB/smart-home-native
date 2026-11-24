package zhb.company.smart_home.domain.schedule

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import zhb.company.smart_home.domain.garden.Weekday
import zhb.company.smart_home.domain.node.string

/** Schedule JSON shared by Node 1 and Node 2. */
object ScheduleJson {
    /** A schedule object; null for JSON null or anything malformed. */
    fun parseSchedule(element: JsonElement?): OutputSchedule? {
        val o = element as? JsonObject ?: return null
        val on = ScheduleTime.parse(o.string("on")) ?: return null
        val off = ScheduleTime.parse(o.string("off")) ?: return null
        val days = when (val d = o["days"]) {
            null, is JsonNull -> Weekday.entries.toSet()
            is JsonArray -> d.mapNotNull { Weekday.fromKey((it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content) }.toSet()
            else -> return null
        }
        return OutputSchedule(on, off, days)
    }

    /**
     * Outputs with a schedule slot, in the node's order: `{"neon":null,"lamp":{...}}` → neon → null,
     * lamp → schedule. The keys are the outputs; null = no schedule (manual only).
     */
    fun parseSchedules(o: JsonObject): Map<String, OutputSchedule?> = o.mapValues { (_, v) -> parseSchedule(v) }

    /** `{"on":"16:52","off":"23:30"}` → window; null = no schedule or no window today. */
    fun parseToday(element: JsonElement?): TodayWindow? {
        val o = element as? JsonObject ?: return null
        return TodayWindow(o.string("on") ?: return null, o.string("off") ?: return null)
    }

    /**
     * `<output>/schedule/set` payload: the schedule with the command [id]. Removing a schedule is a
     * bare `null` (the firmware takes no id with it), see [REMOVE].
     */
    fun setCommand(id: String, schedule: OutputSchedule): String = buildJsonObject {
        put("on", schedule.on.toWire())
        put("off", schedule.off.toWire())
        put("days", buildJsonArray { Weekday.entries.filter { it in schedule.days }.forEach { add(JsonPrimitive(it.key)) } })
        put("id", id)
    }.toString()

    const val REMOVE = "null"
}
