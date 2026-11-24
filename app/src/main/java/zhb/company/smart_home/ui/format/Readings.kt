package zhb.company.smart_home.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import zhb.company.smart_home.R

/** Placeholder for a reading that has not arrived or could not be parsed. */
const val NO_VALUE = "--"

/**
 * One display format for every reading in the app: temperatures with one decimal, humidity and
 * other percentages as whole numbers. Sensors send different precision ("21.48", "3.5", "nan");
 * the screens should not show that difference.
 */
object Readings {

    /** "21.48" -> "21.5"; "nan", "" or null -> "--". */
    fun temperature(raw: String?): String = temperature(raw?.trim()?.toDoubleOrNull())

    fun temperature(value: Double?): String =
        if (value == null || value.isNaN() || value.isInfinite()) NO_VALUE else String.format(Locale.US, "%.1f", value)

    /** "45.2" -> "45"; invalid -> "--". */
    fun percent(raw: String?): String {
        val value = raw?.trim()?.toDoubleOrNull() ?: return NO_VALUE
        return if (value.isNaN() || value.isInfinite()) NO_VALUE else value.roundToInt().toString()
    }

    /** When a reading last changed: "14:32" today, "23. 11. 14:32" on an earlier day. */
    fun updatedAt(ms: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(ms).atZone(zone)
        return at.format(if (at.toLocalDate() == today) TIME else DAY_TIME)
    }

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DAY_TIME = DateTimeFormatter.ofPattern("d. M. HH:mm")
}

/** "nije osveženo od 14:32" for a reading that stopped updating at [sinceMs]. */
@Composable
fun staleText(sinceMs: Long): String = stringResource(R.string.reading_stale, Readings.updatedAt(sinceMs))
