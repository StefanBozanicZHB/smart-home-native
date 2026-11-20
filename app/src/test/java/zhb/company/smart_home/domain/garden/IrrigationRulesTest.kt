package zhb.company.smart_home.domain.garden

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrrigationRulesTest {

    private val config = Node1Config(
        rev = 4, auto = true, days = setOf(Weekday.MON, Weekday.WED, Weekday.FRI), starts = listOf("06:00", "20:30"),
        zones = listOf(10, 10, 0, 15, 10, 10), soak = Soak(5, 10), pauseUntil = null,
    )
    private val draft = IrrigationDraft.from(config)

    @Test
    fun `unchanged draft sends nothing, changes send only what differs`() {
        assertTrue(IrrigationRules.changes(config, draft).isEmpty)
        // reordered start times are the same plan
        assertTrue(IrrigationRules.changes(config, draft.copy(starts = listOf("20:30", "06:00"))).isEmpty)
        assertEquals(
            Node1ConfigChange(zones = listOf(10, 10, 5, 15, 10, 10), pauseUntil = PauseUntil("2026-11-22")),
            IrrigationRules.changes(config, draft.copy(zones = listOf(10, 10, 5, 15, 10, 10), pauseUntil = "2026-11-22")),
        )
        assertEquals(
            Node1ConfigChange(pauseUntil = PauseUntil(null)),
            IrrigationRules.changes(config.copy(pauseUntil = "2026-11-22"), draft),
        )
    }

    @Test
    fun `validation mirrors the firmware limits`() {
        assertTrue(IrrigationRules.validate(draft, config.limits).isEmpty())
        assertEquals(
            listOf(
                IrrigationProblem.TooManyStarts(4),
                IrrigationProblem.BadStart("25:00"),
                IrrigationProblem.DuplicateStart("06:00"),
                IrrigationProblem.ZoneMinutes(2, 60),
                IrrigationProblem.SoakOff(120),
            ),
            IrrigationRules.validate(
                draft.copy(starts = listOf("06:00", "06:00", "25:00", "07:00", "08:00"), zones = listOf(10, 61, 0, 0, 0, 0), soak = Soak(5, 121)),
                config.limits,
            ),
        )
        assertEquals(listOf(IrrigationProblem.BadPauseDate), IrrigationRules.validate(draft.copy(pauseUntil = "22.11."), config.limits))
    }

    @Test
    fun `adding start times`() {
        assertEquals(listOf("06:00"), IrrigationRules.addStart(emptyList(), config.limits))
        assertEquals(listOf("06:00", "20:30", "21:30"), IrrigationRules.addStart(config.starts, config.limits))
        assertEquals(listOf("00:30", "23:00", "23:15", "23:30"), IrrigationRules.addStart(listOf("23:00", "23:15", "23:30"), config.limits))
        // the next free slot after a taken one
        assertEquals(listOf("00:00", "00:15", "23:00"), IrrigationRules.addStart(listOf("00:00", "23:00"), config.limits))
        assertNull(IrrigationRules.addStart(listOf("01:00", "02:00", "03:00", "04:00"), config.limits))
    }

    @Test
    fun `pause dates`() {
        val today = LocalDate.of(2026, 11, 20)
        assertEquals("2026-11-20", IrrigationRules.pauseFor(today, 1))
        assertEquals("2026-11-22", IrrigationRules.pauseFor(today, 3))
        assertTrue(IrrigationRules.isPaused("2026-11-20", today))
        assertFalse(IrrigationRules.isPaused("2026-11-19", today))
        assertFalse(IrrigationRules.isPaused(null, today))
    }
}
