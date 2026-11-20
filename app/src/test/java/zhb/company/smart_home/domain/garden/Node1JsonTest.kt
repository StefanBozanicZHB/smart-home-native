package zhb.company.smart_home.domain.garden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zhb.company.smart_home.domain.node.NodeCommandResult

/** Payloads from smart-home-mcu docs/mqtt_topics.md, section Node 1 (commit 6d7ea71). */
class Node1JsonTest {

    private val configDoc = """{"rev":4,"auto":true,"days":["mon","wed","fri"],"starts":["06:00","20:30"],"zones":[10,10,0,15,10,10],""" +
        """"soak":{"on":5,"off":10},"pause_until":null,"limits":{"zones":6,"starts_max":4,"zone_minutes_max":60,"soak_on_max":60,"soak_off_max":120}}"""

    private val stateDoc = """{"seq":120,"up":7260,"time":"06:12","day":"mon","auto":true,"valve":2,""" +
        """"run":{"src":"schedule","zone":2,"phase":"on","phase_left":95,"zones":[1,2,4,5,6],"left":[0,95,900,600,600]},""" +
        """"next":{"day":"mon","time":"20:30","in_days":0},"paused":false,""" +
        """"fountain":{"pump":false,"pump_left":null,"light":true,"light_left":5400},"cfg_rev":4,"storage":null}"""

    @Test
    fun `config from the docs`() {
        val c = Node1Json.parseConfig(configDoc)!!
        assertEquals(4L, c.rev)
        assertTrue(c.auto)
        assertEquals(setOf(Weekday.MON, Weekday.WED, Weekday.FRI), c.days)
        assertEquals(listOf("06:00", "20:30"), c.starts)
        assertEquals(listOf(10, 10, 0, 15, 10, 10), c.zones)
        assertEquals(Soak(5, 10), c.soak)
        assertNull(c.pauseUntil)
        assertEquals(Node1Limits(), c.limits)
        assertNull(Node1Json.parseConfig("""{"rev":1}"""))
        assertNull(Node1Json.parseConfig("garbage"))
    }

    @Test
    fun `state from the docs`() {
        val s = Node1Json.parseState(stateDoc)!!
        assertEquals("06:12", s.time)
        assertEquals(Weekday.MON, s.day)
        assertEquals(2, s.valve)
        assertEquals(ActiveRun(RunSource.SCHEDULE, 2, RunPhase.ON, 95, listOf(1, 2, 4, 5, 6), listOf(0, 95, 900, 600, 600)), s.run)
        assertEquals(2195, s.run!!.totalLeftSeconds)
        assertEquals(NextStart(Weekday.MON, "20:30", 0), s.next)
        assertEquals(FountainState(pump = false, pumpLeftSeconds = null, light = true, lightLeftSeconds = 5400), s.fountain)
        assertEquals(4L, s.configRev)
        assertNull(s.storage)
    }

    @Test
    fun `idle state without clock`() {
        val s = Node1Json.parseState(
            """{"seq":1,"up":5,"time":null,"day":null,"auto":false,"valve":null,"run":null,"next":null,"paused":false,""" +
                """"fountain":{"pump":false,"pump_left":null,"light":false,"light_left":null},"cfg_rev":1,"storage":"saving the configuration failed"}""",
        )!!
        assertNull(s.time)
        assertNull(s.run)
        assertNull(s.next)
        assertEquals("saving the configuration failed", s.storage)
    }

    @Test
    fun `run events`() {
        assertEquals(
            RunEvent(7, 86412, RunEventType.STARTED, RunSource.SCHEDULE, "06:00", listOf(1, 2, 4, 5, 6), listOf(10, 10, 15, 10, 10), null),
            Node1Json.parseRunEvent("""{"seq":7,"at":86412,"event":"started","src":"schedule","start":"06:00","zones":[1,2,4,5,6],"minutes":[10,10,15,10,10]}"""),
        )
        assertEquals(
            SkipReason.PAUSED,
            Node1Json.parseRunEvent("""{"seq":8,"at":90000,"event":"skipped","src":"schedule","start":"20:30","zones":[],"minutes":[],"reason":"paused"}""")?.reason,
        )
        assertNull(Node1Json.parseRunEvent("""{"seq":8,"at":1,"event":"exploded"}"""))
    }

    @Test
    fun `commands carry only known keys and the id`() {
        assertEquals(
            """{"days":["mon","wed","fri"],"starts":["06:00","20:30"],"soak":{"on":5,"off":10},"id":"a1"}""",
            Node1Json.configSetCommand("a1", Node1ConfigChange(days = setOf(Weekday.FRI, Weekday.MON, Weekday.WED), starts = listOf("06:00", "20:30"), soak = Soak(5, 10))),
        )
        assertEquals("""{"pause_until":null,"id":"a2"}""", Node1Json.configSetCommand("a2", Node1ConfigChange(pauseUntil = PauseUntil(null))))
        assertEquals("""{"pause_until":"2026-11-22","id":"a3"}""", Node1Json.configSetCommand("a3", Node1ConfigChange(pauseUntil = PauseUntil("2026-11-22"))))
        assertEquals("""{"zones":[1,3],"minutes":5,"id":"a4"}""", Node1Json.runStartCommand("a4", listOf(1, 3), 5))
        assertEquals("""{"id":"a5"}""", Node1Json.runStartCommand("a5"))
        assertEquals("""{"id":"a6"}""", Node1Json.runStopCommand("a6"))
        assertEquals("""{"on":true,"minutes":120,"id":"a7"}""", Node1Json.fountainCommand("a7", true, 120))
        assertEquals("""{"on":false,"id":"a8"}""", Node1Json.fountainCommand("a8", false, 120))
    }

    @Test
    fun `result with saved false`() {
        assertEquals(
            NodeCommandResult("config/set", "a1", ok = true, rev = 5, error = null, saved = false),
            NodeCommandResult.parse("""{"cmd":"config/set","id":"a1","ok":true,"rev":5,"saved":false}"""),
        )
    }
}
