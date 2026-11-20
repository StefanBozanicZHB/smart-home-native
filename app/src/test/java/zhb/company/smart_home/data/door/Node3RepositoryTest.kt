package zhb.company.smart_home.data.door

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zhb.company.smart_home.data.mqtt.FakeMqttConnector
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.data.remote.MqttConfigSource
import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto

@OptIn(ExperimentalCoroutinesApi::class)
class Node3RepositoryTest {

    @Test
    fun `state and access fill the snapshot, open is answered on result`() = runTest {
        val connector = FakeMqttConnector()
        val mqtt = SmartHomeRepository(
            MqttConfigSource { Result.success(MqttClientConfigDto(mqttServer = "broker", mqttPort = "1883")) },
            connector,
            UnconfinedTestDispatcher(testScheduler),
        )
        val node3 = Node3Repository(mqtt, backgroundScope, newId = { "d1" })
        mqtt.connect("token")
        runCurrent()
        assertTrue(connector.lastConnection!!.published.any { it.topic == "bozaSub/kuca/node3/config/get" })

        mqtt.handleMessage("bozaSub/kuca/node3/state", """{"seq":8,"up":480,"lock":"locked","tags":12,"reader_ok":false}""", retained = true)
        mqtt.handleMessage("bozaSub/kuca/node3/access", """{"seq":4,"at":300,"result":"granted","method":"button","name":null}""", retained = true)
        assertEquals(false, node3.snapshot.value.state?.readerOk)
        assertEquals(180L, node3.snapshot.value.lastAccess?.secondsBefore(node3.snapshot.value.state))

        val open = async { node3.openDoor() }
        runCurrent()
        val sent = connector.lastConnection!!.published.last()
        assertEquals("bozaSub/kuca/node3/door/open" to """{"id":"d1"}""", sent.topic to sent.payload)
        mqtt.handleMessage("bozaSub/kuca/node3/result", """{"cmd":"door/open","id":"d1","ok":true}""", retained = false)
        assertEquals(CommandOutcome.Accepted(null), open.await())
    }
}
