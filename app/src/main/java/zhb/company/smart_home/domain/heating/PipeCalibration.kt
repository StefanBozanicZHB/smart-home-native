package zhb.company.smart_home.domain.heating

import kotlin.math.roundToInt

/**
 * Calibration with all sensors in the same cup of water: they should read the same, so each
 * assigned sensor's offset becomes (mean of all sensors) − (its own mean raw reading).
 */
object PipeCalibration {

    /** Spread between sensors above which one is probably not in the water (or not settled). */
    const val MAX_SPREAD_TENTHS = 20

    data class Row(
        val pipe: Pipe,
        val address: String,
        val meanRaw: Double,
        val currentOffsetHundredths: Int,
        val proposedOffsetHundredths: Int,
        /** The exact offset was outside −5.00…5.00 and was limited. */
        val clamped: Boolean,
    )

    sealed interface Result {
        /** Fewer than two sensors with readings in every sample: nothing to compare against. */
        data object TooFewSensors : Result

        data class Proposal(
            /** Mean of all sensors, °C. */
            val reference: Double,
            /** Highest minus lowest sensor mean, °C. */
            val spread: Double,
            val rows: List<Row>,
        ) : Result {
            val spreadTooLarge: Boolean get() = spread * 10 > MAX_SPREAD_TENTHS

            /** sensors/set changes for the assigned pipes. */
            fun changes(): Map<Pipe, PipeAssignment?> =
                rows.associate { it.pipe to PipeAssignment(it.address, it.proposedOffsetHundredths) }
        }
    }

    /**
     * [samples] are snapshots of the bus (raw readings, no offsets) taken a few seconds apart;
     * [sensors] gives the current assignment. Only sensors read successfully in every sample count.
     */
    fun compute(samples: List<List<BusSensor>>, sensors: PipeSensors): Result {
        if (samples.isEmpty()) return Result.TooFewSensors
        val addresses = samples.map { s -> s.filter { it.rawTemp != null }.map { it.address }.toSet() }.reduce { a, b -> a intersect b }
        if (addresses.size < 2) return Result.TooFewSensors
        val means = addresses.associateWith { address ->
            samples.map { s -> s.first { it.address == address }.rawTemp!! }.average()
        }
        val reference = means.values.average()
        val rows = Pipe.entries.mapNotNull { pipe ->
            val assignment = sensors.assignment(pipe) ?: return@mapNotNull null
            val mean = means[assignment.address] ?: return@mapNotNull null
            val exact = ((reference - mean) * 100).roundToInt()
            val limited = exact.coerceIn(-PIPE_OFFSET_MAX_HUNDREDTHS, PIPE_OFFSET_MAX_HUNDREDTHS)
            Row(pipe, assignment.address, mean, assignment.offsetHundredths, limited, clamped = limited != exact)
        }
        return Result.Proposal(reference, means.values.max() - means.values.min(), rows)
    }
}
