package zhb.company.smart_home.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import zhb.company.smart_home.data.mqtt.MqttEndpoint
import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto

class MqttClientConfigDtoTest {

    private fun parse(json: String): MqttClientConfigDto = MqttClientConfigDto.fromJson(Json.parseToJsonElement(json).jsonObject as JsonObject)

    @Test
    fun `ports as numbers (new broker setup with a VPN backup)`() {
        val c = parse(
            """{"backupMqttPassword":"","backupMqttPort":1883,"backupMqttServer":"100.107.23.16","backupMqttUsername":"",""" +
                """"mqttPassword":"","mqttPort":1883,"mqttServer":"192.168.0.100","mqttUsername":""}""",
        )
        assertEquals(MqttEndpoint("192.168.0.100", 1883), MqttEndpoint.parse(c.mqttServer, c.mqttPort, c.mqttUsername, c.mqttPassword))
        assertEquals(MqttEndpoint("100.107.23.16", 1883), MqttEndpoint.parse(c.backupMqttServer, c.backupMqttPort, c.backupMqttUsername, c.backupMqttPassword))
    }

    @Test
    fun `ports as strings, missing and null values`() {
        val c = parse("""{"mqttServer":"broker.example","mqttPort":"8883","mqttUsername":"u","mqttPassword":"p","backupMqttServer":null,"extra":true}""")
        assertEquals(MqttEndpoint("broker.example", 8883, "u", "p"), MqttEndpoint.parse(c.mqttServer, c.mqttPort, c.mqttUsername, c.mqttPassword))
        assertEquals("", c.backupMqttServer)
        assertEquals("", c.backupMqttPort)
    }
}
