package zhb.company.smart_home.domain.garden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zhb.company.smart_home.domain.node.NodeJson

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
}
