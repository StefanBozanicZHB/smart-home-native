package zhb.company.smart_home.domain.heating

import java.util.Locale
import kotlin.math.abs

const val MINUTES_PER_DAY = 24 * 60

/** "HH:MM" <-> minutes since midnight. */
object TimeOfDay {
    private val pattern = Regex("""^(\d{2}):(\d{2})$""")

    fun parse(value: String?): Int? {
        val match = pattern.matchEntire(value ?: return null) ?: return null
        val hours = match.groupValues[1].toInt()
        val minutes = match.groupValues[2].toInt()
        if (hours > 23 || minutes > 59) return null
        return hours * 60 + minutes
    }

    /** 390 -> "06:30"; 1440 -> "24:00" (only used as a display label for the end of the day). */
    fun format(minutes: Int): String {
        val m = minutes.coerceIn(0, MINUTES_PER_DAY)
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
    }
}

/** 215 -> "21.5", -5 -> "-0.5". */
fun formatTenths(tenths: Int): String {
    val sign = if (tenths < 0) "-" else ""
    return "$sign${abs(tenths) / 10}.${abs(tenths) % 10}"
}

/** Why a day program or hysteresis would be rejected. Mirrors the firmware's checks (heating_config.cpp). */
sealed interface ScheduleProblem {
    data class SegmentCount(val max: Int) : ScheduleProblem
    data object FirstNotMidnight : ScheduleProblem
    data class StartsNotRising(val index: Int) : ScheduleProblem
    data class TempOutOfRange(val index: Int, val minTenths: Int, val maxTenths: Int) : ScheduleProblem
    data class HysteresisOutOfRange(val maxTenths: Int) : ScheduleProblem
}

object ScheduleRules {

    fun validateDay(day: List<ScheduleSegment>, limits: Node5Limits): ScheduleProblem? {
        if (day.isEmpty() || day.size > limits.segmentsMax) return ScheduleProblem.SegmentCount(limits.segmentsMax)
        if (day.first().startMinute != 0) return ScheduleProblem.FirstNotMidnight
        day.forEachIndexed { i, segment ->
            if (segment.startMinute !in 0 until MINUTES_PER_DAY || (i > 0 && segment.startMinute <= day[i - 1].startMinute)) {
                return ScheduleProblem.StartsNotRising(i)
            }
            if (segment.tempTenths !in limits.tempMinTenths..limits.tempMaxTenths) {
                return ScheduleProblem.TempOutOfRange(i, limits.tempMinTenths, limits.tempMaxTenths)
            }
        }
        return null
    }

    fun validateHysteresis(h: Hysteresis, limits: Node5Limits): ScheduleProblem? {
        val range = 0..limits.stepMaxTenths
        return if (h.lowerTenths in range && h.upperTenths in range) null
        else ScheduleProblem.HysteresisOutOfRange(limits.stepMaxTenths)
    }

    /** End of segment [index] in minutes: the next segment's start, or 24:00 for the last one. */
    fun endMinute(day: List<ScheduleSegment>, index: Int): Int =
        if (index >= day.lastIndex) MINUTES_PER_DAY else day[index + 1].startMinute

    /** Index of the segment in force at [minute] (minutes since midnight), or -1 for an empty day. */
    fun activeIndex(day: List<ScheduleSegment>, minute: Int): Int = day.indexOfLast { it.startMinute <= minute }

    /** The next segment that starts today after [minute], or null if the current one lasts until midnight. */
    fun nextChange(day: List<ScheduleSegment>, minute: Int): ScheduleSegment? = day.firstOrNull { it.startMinute > minute }

    /** Starting point for a day that has no program yet: one segment, whole day. */
    fun defaultDay(limits: Node5Limits): List<ScheduleSegment> =
        listOf(ScheduleSegment(0, 200.coerceIn(limits.tempMinTenths, limits.tempMaxTenths)))

    /**
     * Adds a segment by splitting the longest one at its midpoint (rounded down to 30 minutes),
     * keeping its temperature. Returns the day unchanged if it is full or no segment can be split.
     */
    fun addSegment(day: List<ScheduleSegment>, limits: Node5Limits): List<ScheduleSegment> {
        if (day.isEmpty()) return defaultDay(limits)
        if (day.size >= limits.segmentsMax) return day
        val longest = day.indices.maxBy { endMinute(day, it) - day[it].startMinute }
        val start = day[longest].startMinute
        val end = endMinute(day, longest)
        if (end - start < 2) return day
        val split = (((start + end) / 2) / 30 * 30).coerceIn(start + 1, end - 1)
        return day.toMutableList().apply { add(longest + 1, ScheduleSegment(split, day[longest].tempTenths)) }
    }

    /** Removes segment [index]; the previous segment grows to cover it. The first segment stays at 00:00. */
    fun removeSegment(day: List<ScheduleSegment>, index: Int): List<ScheduleSegment> {
        if (day.size <= 1 || index !in day.indices) return day
        val result = day.toMutableList().apply { removeAt(index) }
        if (result.first().startMinute != 0) result[0] = result[0].copy(startMinute = 0)
        return result
    }

    fun setStart(day: List<ScheduleSegment>, index: Int, minute: Int): List<ScheduleSegment> {
        if (index <= 0 || index !in day.indices) return day // the first segment always starts at 00:00
        return day.toMutableList().apply { this[index] = this[index].copy(startMinute = minute) }
    }

    fun setTemp(day: List<ScheduleSegment>, index: Int, tempTenths: Int, limits: Node5Limits): List<ScheduleSegment> {
        if (index !in day.indices) return day
        val clamped = tempTenths.coerceIn(limits.tempMinTenths, limits.tempMaxTenths)
        return day.toMutableList().apply { this[index] = this[index].copy(tempTenths = clamped) }
    }
}
