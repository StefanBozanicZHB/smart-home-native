package zhb.company.smart_home.domain.heating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Payloads from smart-home-mcu docs/mqtt_topics.md (Node 5, pipe sensors, commit 1a62d4e). */
class PipeSensorsTest {

    private val docExample = """{"rev":9,""" +
        """"pipes":{"supply":{"addr":"28FF4C1D6116038F","offset":-0.12},"return":null,"well_in":null,"well_out":null},""" +
        """"fallback":false,""" +
        """"bus":[{"addr":"28FF4C1D6116038F","t":34.37,"pipe":"supply"},{"addr":"28AA01020304A169","t":21.06,"pipe":null}]}"""

    private val sensors = Node5Json.parseSensors(docExample)!!

    @Test
    fun `parses assignment, fallback and bus`() {
        assertEquals(9L, sensors.rev)
        assertEquals(PipeAssignment("28FF4C1D6116038F", -12), sensors.assignment(Pipe.SUPPLY))
        assertNull(sensors.assignment(Pipe.RETURN))
        assertFalse(sensors.fallback)
        assertEquals(
            listOf(BusSensor("28FF4C1D6116038F", 34.37, Pipe.SUPPLY), BusSensor("28AA01020304A169", 21.06, null)),
            sensors.bus,
        )
        assertEquals(Pipe.SUPPLY, sensors.pipeOf("28ff4c1d6116038f"))
        assertTrue(sensors.onBus("28AA01020304A169"))
    }

    @Test
    fun `offset defaults to 0, failed reads are null, garbage is rejected`() {
        val s = Node5Json.parseSensors(
            """{"rev":1,"pipes":{"return":{"addr":"28aa01020304a169"}},"fallback":true,"bus":[{"addr":"28AA01020304A169","t":null,"pipe":"return"}]}""",
        )!!
        assertEquals(PipeAssignment("28AA01020304A169", 0), s.assignment(Pipe.RETURN))
        assertTrue(s.fallback)
        assertNull(s.bus.single().rawTemp)
        assertNull(Node5Json.parseSensors("[]"))
        assertNull(Node5Json.parseSensors("""{"rev":1}"""))
    }

    @Test
    fun `command sends only the changed pipes, null removes`() {
        val json = Node5Json.sensorsSetCommand(
            "x1",
            mapOf(Pipe.RETURN to null, Pipe.SUPPLY to PipeAssignment("28FF4C1D6116038F", -12)),
        )
        assertEquals("""{"pipes":{"supply":{"addr":"28FF4C1D6116038F","offset":-0.12},"return":null},"id":"x1"}""", json)
    }

    @Test
    fun `assigning a new sensor starts at offset 0`() {
        assertEquals(
            mapOf(Pipe.RETURN to PipeAssignment("28AA01020304A169", 0)),
            PipeSensorRules.assign(sensors, "28aa01020304a169", Pipe.RETURN),
        )
    }

    @Test
    fun `moving a sensor clears its old pipe and keeps its offset`() {
        assertEquals(
            mapOf(Pipe.WELL_IN to PipeAssignment("28FF4C1D6116038F", -12), Pipe.SUPPLY to null),
            PipeSensorRules.assign(sensors, "28FF4C1D6116038F", Pipe.WELL_IN),
        )
    }

    @Test
    fun `remove and offset changes`() {
        assertEquals(mapOf(Pipe.SUPPLY to null), PipeSensorRules.remove(Pipe.SUPPLY))
        assertEquals(
            mapOf(Pipe.SUPPLY to PipeAssignment("28FF4C1D6116038F", 35)),
            PipeSensorRules.setOffset(sensors, Pipe.SUPPLY, 35),
        )
        assertNull(PipeSensorRules.setOffset(sensors, Pipe.RETURN, 10))
    }

    @Test
    fun `offset text parsing and formatting`() {
        assertEquals(-12, PipeSensorRules.parseOffset("-0.12"))
        assertEquals(50, PipeSensorRules.parseOffset("0,5"))
        assertEquals(100, PipeSensorRules.parseOffset("+1"))
        assertEquals(-500, PipeSensorRules.parseOffset("−5.00"))
        assertNull(PipeSensorRules.parseOffset("5.01"))
        assertNull(PipeSensorRules.parseOffset("0.123"))
        assertNull(PipeSensorRules.parseOffset("abc"))
        assertEquals("-0.12", PipeSensorRules.formatOffset(-12))
        assertEquals("0.05", PipeSensorRules.formatOffset(5))
        assertEquals("5.00", PipeSensorRules.formatOffset(500))
        assertEquals("28…16038F", PipeSensorRules.shortAddress("28FF4C1D6116038F"))
    }
}
