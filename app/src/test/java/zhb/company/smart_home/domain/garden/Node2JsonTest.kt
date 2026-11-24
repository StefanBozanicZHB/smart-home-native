package zhb.company.smart_home.domain.garden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zhb.company.smart_home.domain.node.NodeJson
import zhb.company.smart_home.domain.schedule.OutputSchedule
import zhb.company.smart_home.domain.schedule.TodayWindow

/** Payloads from smart-home-mcu docs/mqtt_topics.md, section Node 2 (commit 5cb67af). */
class Node2JsonTest {

    @Test
    fun `state from the docs`() {
        val s = Node2Json.parseState("""{"seq":5,"up":300,"neon":false,"neon_left":null,"lamp":true,"lamp_left":5400,"ds18b20":true}""")
        assertEquals(Node2State(5, 300, neon = false, neonLeftSeconds = null, lamp = true, lampLeftSeconds = 5400, sensorOk = true), s)
        assertEquals(listOf(Node2Devices.LAMP), Node2Devices.onLabels(s))
        assertNull(Node2Json.parseState("""{"neon":true}"""))
        assertNull(Node2Json.parseState(""))
    }

    @Test
    fun `light commands`() {
        assertEquals("""{"on":true,"minutes":120,"id":"x"}""", NodeJson.switchCommand("x", true, 120))
        assertEquals("""{"on":false,"id":"y"}""", NodeJson.switchCommand("y", false, 120))
    }

    @Test
    fun `config and today's windows of node2-2_1_0`() {
        val c = Node2Json.parseConfig("""{"rev":3,"schedules":{"neon":null,"lamp":{"on":"sunset+20","off":"23:30","days":["mon","tue","wed","thu","fri","sat","sun"]}}}""")
        assertEquals(3L, c?.rev)
        assertEquals(listOf("neon", "lamp"), c?.schedules?.keys?.toList())
        assertNull(c?.schedules?.get("neon"))
        assertEquals(OutputSchedule.DEFAULT, c?.schedules?.get("lamp"))
        assertNull(Node2Json.parseConfig("""{"schedules":{}}"""))

        val s = Node2Json.parseState(
            """{"seq":5,"up":300,"neon":false,"neon_left":null,"neon_today":null,"lamp":true,"lamp_left":null,"lamp_today":{"on":"16:52","off":"23:30"},"ds18b20":true}""",
        )
        assertEquals(mapOf("neon" to null, "lamp" to TodayWindow("16:52", "23:30")), s?.today)
    }

    @Test
    fun `older state has no windows`() {
        assertEquals(emptyMap<String, TodayWindow?>(), Node2Json.parseState("""{"seq":5,"up":300,"neon":false,"lamp":true,"ds18b20":true}""")?.today)
    }
}
