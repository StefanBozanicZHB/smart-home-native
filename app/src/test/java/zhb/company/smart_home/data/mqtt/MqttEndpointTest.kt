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

    private fun lan(host: String) = MqttEndpoint(host, 1883).isOnLan

    @Test
    fun `private home network addresses are on the LAN`() {
        assertTrue(lan("192.168.0.100"))
        assertTrue(lan("10.0.0.5"))
        assertTrue(lan("172.20.1.1"))
    }

    @Test
    fun `Tailscale, public addresses and host names are not on the LAN`() {
        assertFalse(lan("100.107.23.16"))
        assertFalse(lan("172.32.0.1"))
        assertFalse(lan("8.8.8.8"))
        assertFalse(lan("broker.hivemq.cloud"))
        assertFalse(lan("192.168.0"))
    }
}
