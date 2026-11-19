package zhb.company.smart_home.domain

import zhb.company.smart_home.domain.model.HeatingSegment

/**
 * Encodes/decodes the fixed-width heating (boiler) weekday/weekend schedule
 * string used on node5.
 *
 * Status topics (`.../planogram/provera/10` for workday, `.../provera/00`
 * for weekend) deliver a payload of: 1 digit segment count, followed by that
 * many repetitions of a 4-digit start time (HHMM, no colon) + 2-digit
 * temperature.
 *
 * The single control topic (`.../planogram`) is shared by both schedules, so
 * publishing prepends a '1' (workday) or '0' (weekend) selector digit ahead
 * of the same segment-count + repeating-segment encoding.
 */
object HeatingScheduleCodec {
    const val MAX_SEGMENTS = 6

    fun decode(payload: String): List<HeatingSegment> {
        if (payload.isEmpty()) return emptyList()
        val count = payload[0].digitToIntOrNull()?.coerceIn(0, MAX_SEGMENTS) ?: return emptyList()
        val segments = mutableListOf<HeatingSegment>()
        var cursor = 1
        repeat(count) {
            if (cursor + 6 > payload.length) return@repeat
            val hhmm = payload.substring(cursor, cursor + 4)
            val temp = payload.substring(cursor + 4, cursor + 6).toIntOrNull() ?: 20
            val time = "${hhmm.substring(0, 2)}:${hhmm.substring(2, 4)}"
            segments += HeatingSegment(time = time, temp = temp)
            cursor += 6
        }
        return segments
    }

    fun encode(isWorkday: Boolean, segments: List<HeatingSegment>): String {
        val clamped = segments.take(MAX_SEGMENTS)
        val prefix = if (isWorkday) "1" else "0"
        val body = buildString {
            append(clamped.size)
            clamped.forEach { segment ->
                val parts = segment.time.split(":")
                val hh = parts.getOrElse(0) { "00" }.padStart(2, '0')
                val mm = parts.getOrElse(1) { "00" }.padStart(2, '0')
                append(hh)
                append(mm)
                append(segment.temp.toString().padStart(2, '0'))
            }
        }
        return prefix + body
    }
}
