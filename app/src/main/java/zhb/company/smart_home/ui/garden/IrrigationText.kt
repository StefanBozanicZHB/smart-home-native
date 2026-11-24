package zhb.company.smart_home.ui.garden

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.garden.IrrigationRules
import zhb.company.smart_home.domain.garden.NextStart
import zhb.company.smart_home.domain.garden.RunEvent
import zhb.company.smart_home.domain.garden.RunEventType
import zhb.company.smart_home.domain.garden.RunSource
import zhb.company.smart_home.domain.garden.SkipReason
import zhb.company.smart_home.domain.garden.Weekday
import zhb.company.smart_home.ui.format.Durations

/** "pon", "uto", ... */
@Composable
fun weekdayShort(day: Weekday): String = stringResource(
    when (day) {
        Weekday.MON -> R.string.weekday_mon
        Weekday.TUE -> R.string.weekday_tue
        Weekday.WED -> R.string.weekday_wed
        Weekday.THU -> R.string.weekday_thu
        Weekday.FRI -> R.string.weekday_fri
        Weekday.SAT -> R.string.weekday_sat
        Weekday.SUN -> R.string.weekday_sun
    },
)

/** "danas u 20:30", "sutra u 06:00", "pon u 06:00". */
@Composable
fun nextStartText(next: NextStart): String = when {
    next.inDays == 0 -> stringResource(R.string.irrigation_next_today, next.time)
    next.inDays == 1 -> stringResource(R.string.irrigation_next_tomorrow, next.time)
    next.day != null -> stringResource(R.string.irrigation_next_day, weekdayShort(next.day), next.time)
    else -> pluralStringResource(R.plurals.irrigation_next_in_days, next.inDays, next.inDays, next.time)
}

/** "2026-11-22" → "22.11."; unparsable dates are shown as sent. */
fun shortDate(date: String): String = IrrigationRules.parseDate(date)?.let { "${it.dayOfMonth}.${it.monthValue}." } ?: date

/** 95 → "1 min 35 s"-like compact text; under a minute in seconds. */
fun leftText(seconds: Int): String = Durations.compact(seconds.toLong())

/** The last run event in one line, e.g. "Preskočeno 06:00 · pauza zbog kiše". */
@Composable
fun lastRunText(event: RunEvent): String {
    val what = stringResource(
        when (event.event) {
            RunEventType.STARTED -> R.string.irrigation_event_started
            RunEventType.FINISHED -> R.string.irrigation_event_finished
            RunEventType.STOPPED -> R.string.irrigation_event_stopped
            RunEventType.REPLACED -> R.string.irrigation_event_replaced
            RunEventType.SKIPPED -> R.string.irrigation_event_skipped
        },
    )
    val source = when {
        event.start != null -> event.start
        event.source == RunSource.APP -> stringResource(R.string.irrigation_source_app)
        else -> null
    }
    val reason = event.reason?.let {
        stringResource(
            when (it) {
                SkipReason.BUSY -> R.string.irrigation_skip_busy
                SkipReason.NO_ZONES -> R.string.irrigation_skip_no_zones
                SkipReason.PAUSED -> R.string.irrigation_skip_paused
            },
        )
    }
    return listOfNotNull("$what${source?.let { " $it" }.orEmpty()}", reason).joinToString(" · ")
}
