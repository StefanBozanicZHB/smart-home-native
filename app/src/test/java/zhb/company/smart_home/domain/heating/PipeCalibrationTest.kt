package zhb.company.smart_home.domain.heating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipeCalibrationTest {

    private val a = "28AAAAAAAAAAAA01"
    private val b = "28BBBBBBBBBBBB02"
    private val c = "28CCCCCCCCCCCC03"

    private val sensors = PipeSensors(
        rev = 1,
        assignments = mapOf(
            Pipe.SUPPLY to PipeAssignment(a, -50),
            Pipe.RETURN to PipeAssignment(b, 0),
            Pipe.WELL_IN to null,
            Pipe.WELL_OUT to null,
        ),
        fallback = false,
        bus = emptyList(),
    )

    private fun sample(ta: Double?, tb: Double?, tc: Double?) = listOf(
        BusSensor(a, ta, Pipe.SUPPLY), BusSensor(b, tb, Pipe.RETURN), BusSensor(c, tc, null),
    )

    @Test
    fun `offset is the mean of all sensors minus each sensor's mean`() {
        // means: a = 20.20, b = 19.90, c = 20.00 → reference 20.0333
        val result = PipeCalibration.compute(
            listOf(sample(20.1, 19.9, 20.0), sample(20.3, 19.9, 20.0)),
            sensors,
        ) as PipeCalibration.Result.Proposal

        assertEquals(20.0333, result.reference, 0.0001)
        assertEquals(0.3, result.spread, 0.0001)
        assertFalse(result.spreadTooLarge)
        assertEquals(listOf(Pipe.SUPPLY, Pipe.RETURN), result.rows.map { it.pipe })
        assertEquals(-17, result.rows[0].proposedOffsetHundredths) // 20.033 - 20.20
        assertEquals(-50, result.rows[0].currentOffsetHundredths)
        assertEquals(13, result.rows[1].proposedOffsetHundredths) // 20.033 - 19.90
        assertEquals(
            mapOf(Pipe.SUPPLY to PipeAssignment(a, -17), Pipe.RETURN to PipeAssignment(b, 13)),
            result.changes(),
        )
    }

    @Test
    fun `a sensor out of the cup shows up as a large spread`() {
        val result = PipeCalibration.compute(listOf(sample(20.0, 20.1, 25.0)), sensors) as PipeCalibration.Result.Proposal
        assertTrue(result.spreadTooLarge)
    }

    @Test
    fun `sensors missing in a sample are left out, offsets are limited to 5 degrees`() {
        val result = PipeCalibration.compute(
            listOf(sample(9.0, 21.0, null), sample(9.0, 21.0, 30.0)),
            sensors,
        ) as PipeCalibration.Result.Proposal
        assertEquals(15.0, result.reference, 0.0001) // c not in every sample
        assertEquals(500, result.rows[0].proposedOffsetHundredths)
        assertTrue(result.rows[0].clamped)
        assertEquals(-500, result.rows[1].proposedOffsetHundredths)
        assertTrue(result.rows[1].clamped)
    }

    @Test
    fun `fewer than two sensors cannot be calibrated`() {
        assertEquals(PipeCalibration.Result.TooFewSensors, PipeCalibration.compute(listOf(sample(20.0, null, null)), sensors))
        assertEquals(PipeCalibration.Result.TooFewSensors, PipeCalibration.compute(emptyList(), sensors))
    }
}
