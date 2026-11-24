package zhb.company.smart_home.domain.garden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Payloads from smart-home-mcu docs/mqtt_topics.md, section Node 6. */
class Node6JsonTest {
    @Test
    fun `state of node6-2_0_0 has no heating`() {
        val s = Node6Json.parseState("""{"seq":5,"up":300,"pump":true,"pump_left":7140,"ds18b20":true}""")
        assertEquals(
            Node6State(5, 300, pump = true, pumpLeftSeconds = 7140, heating = null, heatingLeftSeconds = null, sensorOk = true),
            s,
        )
        assertEquals(listOf(Node6Json.PUMP_LABEL), Node6Json.onLabels(s))
        assertEquals(emptyList<String>(), Node6Json.onLabels(s?.copy(pump = false)))
        assertNull(Node6Json.parseState("""{"pump":true}"""))
    }

    @Test
    fun `state with the heating relay (node6-2_0_1)`() {
        val off = Node6Json.parseState(
            """{"seq":5,"up":300,"pump":true,"pump_left":7140,"heating":false,"heating_left":null,"ds18b20":true}""",
        )
        assertEquals(false, off?.heating)
        assertNull(off?.heatingLeftSeconds)

        val on = Node6Json.parseState("""{"seq":6,"up":310,"pump":false,"pump_left":null,"heating":true,"heating_left":3600,"ds18b20":true}""")
        assertEquals(true, on?.heating)
        assertEquals(3600, on?.heatingLeftSeconds)
        assertEquals(listOf(Node6Json.HEATING_LABEL), Node6Json.onLabels(on))
    }
}
