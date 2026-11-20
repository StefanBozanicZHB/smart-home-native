package zhb.company.smart_home.data.reports

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The app must read what das_report.py (DAS repository) writes to Firebase. */
class HeatingReportJsonTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test
    fun decodesReportProducedByThePythonGenerator() {
        val r = json.decodeFromString<HeatingReport>(SAMPLE)
        assertEquals("2026-11-19", r.date)
        assertEquals(7200L, r.pump.runSeconds)
        assertEquals(2, r.pump.starts)
        assertEquals(3600L, r.pump.cycles?.avgSeconds)
        assertEquals(21.0, r.zones.getValue("ground").room?.min)
        assertEquals(17.1, r.outdoor?.hdd)
        assertEquals(4.5, r.pipes.getValue("delta"), 1e-9)
        assertEquals(100.0, r.data.completenessPct)
    }

    @Test
    fun decodesIndexEntryWithNulls() {
        val s = json.decodeFromString<ReportSummary>(
            """{"run_s":7200,"starts":2,"room_min":null,"room_max":21.0,"safe_mode_s":0,"completeness_pct":99.5}""",
        )
        assertEquals(7200L, s.runSeconds)
        assertNull(s.roomMin)
        assertNull(s.outdoorMin)
    }

    private companion object {
        val SAMPLE = """{"version": 1, "node": "node5", "heated": true, "pump": {"run_s": 7200, "starts": 2, "start_src": {}, "cycles": {"count": 2, "avg_s": 3600, "min_s": 3600, "max_s": 3600}, "main_switch_on_s": 86400}, "zones": {"ground": {"room": {"min": 21.0, "avg": 21.0, "max": 21.0}, "target_avg": 21.5, "deviation_avg": -0.5, "below_target_s": 0, "demand_s": 7200}}, "outdoor": {"min": -2.0, "avg": 0.9, "max": 3.8, "hdd": 17.1}, "pipes_while_running": {"delta": 4.5, "return": 30.5, "supply": 35.0}, "safe_mode": {"total_s": 0, "reasons": {}}, "efficiency": {"run_h_per_hdd": 0.117}, "data": {"samples": 8640, "expected": 8640, "completeness_pct": 100.0, "reboots": 0, "config_revisions": [3]}, "date": "2026-11-19", "generated_at_ms": 1, "summary": "Pumpa 2 h, 2 paljenja. Soba 21.0–21.0 °C. Napolju -2.0–3.8 °C."}"""
    }
}
