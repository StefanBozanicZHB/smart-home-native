package zhb.company.smart_home.domain.heating

import org.junit.Assert.assertEquals
import org.junit.Test

class HeatPumpStatusTest {

    private val config = Node5Config(rev = 1, mainSwitch = true, zones = listOf("ground"), minOnSeconds = 600, minOffSeconds = 600, limits = Node5Limits())

    private fun live(pump: Boolean, demand: Boolean, up: Long = 10_000, time: String? = "21:35", mainSwitch: Boolean = true, safe: String? = null) =
        Node5LiveState(
            seq = 1, uptimeSeconds = up, time = time, weekend = false, mainSwitch = mainSwitch, mainRelay = mainSwitch,
            thermoRelay = pump, pump = pump, demand = demand, safeReason = safe, pipes = PipeReadings(), configRev = 1, zones = emptyMap(),
        )

    private fun pumpEvent(on: Boolean, at: Long) = PumpEvent(seq = 3, atUptimeSeconds = at, pump = on, source = "thermostat", pipes = PipeReadings())

    @Test
    fun `running with demand shows how long`() {
        assertEquals(
            HeatPumpStatus.Running(minutes = 42, demand = true, minRunLeftMinutes = null),
            HeatPumpStatus.from(true, config, live(pump = true, demand = true), pumpEvent(on = true, at = 10_000 - 42 * 60), null),
        )
    }

    @Test
    fun `target reached but still in the minimum run`() {
        // started 7 min ago, minimum run 10 min → about 3 min left
        assertEquals(
            HeatPumpStatus.Running(minutes = 7, demand = false, minRunLeftMinutes = 3),
            HeatPumpStatus.from(true, config, live(pump = true, demand = false), pumpEvent(on = true, at = 10_000 - 7 * 60), null),
        )
    }

    @Test
    fun `demand during the minimum pause waits`() {
        // stopped 5 min 30 s ago, minimum pause 10 min → starts in about 5 min (rounded up)
        assertEquals(
            HeatPumpStatus.Waiting(startsInMinutes = 5),
            HeatPumpStatus.from(true, config, live(pump = false, demand = true), pumpEvent(on = false, at = 10_000 - 330), null),
        )
        // pause over: starting any moment
        assertEquals(
            HeatPumpStatus.Waiting(startsInMinutes = null),
            HeatPumpStatus.from(true, config, live(pump = false, demand = true), pumpEvent(on = false, at = 1_000), null),
        )
    }

    @Test
    fun `idle since the node time of the last stop`() {
        assertEquals(
            HeatPumpStatus.Idle(sinceTime = "21:10"),
            HeatPumpStatus.from(true, config, live(pump = false, demand = false), pumpEvent(on = false, at = 10_000 - 25 * 60), null),
        )
        assertEquals(
            HeatPumpStatus.Idle(sinceTime = "23:50"),
            HeatPumpStatus.from(true, config, live(pump = false, demand = false, time = "00:10"), pumpEvent(on = false, at = 10_000 - 20 * 60), null),
        )
        assertEquals(HeatPumpStatus.Idle(sinceTime = null), HeatPumpStatus.from(true, config, live(pump = false, demand = false), null, null))
    }

    @Test
    fun `main switch off, safe mode and unknown`() {
        assertEquals(HeatPumpStatus.Off, HeatPumpStatus.from(true, config.copy(mainSwitch = false), live(pump = false, demand = true, mainSwitch = false), null, null))
        assertEquals(
            HeatPumpStatus.SafeMode("room_temp_stale"),
            HeatPumpStatus.from(true, config, live(pump = false, demand = false, safe = "room_temp_stale"), null, SafeModeState(true, "room_temp_stale", "ground", 7200)),
        )
        assertEquals(HeatPumpStatus.Unknown, HeatPumpStatus.from(false, config, live(pump = true, demand = true), null, null))
        assertEquals(HeatPumpStatus.Unknown, HeatPumpStatus.from(true, config, null, null, null))
    }

    @Test
    fun `safe mode entered while running shows running until the minimum run ends`() {
        val status = HeatPumpStatus.from(true, config, live(pump = true, demand = false, safe = "mqtt_down"), pumpEvent(on = true, at = 10_000 - 120), null)
        assertEquals(HeatPumpStatus.Running(minutes = 2, demand = false, minRunLeftMinutes = 8), status)
    }
}
