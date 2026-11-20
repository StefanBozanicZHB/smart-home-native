package zhb.company.smart_home.data.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Bars with an optional line on a second axis, e.g. pump run time per day and the outdoor mean. */
data class BarChartData(
    val starts: List<Long>,
    val endMs: Long,
    val bars: List<Float?>,
    val line: List<Float?>,
    val barUnit: BarUnit,
) {
    val hasBars: Boolean get() = bars.any { it != null }
}

enum class BarUnit { MINUTES, HOURS }

/** [ratio] is null when it was too warm to compare (under 1 degree-day). */
data class DegreeDays(val runHours: Float, val degreeDays: Float, val ratio: Float?)

/** Turns history documents into what a chart draws. Pure, so it is unit tested. */
object HistoryCharts {

    private const val HOUR_MS = 3_600_000L

    /**
     * Heat pump run time:
     * - [HistoryPeriod.DAY]: minutes per hour over the last 24 hours (from the 10-minute `day`);
     * - [HistoryPeriod.WEEK] / [HistoryPeriod.MONTH]: hours per day over 7 / 30 days (`month`).
     * The line is the mean outdoor temperature of the same hour or day.
     */
    fun pumpRuns(period: HistoryPeriod, day: HistoryDoc?, month: HistoryDoc?): BarChartData? = when (period) {
        HistoryPeriod.DAY -> day?.let { hourlyPump(it) }
        HistoryPeriod.WEEK -> month?.let { dailyPump(it, 7) }
        HistoryPeriod.MONTH -> month?.let { dailyPump(it, 30) }
    }

    private fun hourlyPump(day: HistoryDoc): BarChartData? {
        val pump = day[HistorySeriesNames.PUMP] ?: return null
        val outdoor = day[HistorySeriesNames.OUTDOOR_T]
        val stepMin = if (day.times.size > 1) (day.times[1] - day.times[0]) / 60_000f else 10f
        // Local hours: the zone offset is a whole number of hours here, so epoch hours line up.
        val groups = day.times.indices.groupBy { day.times[it] / HOUR_MS }.toSortedMap().entries.toList().takeLast(24)
        val bars = groups.map { (_, idx) ->
            val v = idx.mapNotNull { pump.avg[it] }
            if (v.isEmpty()) null else v.sum() * stepMin
        }
        val line = groups.map { (_, idx) -> mean(idx.mapNotNull { outdoor?.avg?.getOrNull(it) }) }
        val starts = groups.map { it.key * HOUR_MS }
        return BarChartData(starts, minOf(day.endMs, (groups.last().key + 1) * HOUR_MS), bars, line, BarUnit.MINUTES)
    }

    private fun dailyPump(month: HistoryDoc, days: Int): BarChartData? {
        val pump = month[HistorySeriesNames.PUMP] ?: return null
        val from = (month.times.size - days).coerceAtLeast(0)
        val hours = pump.onHours ?: return null
        val outdoor = month[HistorySeriesNames.OUTDOOR_T]?.avg
        return BarChartData(
            starts = month.times.subList(from, month.times.size),
            endMs = month.endMs,
            bars = hours.subList(from, hours.size),
            line = (from until month.times.size).map { outdoor?.getOrNull(it) },
            barUnit = BarUnit.HOURS,
        )
    }

    /** Last [days] local days of the node's availability (share online, null = not logged). */
    fun availability(month: HistoryDoc?, node: String, days: Int = 7, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, Float?>> {
        month ?: return emptyList()
        val values = month[HistorySeriesNames.online(node)]?.avg ?: return emptyList()
        val from = (month.times.size - days).coerceAtLeast(0)
        return (from until month.times.size).map { i ->
            Instant.ofEpochMilli(month.times[i]).atZone(zone).toLocalDate() to values.getOrNull(i)
        }
    }

    /** Point by point [a] − [b] (e.g. supply − return); null where either is missing. */
    fun delta(doc: HistoryDoc?, a: String, b: String): List<Float?>? {
        val x = doc?.get(a)?.avg ?: return null
        val y = doc[b]?.avg ?: return null
        val d = x.indices.map { i -> val p = x[i]; val q = y.getOrNull(i); if (p != null && q != null) p - q else null }
        return d.takeIf { v -> v.any { it != null } }
    }

    /**
     * Pump run hours per heating degree-day over the last [days] days of `month`:
     * degree-days = Σ max(0, [baseC] − mean outdoor of the day), as in the daily report. Lower is
     * better (less running for the same cold). Days without outdoor data are left out of both sums.
     */
    fun hoursPerDegreeDay(month: HistoryDoc?, days: Int, baseC: Float = HDD_BASE_C): DegreeDays? {
        val hours = month?.get(HistorySeriesNames.PUMP)?.onHours ?: return null
        val outdoor = month[HistorySeriesNames.OUTDOOR_T]?.avg ?: return null
        val from = (month.times.size - days).coerceAtLeast(0)
        var h = 0f
        var dd = 0f
        for (i in from until month.times.size) {
            val o = outdoor.getOrNull(i) ?: continue
            val run = hours.getOrNull(i) ?: continue
            h += run
            dd += (baseC - o).coerceAtLeast(0f)
        }
        return DegreeDays(h, dd, if (dd >= 1f) h / dd else null)
    }

    const val HDD_BASE_C = 18f

    /** Most recent value of a series, with the start time of its point. */
    fun latest(doc: HistoryDoc?, name: String): Pair<Long, Float>? {
        val s = doc?.get(name) ?: return null
        val i = s.avg.indexOfLast { it != null }
        return if (i < 0) null else doc.times[i] to s.avg[i]!!
    }

    private fun mean(v: List<Float>): Float? = if (v.isEmpty()) null else v.sum() / v.size
}
