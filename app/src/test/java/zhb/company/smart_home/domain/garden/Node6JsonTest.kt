package zhb.company.smart_home.domain.garden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Payloads from smart-home-mcu docs/mqtt_topics.md, section Node 6 (commit 05de25e). */
class Node6JsonTest {
    @Test
    fun `state from the docs`() {
        val s = Node6Json.parseState("""{"seq":5,"up":300,"pump":true,"pump_left":7140,"ds18b20":true}""")
        assertEquals(Node6State(5, 300, pump = true, pumpLeftSeconds = 7140, sensorOk = true), s)
        assertEquals(listOf(Node6Json.PUMP_LABEL), Node6Json.onLabels(s))
        assertEquals(emptyList<String>(), Node6Json.onLabels(s?.copy(pump = false)))
        assertNull(Node6Json.parseState("""{"pump":true}"""))
    }
}
