package zhb.company.smart_home.domain.heating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Payload examples are taken from smart-home-mcu/docs/mqtt_topics.md (Node 5, node5-2.0.0). */
class Node5JsonTest {

    @Test
    fun `parses config with limits`() {
        val config = Node5Json.parseConfig(
            """{"rev":9,"main_switch":true,"zones":["ground"],"min_on_s":600,"min_off_s":600,""" +
                """"limits":{"temp_min":5.0,"temp_max":30.0,"step_max":2.0,"segments_max":8}}""",
        )
        assertEquals(Node5Config(9, true, listOf("ground"), 600, 600, Node5Limits(50, 300, 20, 8)), config)
    }

    @Test
    fun `config without limits falls back to firmware defaults`() {
        val config = Node5Json.parseConfig("""{"rev":1,"main_switch":false,"zones":["ground","upper"]}""")
        assertEquals(Node5Limits(), config?.limits)
        assertEquals(listOf("ground", "upper"), config?.zones)
    }

    @Test
    fun `parses zone settings in tenths and minutes`() {
        val zone = Node5Json.parseZone(
            """{"rev":9,"hysteresis":{"lower":0.2,"upper":0.1},""" +
                """"workday":[["00:00",19.5],["06:00",21.5],["22:30",19.5]],"weekend":[]}""",
        )
        assertEquals(
            ZoneSettings(
                rev = 9,
                hysteresis = Hysteresis(2, 1),
                workday = listOf(ScheduleSegment(0, 195), ScheduleSegment(360, 215), ScheduleSegment(1350, 195)),
                weekend = emptyList(),
            ),
            zone,
        )
    }

    @Test
    fun `parses state with zones, nulls and safe mode reason`() {
        val state = Node5Json.parseState(
            """{"seq":1234,"up":12340,"time":"21:35","weekend":false,"main_switch":true,"main_relay":true,""" +
                """"thermo_relay":false,"pump":false,"demand":false,"safe":null,"flow_t":34.25,"cfg_rev":9,""" +
                """"zones":{"ground":{"room_t":21.48,"room_age":6,"target":21.5,"demand":false,"fault":null}}}""",
        )!!
        assertEquals(1234L, state.seq)
        assertEquals("21:35", state.time)
        assertEquals(false, state.weekend)
        assertTrue(state.mainSwitch)
        assertFalse(state.pump)
        assertNull(state.safeReason)
        assertEquals(34.25, state.flowTemp!!, 0.0)
        assertEquals(ZoneLiveState(21.48, 6, 215, false, null), state.zones["ground"])

        val beforeNtp = Node5Json.parseState(
            """{"seq":1,"up":3,"time":null,"weekend":null,"main_switch":false,"pump":false,"safe":"startup",""" +
                """"flow_t":null,"zones":{"ground":{"room_t":null,"room_age":null,"target":null,"demand":false,"fault":"room_temp_stale"}}}""",
        )!!
        assertNull(beforeNtp.time)
        assertNull(beforeNtp.flowTemp)
        assertEquals("startup", beforeNtp.safeReason)
        assertEquals(ZoneLiveState(null, null, null, false, "room_temp_stale"), beforeNtp.zones["ground"])
    }

    @Test
    fun `parses pump event and safe mode`() {
        assertEquals(
            PumpEvent(3, 5400, true, "thermostat", 31.5),
            Node5Json.parsePump("""{"seq":3,"at":5400,"pump":true,"src":"thermostat","flow_t":31.50,"zones":{}}"""),
        )
        assertEquals(
            SafeModeState(true, "room_temp_stale", "ground", 7200),
            Node5Json.parseSafeMode("""{"active":true,"reason":"room_temp_stale","zone":"ground","at":7200}"""),
        )
        assertEquals(
            SafeModeState(false, null, null, 10),
            Node5Json.parseSafeMode("""{"active":false,"reason":null,"zone":null,"at":10}"""),
        )
    }

    @Test
    fun `parses ok and error results, id as string or number`() {
        assertEquals(
            Node5CommandResult("zones/ground/set", "req-17", true, 9, null),
            Node5Json.parseResult("""{"cmd":"zones/ground/set","id":"req-17","ok":true,"rev":9}"""),
        )
        assertEquals(
            Node5CommandResult("zones/ground/set", null, false, null, "workday: start times must rise"),
            Node5Json.parseResult("""{"cmd":"zones/ground/set","ok":false,"error":"workday: start times must rise"}"""),
        )
        assertEquals("42", Node5Json.parseResult("""{"cmd":"main_switch/set","id":42,"ok":true,"rev":3}""")?.id)
    }

    @Test
    fun `malformed or incomplete payloads are rejected`() {
        assertNull(Node5Json.parseConfig("not json"))
        assertNull(Node5Json.parseConfig("""{"rev":1}"""))
        assertNull(Node5Json.parseZone("""{"hysteresis":{"lower":0.1,"upper":0.1},"workday":[["6:00",20]],"weekend":[]}"""))
        assertNull(Node5Json.parseState("[]"))
        assertNull(Node5Json.parseResult(""))
    }

    @Test
    fun `main switch command`() {
        assertEquals("""{"on":false,"id":"a1"}""", Node5Json.mainSwitchCommand(on = false, id = "a1"))
    }

    @Test
    fun `zone command sends only the given keys with one decimal`() {
        val day = listOf(ScheduleSegment(0, 195), ScheduleSegment(360, 200), ScheduleSegment(1350, 215))
        assertEquals(
            """{"workday":[["00:00",19.5],["06:00",20.0],["22:30",21.5]],"id":"a2"}""",
            Node5Json.zoneSetCommand(id = "a2", workday = day),
        )
        assertEquals(
            """{"hysteresis":{"lower":0.2,"upper":0.0},"id":"a3"}""",
            Node5Json.zoneSetCommand(id = "a3", hysteresis = Hysteresis(2, 0)),
        )
    }

    @Test
    fun `zone command round-trips through the zone parser`() {
        val day = listOf(ScheduleSegment(0, 51), ScheduleSegment(1439, 299))
        val payload = Node5Json.zoneSetCommand(id = "x", hysteresis = Hysteresis(1, 20), workday = day, weekend = day)
        val parsed = Node5Json.parseZone(payload)!!
        assertEquals(day, parsed.workday)
        assertEquals(Hysteresis(1, 20), parsed.hysteresis)
    }
}
