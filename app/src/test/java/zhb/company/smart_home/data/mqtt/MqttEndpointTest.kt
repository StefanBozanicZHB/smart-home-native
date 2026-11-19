package zhb.company.smart_home.data.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttEndpointTest {

    @Test
    fun `parse trims host and port`() {
        assertEquals(MqttEndpoint("broker.local", 1883, "u", "p"), MqttEndpoint.parse(" broker.local ", " 1883 ", "u", "p"))
    }

    @Test
    fun `parse rejects blank host and non-numeric port`() {
        assertNull(MqttEndpoint.parse("", "1883", "", ""))
        assertNull(MqttEndpoint.parse("broker", "", "", ""))
        assertNull(MqttEndpoint.parse("broker", "abc", "", ""))
    }

    @Test
    fun `TLS is used only on port 8883`() {
        assertTrue(MqttEndpoint("h", 8883).useTls)
        assertFalse(MqttEndpoint("h", 1883).useTls)
    }
}
