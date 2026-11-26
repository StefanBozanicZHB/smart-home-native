package zhb.company.smart_home.domain.wall

import java.time.LocalDate
import java.time.ZoneId
import zhb.company.smart_home.data.history.HistoryDoc
import zhb.company.smart_home.data.history.HistorySeriesNames

/** One heat pump run, [startMs] to [endMs] (phone clock). */
data class PumpRun(val startMs: Long, val endMs: Long)

/** Today's heat pump runs for the 0-24 h strip, with their total and count. */
data class HeatingToday(val runs: List<PumpRun>, val totalMinutes: Long, val cycles: Int)

/**
 * Heat pump history for the wall, from the RPi history (`pump`: share of each point the pump ran)
 * and, for the minutes the history has not caught up with yet, the run in progress.
 */
object HeatingDay {
    /** A point counts as running when the pump ran at least half of it. */
    private const val ON_SHARE = 0.5f

    /**
     * Runs between [dayStartMs] and [nowMs]. [runningSinceMs] is the start of the run in progress
     * (null when the pump is off or the start is unknown).
     */
    fun today(day: HistoryDoc?, dayStartMs: Long, nowMs: Long, runningSinceMs: Long?): HeatingToday {
        val runs = mutableListOf<PumpRun>()
        val pump = day?.get(HistorySeriesNames.PUMP)?.avg
        if (day != null && pump != null) {
            day.times.forEachIndexed { i, start ->
                val end = day.times.getOrNull(i + 1) ?: day.endMs
                val share = pump.getOrNull(i) ?: return@forEachIndexed
                if (share < ON_SHARE) return@forEachIndexed
                val s = maxOf(start, dayStartMs)
                val e = minOf(end, nowMs)
                if (e > s) runs += PumpRun(s, e)
            }
        }
        if (runningSinceMs != null && nowMs > dayStartMs) runs += PumpRun(maxOf(runningSinceMs, dayStartMs), nowMs)
        val merged = merge(runs)
        return HeatingToday(merged, merged.sumOf { it.endMs - it.startMs } / 60_000, merged.size)
    }

    /** The end of the last run in the day history (last 24 h), or null. */
    fun lastRunEnd(day: HistoryDoc?): Long? {
        day ?: return null
        val pump = day[HistorySeriesNames.PUMP]?.avg ?: return null
        val i = pump.indexOfLast { (it ?: 0f) >= ON_SHARE }
        if (i < 0) return null
        return day.times.getOrNull(i + 1) ?: day.endMs
    }

    /** The last day in the month history on which the pump ran at all. */
    fun lastRunDay(month: HistoryDoc?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
        month ?: return null
        val series = month[HistorySeriesNames.PUMP] ?: return null
        val values = series.onHours ?: series.avg
        val i = values.indexOfLast { (it ?: 0f) > 0f }
        if (i < 0) return null
        return java.time.Instant.ofEpochMilli(month.times[i]).atZone(zone).toLocalDate()
    }

    /**
     * Out of season: the main switch is off, or the pump has not run for [quietDays] days (from
     * the month history; with no history the season is assumed on, so nothing is hidden by mistake).
     */
    fun outOfSeason(mainSwitchOn: Boolean?, running: Boolean, lastRunDay: LocalDate?, monthKnown: Boolean, today: LocalDate, quietDays: Long = 7): Boolean {
        if (mainSwitchOn == false) return true
        if (running || !monthKnown) return false
        return lastRunDay == null || lastRunDay.isBefore(today.minusDays(quietDays))
    }

    /** Outdoor minimum and maximum today, from the day history (point averages). */
    fun outdoorRange(day: HistoryDoc?, dayStartMs: Long): Pair<Double, Double>? {
        val values = day?.get(HistorySeriesNames.OUTDOOR_T)?.avg ?: return null
        val today = day.times.indices.filter { day.times[it] >= dayStartMs }.mapNotNull { values.getOrNull(it) }
        if (today.isEmpty()) return null
        return today.min().toDouble() to today.max().toDouble()
    }

    private fun merge(runs: List<PumpRun>): List<PumpRun> {
        val sorted = runs.sortedBy { it.startMs }
        val out = mutableListOf<PumpRun>()
        for (r in sorted) {
            val last = out.lastOrNull()
            if (last != null && r.startMs <= last.endMs) out[out.lastIndex] = PumpRun(last.startMs, maxOf(last.endMs, r.endMs))
            else out += r
        }
        return out
    }
}
