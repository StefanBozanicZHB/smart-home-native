package zhb.company.smart_home.data.garden

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
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
import zhb.company.smart_home.domain.garden.Node1ConfigChange

@OptIn(ExperimentalCoroutinesApi::class)
class Node1RepositoryTest {

    private class Fixture(scope: TestScope) {
        val connector = FakeMqttConnector()
        val mqtt = SmartHomeRepository(
            MqttConfigSource { Result.success(MqttClientConfigDto(mqttServer = "broker", mqttPort = "1883")) },
            connector,
            UnconfinedTestDispatcher(scope.testScheduler),
        )
        private var ids = 0
        val node1 = Node1Repository(mqtt, scope.backgroundScope, newId = { "id-${++ids}" })

        fun published() = connector.lastConnection?.published.orEmpty()
        fun deliver(path: String, payload: String, retained: Boolean = true) = mqtt.handleMessage("bozaSub/kuca/node1/$path", payload, retained)
    }

    @Test
    fun `connecting asks for the state, retained topics fill the snapshot`() = runTest {
        val f = Fixture(this)
        f.mqtt.connect("token")
        runCurrent()
        assertTrue(f.published().any { it.topic == "bozaSub/kuca/node1/config/get" })

        f.deliver("config", """{"rev":4,"auto":true,"days":["mon"],"starts":["06:00"],"zones":[10,10,0,15,10,10],"soak":{"on":0,"off":0},"pause_until":null}""")
        f.deliver("state", """{"seq":1,"up":60,"time":"06:01","day":"mon","auto":true,"valve":1,"run":null,"next":null,"paused":false,"fountain":{"pump":true,"light":false},"cfg_rev":4,"storage":null}""")
        f.deliver("run", """{"seq":1,"at":60,"event":"started","src":"schedule","start":"06:00","zones":[1,2],"minutes":[10,10]}""")
        f.deliver("config/set", """{"auto":false}""", retained = false) // command echo, ignored

        val s = f.node1.snapshot.value
        assertEquals(4L, s.config?.rev)
        assertTrue(s.config!!.auto)
        assertEquals(1, s.state?.valve)
        assertTrue(s.state!!.fountain.pump)
        assertEquals("06:00", s.lastRun?.start)
    }

    @Test
    fun `commands are matched with their result by id`() = runTest {
        val f = Fixture(this)
        f.mqtt.connect("token")
        runCurrent()

        val save = async { f.node1.updateConfig(Node1ConfigChange(auto = false)) }
        runCurrent()
        val sent = f.published().last()
        assertEquals("bozaSub/kuca/node1/config/set", sent.topic)
        assertEquals("""{"auto":false,"id":"id-1"}""", sent.payload)
        f.deliver("result", """{"cmd":"config/set","id":"id-1","ok":true,"rev":5,"saved":false}""", retained = false)
        assertEquals(CommandOutcome.Accepted(5, saved = false), save.await())

        val run = async { f.node1.startRun(zones = listOf(3)) }
        runCurrent()
        f.deliver("result", """{"cmd":"run/start","id":"id-2","ok":false,"error":"zone 3 has no minutes"}""", retained = false)
        assertEquals(CommandOutcome.Rejected("zone 3 has no minutes"), run.await())
    }

    @Test
    fun `not connected`() = runTest {
        val f = Fixture(this)
        assertEquals(CommandOutcome.NotConnected, f.node1.stopRun())
    }
}
