package zhb.company.smart_home.domain.garden

import java.time.LocalDate
import java.time.format.DateTimeParseException
import zhb.company.smart_home.domain.heating.TimeOfDay

/** The watering plan as edited on the plan screen; saved as a [Node1ConfigChange]. */
data class IrrigationDraft(
    val auto: Boolean,
    val days: Set<Weekday>,
    val starts: List<String>,
    val zones: List<Int>,
    val soak: Soak,
    val pauseUntil: String?,
) {
    companion object {
        fun from(config: Node1Config) = IrrigationDraft(
            auto = config.auto,
            days = config.days,
            starts = config.starts,
            zones = config.zones,
            soak = config.soak,
            pauseUntil = config.pauseUntil,
        )
    }
}

/** Why the firmware would reject a plan; checked before sending (mirrors irrigation_config.cpp). */
sealed interface IrrigationProblem {
    data class TooManyStarts(val max: Int) : IrrigationProblem
    data class BadStart(val value: String) : IrrigationProblem
    data class DuplicateStart(val value: String) : IrrigationProblem
    data class ZoneMinutes(val zone: Int, val max: Int) : IrrigationProblem
    data class SoakOn(val max: Int) : IrrigationProblem
    data class SoakOff(val max: Int) : IrrigationProblem
    data object BadPauseDate : IrrigationProblem
}

object IrrigationRules {

    fun validate(draft: IrrigationDraft, limits: Node1Limits): List<IrrigationProblem> = buildList {
        if (draft.starts.size > limits.startsMax) add(IrrigationProblem.TooManyStarts(limits.startsMax))
        draft.starts.filter { TimeOfDay.parse(it) == null }.forEach { add(IrrigationProblem.BadStart(it)) }
        draft.starts.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { add(IrrigationProblem.DuplicateStart(it)) }
        draft.zones.forEachIndexed { i, m -> if (m !in 0..limits.zoneMinutesMax) add(IrrigationProblem.ZoneMinutes(i + 1, limits.zoneMinutesMax)) }
        if (draft.soak.onMinutes !in 0..limits.soakOnMax) add(IrrigationProblem.SoakOn(limits.soakOnMax))
        if (draft.soak.offMinutes !in 0..limits.soakOffMax) add(IrrigationProblem.SoakOff(limits.soakOffMax))
        if (draft.pauseUntil != null && parseDate(draft.pauseUntil) == null) add(IrrigationProblem.BadPauseDate)
    }

    /** Only what differs from the node's config; start times compared as the node stores them (sorted). */
    fun changes(config: Node1Config, draft: IrrigationDraft): Node1ConfigChange = Node1ConfigChange(
        auto = draft.auto.takeIf { it != config.auto },
        days = draft.days.takeIf { it != config.days },
        starts = sortedStarts(draft.starts).takeIf { it != sortedStarts(config.starts) },
        zones = draft.zones.takeIf { it != config.zones },
        soak = draft.soak.takeIf { it != config.soak },
        pauseUntil = PauseUntil(draft.pauseUntil).takeIf { draft.pauseUntil != config.pauseUntil },
    )

    fun sortedStarts(starts: List<String>): List<String> = starts.sortedBy { TimeOfDay.parse(it) ?: Int.MAX_VALUE }

    /** Adds a start time (default: one hour after the last, or 06:00); null if the limit is reached. */
    fun addStart(starts: List<String>, limits: Node1Limits): List<String>? {
        if (starts.size >= limits.startsMax) return null
        val last = starts.mapNotNull(TimeOfDay::parse).maxOrNull()
        var candidate = last?.let { (it + 60) % (24 * 60) } ?: (6 * 60)
        val taken = starts.mapNotNull(TimeOfDay::parse).toSet()
        while (candidate in taken) candidate = (candidate + 15) % (24 * 60)
        return sortedStarts(starts + TimeOfDay.format(candidate))
    }

    /** "Pause for [days] days" from [today]: the last paused date, inclusive (1 = today only). */
    fun pauseFor(today: LocalDate, days: Int): String = today.plusDays((days - 1).coerceAtLeast(0).toLong()).toString()

    /** True while [pauseUntil] is today or later. */
    fun isPaused(pauseUntil: String?, today: LocalDate): Boolean = parseDate(pauseUntil)?.let { !it.isBefore(today) } == true

    fun parseDate(value: String?): LocalDate? = try {
        value?.let(LocalDate::parse)
    } catch (_: DateTimeParseException) {
        null
    }

    /** Minutes of one scheduled run: all zones with minutes. */
    fun runMinutes(zones: List<Int>): Int = zones.sum()
}
