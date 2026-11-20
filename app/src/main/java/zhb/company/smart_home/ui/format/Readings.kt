package zhb.company.smart_home.ui.format

import java.util.Locale
import kotlin.math.roundToInt

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
}
