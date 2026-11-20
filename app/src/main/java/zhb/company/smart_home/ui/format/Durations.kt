package zhb.company.smart_home.ui.format

import java.util.Locale

/** Compact, language-neutral units for diagnostics (uptime, free memory). */
object Durations {

    /** 45 → "45 s", 750 → "12 min", 12_000 → "3 h 20 min", 450_000 → "5 d 5 h". */
    fun compact(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val days = s / 86_400
        val hours = s % 86_400 / 3_600
        val minutes = s % 3_600 / 60
        return when {
            days > 0 -> if (hours > 0) "$days d $hours h" else "$days d"
            hours > 0 -> if (minutes > 0) "$hours h $minutes min" else "$hours h"
            minutes > 0 -> "$minutes min"
            else -> "$s s"
        }
    }

    /** 27_560 → "26.9 KB". */
    fun kilobytes(bytes: Long): String = String.format(Locale.US, "%.1f KB", bytes / 1024.0)
}
