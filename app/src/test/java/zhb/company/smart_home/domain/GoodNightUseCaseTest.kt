package zhb.company.smart_home.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zhb.company.smart_home.data.garden.Node1Repository
import zhb.company.smart_home.data.garden.Node2Repository
import zhb.company.smart_home.data.garden.Node6Repository
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
    fun `turns off every device that is on`() = runTest {
        val connector = FakeMqttConnector()
        val repo = connectedRepository(connector)
        repo.handleMessage(MqttTopics.node7BulbStatus(1), "1", retained = false)
        repo.handleMessage(MqttTopics.node7BulbStatus(3), "1", retained = false)

        val changed = GoodNightUseCase(repo, Node1Repository(repo, backgroundScope), Node2Repository(repo, backgroundScope), Node6Repository(repo, backgroundScope))()

        assertTrue(changed)
        val sent = connector.lastConnection!!.published.drop(1).filterNot { it.topic.endsWith("/config/get") }.map { it.topic to it.payload } // drop "s" request
        assertEquals(
            listOf(
                MqttTopics.node7BulbControl(1) to "0",
                MqttTopics.node7BulbControl(3) to "0",
            ),
            sent,
        )
    }

    @Test
    fun `does nothing when everything is already off`() = runTest {
        val connector = FakeMqttConnector()
        val repo = connectedRepository(connector)

        assertFalse(GoodNightUseCase(repo, Node1Repository(repo, backgroundScope), Node2Repository(repo, backgroundScope), Node6Repository(repo, backgroundScope))())
        assertEquals(1, connector.lastConnection!!.published.count { !it.topic.endsWith("/config/get") }) // only the "s" request
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

        assertTrue(GoodNightUseCase(repo, node1, Node2Repository(repo, backgroundScope), Node6Repository(repo, backgroundScope))())
        val topics = connector.lastConnection!!.published.map { it.topic }
        assertTrue("bozaSub/kuca/node1/fountain/pump/set" in topics)
        assertTrue("bozaSub/kuca/node1/run/stop" in topics)
        assertFalse("bozaSub/kuca/node1/fountain/light/set" in topics)
    }

    @Test
    fun `switches the yard lights off with Node 2 commands`() = runTest {
        val connector = FakeMqttConnector()
        val repo = connectedRepository(connector)
        val node2 = Node2Repository(repo, backgroundScope, commandTimeoutMs = 1_000, newId = { "g" })
        repo.handleMessage(
            "bozaSub/kuca/node2/state",
            """{"seq":5,"up":300,"neon":false,"neon_left":null,"lamp":true,"lamp_left":5400,"ds18b20":true}""",
            retained = true,
        )

        assertTrue(GoodNightUseCase(repo, Node1Repository(repo, backgroundScope), node2, Node6Repository(repo, backgroundScope))())
        val sent = connector.lastConnection!!.published.filter { it.topic.startsWith("bozaSub/kuca/node2/") && !it.topic.endsWith("/config/get") }
        assertEquals(listOf("bozaSub/kuca/node2/lamp/set" to """{"on":false,"id":"g"}"""), sent.map { it.topic to it.payload })
    }

    @Test
    fun `switches the pool pump off with a Node 6 command`() = runTest {
        val connector = FakeMqttConnector()
        val repo = connectedRepository(connector)
        val node6 = Node6Repository(repo, backgroundScope, commandTimeoutMs = 1_000, newId = { "p" })
        repo.handleMessage("bozaSub/kuca/node6/state", """{"seq":5,"up":300,"pump":true,"pump_left":7140,"ds18b20":true}""", retained = true)

        assertTrue(GoodNightUseCase(repo, Node1Repository(repo, backgroundScope), Node2Repository(repo, backgroundScope), node6)())
        val sent = connector.lastConnection!!.published.filter { it.topic == "bozaSub/kuca/node6/pump/set" }
        assertEquals(listOf("""{"on":false,"id":"p"}"""), sent.map { it.payload })
    }
}
