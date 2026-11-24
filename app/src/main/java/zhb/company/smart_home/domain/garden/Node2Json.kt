package zhb.company.smart_home.domain.garden

import zhb.company.smart_home.domain.schedule.OutputSchedule
import zhb.company.smart_home.domain.schedule.ScheduleJson
import zhb.company.smart_home.domain.schedule.TodayWindow
import zhb.company.smart_home.domain.node.bool
import zhb.company.smart_home.domain.node.int
import zhb.company.smart_home.domain.node.long
import zhb.company.smart_home.domain.node.obj
import zhb.company.smart_home.domain.node.parseJsonObject

/**
 * Retained `node2/state` (yard, smart-home-mcu docs/mqtt_topics.md, section Node 2): the two lights
 * with their automatic switch-off timers, today's schedule windows and whether the DS18B20 answered.
 */
data class Node2State(
    val seq: Long,
    val uptimeSeconds: Long,
    val neon: Boolean,
    val neonLeftSeconds: Int?,
    val lamp: Boolean,
    val lampLeftSeconds: Int?,
    /** The outdoor temperature sensor answered in the last cycle; null if not reported. */
    val sensorOk: Boolean?,
    /** Today's schedule window per light (`neon_today`, `lamp_today`); null = no window today. */
    val today: Map<String, TodayWindow?> = emptyMap(),
)

/**
 * Retained `node2/config` (`node2-2.1.0`): the schedule of each light, keyed by output name
 * ("neon", "lamp"); null = manual only. Older firmware publishes no config, so no schedules.
 */
data class Node2Config(val rev: Long, val schedules: Map<String, OutputSchedule?>)

object Node2Json {
    fun parseState(payload: String): Node2State? {
        val o = parseJsonObject(payload) ?: return null
        return Node2State(
            seq = o.long("seq") ?: return null,
            uptimeSeconds = o.long("up") ?: return null,
            neon = o.bool("neon") ?: false,
            neonLeftSeconds = o.int("neon_left"),
            lamp = o.bool("lamp") ?: false,
            lampLeftSeconds = o.int("lamp_left"),
            sensorOk = o.bool("ds18b20"),
            today = o.keys.filter { it.endsWith(TODAY_SUFFIX) }.associate { it.removeSuffix(TODAY_SUFFIX) to ScheduleJson.parseToday(o[it]) },
        )
    }

    fun parseConfig(payload: String): Node2Config? {
        val o = parseJsonObject(payload) ?: return null
        val rev = o.long("rev") ?: return null
        val schedules = o.obj("schedules") ?: return null
        return Node2Config(rev, ScheduleJson.parseSchedules(schedules))
    }

    private const val TODAY_SUFFIX = "_today"
}

/** Node 2 lights for the home "devices on" list and GoodNight. */
object Node2Devices {
    const val NEON = "LED traka (dvorište)"
    const val LAMP = "Sijalica (dvorište)"

    fun onLabels(state: Node2State?): List<String> {
        state ?: return emptyList()
        return buildList {
            if (state.neon) add(NEON)
            if (state.lamp) add(LAMP)
        }
    }
}
