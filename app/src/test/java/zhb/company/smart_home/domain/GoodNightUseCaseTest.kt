package zhb.company.smart_home.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zhb.company.smart_home.data.garden.Node1Repository
import zhb.company.smart_home.data.mqtt.FakeMqttConnector
import zhb.company.smart_home.data.mqtt.MqttTopics
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.remote.MqttConfigSource
import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto

@OptIn(ExperimentalCoroutinesApi::class)
class GoodNightUseCaseTest {

    private suspend fun connectedRepository(connector: FakeMqttConnector): SmartHomeRepository {
        val config = MqttClientConfigDto(mqttServer = "broker", mqttPort = "1883")
        return SmartHomeRepository(MqttConfigSource { Result.success(config) }, connector, UnconfinedTestDispatcher())
            .also { it.connect("token") }
    }

    @Test
    fun `turns off every device that is on, including the AC`() = runTest {
        val connector = FakeMqttConnector()
        val repo = connectedRepository(connector)
        repo.handleMessage(MqttTopics.NODE2_BULB_STATUS, "1", retained = false)
        repo.handleMessage(MqttTopics.NODE6_POOL_PUMP_STATUS, "1", retained = false)
        repo.setAcPower(true)

        val changed = GoodNightUseCase(repo, Node1Repository(repo, backgroundScope))()

        assertTrue(changed)
        val sent = connector.lastConnection!!.published.drop(1).filterNot { it.topic.endsWith("node1/config/get") }.map { it.topic to it.payload } // drop "s" request
        assertEquals(
            listOf(
                MqttTopics.NODE2_BULB_CONTROL to "0",
                MqttTopics.NODE6_POOL_PUMP_CONTROL to "0",
                MqttTopics.NODE8_AC_COMMAND to AcCommandBuilder.POWER_OFF_COMMAND,
            ),
            sent,
        )
        assertFalse(repo.state.value.acOn)
    }

    @Test
    fun `does nothing when everything is already off`() = runTest {
        val connector = FakeMqttConnector()
        val repo = connectedRepository(connector)

        assertFalse(GoodNightUseCase(repo, Node1Repository(repo, backgroundScope))())
        assertEquals(1, connector.lastConnection!!.published.count { !it.topic.endsWith("node1/config/get") }) // only the "s" request
    }

    @Test
    fun `stops watering and switches the fountain off with Node 1 commands`() = runTest {
        val connector = FakeMqttConnector()
        val repo = connectedRepository(connector)
        val node1 = Node1Repository(repo, backgroundScope, commandTimeoutMs = 1_000, newId = { "g" })
        repo.handleMessage(
            "bozaSub/kuca/node1/state",
            """{"seq":1,"up":1,"auto":true,"valve":2,"run":{"src":"app","zone":2,"phase":"on","phase_left":60,"zones":[2],"left":[60]},""" +
                """"next":null,"paused":false,"fountain":{"pump":true,"light":false},"cfg_rev":1,"storage":null}""",
            retained = true,
        )

        assertTrue(GoodNightUseCase(repo, node1)())
        val topics = connector.lastConnection!!.published.map { it.topic }
        assertTrue("bozaSub/kuca/node1/fountain/pump/set" in topics)
        assertTrue("bozaSub/kuca/node1/run/stop" in topics)
        assertFalse("bozaSub/kuca/node1/fountain/light/set" in topics)
    }
}
