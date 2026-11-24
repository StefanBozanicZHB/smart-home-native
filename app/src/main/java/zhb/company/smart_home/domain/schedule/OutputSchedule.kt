package zhb.company.smart_home.domain.schedule

import java.util.Locale
import zhb.company.smart_home.domain.garden.Weekday

/*
 * Daily on/off schedules of switched outputs (Node 1 fountain pump and light, Node 2 neon and
 * lamp), smart-home-mcu docs/mqtt_topics.md, "API conventions" → "Schedules".
 */

enum class SunEvent(val key: String) { SUNSET("sunset"), SUNRISE("sunrise") }

/** Start or end of a window: a fixed time of day, or sunset/sunrise with an offset. */
sealed interface ScheduleTime {
    /** [minuteOfDay] 0..1439, local time. */
    data class Fixed(val minuteOfDay: Int) : ScheduleTime

    /** [offsetMinutes] -[OFFSET_MAX]..[OFFSET_MAX]; positive = after the event. */
    data class Sun(val event: SunEvent, val offsetMinutes: Int = 0) : ScheduleTime

    /** The firmware's text: "23:30", "sunset", "sunset+20", "sunrise-30". */
    fun toWire(): String = when (this) {
        is Fixed -> String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)
        is Sun -> when {
            offsetMinutes == 0 -> event.key
            offsetMinutes > 0 -> "${event.key}+$offsetMinutes"
            else -> "${event.key}$offsetMinutes"
        }
    }

    companion object {
        const val OFFSET_MAX = 180
        private val FIXED = Regex("""^(\d{2}):(\d{2})$""")
        private val SUN = Regex("""^(sunset|sunrise)(?:([+-])(\d{1,3}))?$""")

        /** Null for anything the firmware would not accept. */
        fun parse(text: String?): ScheduleTime? {
            text ?: return null
            FIXED.matchEntire(text)?.let { m ->
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toInt()
                return if (h in 0..23 && min in 0..59) Fixed(h * 60 + min) else null
            }
            SUN.matchEntire(text)?.let { m ->
                val event = SunEvent.entries.first { it.key == m.groupValues[1] }
                val magnitude = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0
                val offset = if (m.groupValues[2] == "-") -magnitude else magnitude
                return if (offset in -OFFSET_MAX..OFFSET_MAX) Sun(event, offset) else null
            }
            return null
        }
    }
}

/** The window from [on] to [off], starting on [days]. */
data class OutputSchedule(
    val on: ScheduleTime,
    val off: ScheduleTime,
    val days: Set<Weekday> = Weekday.entries.toSet(),
) {
    companion object {
        /** Suggested when a schedule is switched on: 20 min after sunset until 23:30, every day. */
        val DEFAULT = OutputSchedule(ScheduleTime.Sun(SunEvent.SUNSET, 20), ScheduleTime.Fixed(23 * 60 + 30))
    }
}

/** Today's window as the node computed it, local "HH:MM". */
data class TodayWindow(val on: String, val off: String)

/** Why a schedule would be rejected; checked before sending (the node checks it again). */
enum class ScheduleProblem { NO_DAYS, OFFSET_RANGE, SAME_TIMES }

object ScheduleRules {
    fun validate(s: OutputSchedule): ScheduleProblem? = when {
        s.days.isEmpty() -> ScheduleProblem.NO_DAYS
        listOf(s.on, s.off).any { it is ScheduleTime.Sun && it.offsetMinutes !in -ScheduleTime.OFFSET_MAX..ScheduleTime.OFFSET_MAX } ->
            ScheduleProblem.OFFSET_RANGE
        s.on == s.off -> ScheduleProblem.SAME_TIMES
        else -> null
    }
}
