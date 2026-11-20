package zhb.company.smart_home.domain.heating

import kotlin.math.abs

/** Limits from the firmware (node5/sensors/set). */
const val PIPE_OFFSET_MAX_HUNDREDTHS = 500

/** Builds `sensors/set` changes (pipe → new assignment, null = remove) from user actions. */
object PipeSensorRules {

    /**
     * Put [address] on [pipe]. A sensor already on another pipe moves (that pipe is cleared in the
     * same command) and keeps its offset, because the calibration belongs to the sensor; a sensor
     * new to the pipes starts at offset 0.
     */
    fun assign(sensors: PipeSensors, address: String, pipe: Pipe): Map<Pipe, PipeAssignment?> {
        val previousPipe = sensors.pipeOf(address)
        val offset = previousPipe?.let { sensors.assignment(it)?.offsetHundredths } ?: 0
        return buildMap {
            put(pipe, PipeAssignment(address.uppercase(), offset))
            if (previousPipe != null && previousPipe != pipe) put(previousPipe, null)
        }
    }

    fun remove(pipe: Pipe): Map<Pipe, PipeAssignment?> = mapOf(pipe to null)

    /** New offset for the sensor on [pipe]; null if no sensor is assigned there. */
    fun setOffset(sensors: PipeSensors, pipe: Pipe, offsetHundredths: Int): Map<Pipe, PipeAssignment?>? {
        val current = sensors.assignment(pipe) ?: return null
        return mapOf(pipe to current.copy(offsetHundredths = offsetHundredths))
    }

    fun isValidOffset(hundredths: Int): Boolean = abs(hundredths) <= PIPE_OFFSET_MAX_HUNDREDTHS

    /** "-0.12" / "0,5" / "+1" → hundredths, or null if not a number in −5.00…5.00 with at most 2 decimals. */
    fun parseOffset(text: String): Int? {
        val normalized = text.trim().replace(',', '.').removePrefix("+").replace('−', '-')
        if (!Regex("""^-?\d{1,2}(\.\d{1,2})?$""").matches(normalized)) return null
        val hundredths = Math.round(normalized.toDouble() * 100).toInt()
        return hundredths.takeIf(::isValidOffset)
    }

    /** -12 → "-0.12", 5 → "0.05", 0 → "0.00". */
    fun formatOffset(hundredths: Int): String {
        val sign = if (hundredths < 0) "-" else ""
        val a = abs(hundredths)
        return "$sign${a / 100}.${(a % 100).toString().padStart(2, '0')}"
    }

    /** "28FF4C1D6116038F" → "28…16038F": family code and the last bytes are enough to tell sensors apart. */
    fun shortAddress(address: String): String =
        if (address.length <= 10) address else "${address.take(2)}…${address.takeLast(6)}"
}
