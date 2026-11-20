package zhb.company.smart_home.data.heating

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zhb.company.smart_home.data.mqtt.FakeMqttConnector
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.remote.MqttConfigSource
import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto
import zhb.company.smart_home.domain.heating.Hysteresis
import zhb.company.smart_home.domain.heating.Pipe
import zhb.company.smart_home.domain.heating.PipeAssignment
import zhb.company.smart_home.domain.heating.ScheduleSegment

@OptIn(ExperimentalCoroutinesApi::class)
class Node5RepositoryTest {

    private val config = """{"rev":9,"main_switch":true,"zones":["ground"],"min_on_s":600,"min_off_s":600,""" +
        """"limits":{"temp_min":5.0,"temp_max":30.0,"step_max":2.0,"segments_max":8}}"""

    private class Fixture(scope: TestScope, initialNodeId: String = Node5Topics.REAL) {
        val connector = FakeMqttConnector()
        val nodeId = MutableStateFlow(initialNodeId)
        val dispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        val mqtt = SmartHomeRepository(
            MqttConfigSource { Result.success(MqttClientConfigDto(mqttServer = "broker", mqttPort = "1883")) },
            connector,
            dispatcher,
        )
        private var ids = 0
        val node5 = Node5Repository(
            mqtt = mqtt,
            nodeIds = nodeId,
            scope = scope.backgroundScope,
            commandTimeoutMs = 10_000,
            newId = { "id-${++ids}" },
        )

        fun published() = connector.lastConnection?.published.orEmpty()
        fun deliver(topic: String, payload: String, retained: Boolean = true) = mqtt.handleMessage(topic, payload, retained)
    }

    @Test
    fun `retained topics of the selected node fill the snapshot`() = runTest {
        val f = Fixture(this)
        f.deliver("bozaSub/kuca/node5/config", config)
        f.deliver(
            "bozaSub/kuca/node5/zones/ground",
            """{"rev":9,"hysteresis":{"lower":0.2,"upper":0.1},"workday":[["00:00",19.5]],"weekend":[]}""",
        )
        f.deliver("bozaSub/kuca/node5/safe_mode", """{"active":true,"reason":"no_schedule","zone":"ground","at":40}""")

        val s = f.node5.snapshot.value
        assertEquals(9L, s.config?.rev)
        assertEquals(Hysteresis(2, 1), s.zones["ground"]?.hysteresis)
        assertEquals(listOf(ScheduleSegment(0, 195)), s.zones["ground"]?.workday)
        assertEquals("no_schedule", s.safeMode?.reason)
    }

    @Test
    fun `messages of the other node id and command echoes are ignored`() = runTest {
        val f = Fixture(this)
        f.deliver("bozaSub/kuca/testnode5/config", config)
        f.deliver("bozaSub/kuca/node5/zones/ground/set", """{"hysteresis":{"lower":0.1,"upper":0.1},"workday":[],"weekend":[]}""")
        f.deliver("bozaSub/kuca/node5/config", "garbage")

        assertNull(f.node5.snapshot.value.config)
        assertTrue(f.node5.snapshot.value.zones.isEmpty())
    }

    @Test
    fun `connecting asks the node to republish its state`() = runTest {
        val f = Fixture(this)
        f.mqtt.connect("token")
        runCurrent()

        assertTrue(f.published().any { it.topic == "bozaSub/kuca/node5/config/get" })
    }

    @Test
    fun `switching node id clears the snapshot and requests the new node`() = runTest {
        val f = Fixture(this)
        f.mqtt.connect("token")
        f.deliver("bozaSub/kuca/node5/config", config)

        f.nodeId.value = Node5Topics.BENCH
        runCurrent()

        assertEquals(Node5Topics.BENCH, f.node5.snapshot.value.nodeId)
        assertNull(f.node5.snapshot.value.config)
        assertTrue(f.published().any { it.topic == "bozaSub/kuca/testnode5/config/get" })
        f.deliver("bozaSub/kuca/testnode5/config", config)
        assertEquals(9L, f.node5.snapshot.value.config?.rev)
    }

    @Test
    fun `command is matched with its result by id`() = runTest {
        val f = Fixture(this)
        f.mqtt.connect("token")
        runCurrent() // let the connect-time config/get go out first

        val outcome = async { f.node5.setMainSwitch(false) }
        runCurrent()
        val sent = f.published().last()
        assertEquals("bozaSub/kuca/node5/main_switch/set", sent.topic)
        assertEquals("""{"on":false,"id":"id-1"}""", sent.payload)
        assertFalse(sent.retain)

        // An answer for another client's command must not complete ours.
        f.deliver("bozaSub/kuca/node5/result", """{"cmd":"main_switch/set","id":"other","ok":false,"error":"x"}""", retained = false)
        assertFalse(outcome.isCompleted)
        f.deliver("bozaSub/kuca/node5/result", """{"cmd":"main_switch/set","id":"id-1","ok":true,"rev":10}""", retained = false)

        assertEquals(CommandOutcome.Accepted(10), outcome.await())
    }

    @Test
    fun `zone update sends only the changed day and reports a rejection`() = runTest {
        val f = Fixture(this)
        f.mqtt.connect("token")
        runCurrent() // let the connect-time config/get go out first

        val day = listOf(ScheduleSegment(0, 195), ScheduleSegment(360, 215))
        val outcome = async { f.node5.updateZone("ground", weekend = day) }
        runCurrent()
        val sent = f.published().last()
        assertEquals("bozaSub/kuca/node5/zones/ground/set", sent.topic)
        assertEquals("""{"weekend":[["00:00",19.5],["06:00",21.5]],"id":"id-1"}""", sent.payload)

        f.deliver(
            "bozaSub/kuca/node5/result",
            """{"cmd":"zones/ground/set","id":"id-1","ok":false,"error":"weekend: start times must rise"}""",
            retained = false,
        )
        assertEquals(CommandOutcome.Rejected("weekend: start times must rise"), outcome.await())
    }

    @Test
    fun `pipe sensors on the bench board - read and assign with an id`() = runTest {
        val f = Fixture(this, initialNodeId = Node5Topics.BENCH)
        f.mqtt.connect("token")
        runCurrent() // let the connect-time config/get go out first
        f.deliver(
            "bozaSub/kuca/testnode5/sensors",
            """{"rev":3,"pipes":{"supply":null,"return":null,"well_in":null,"well_out":null},"fallback":true,""" +
                """"bus":[{"addr":"28AA01020304A169","t":21.06,"pipe":null}]}""",
        )
        assertTrue(f.node5.snapshot.value.sensors!!.fallback)

        val outcome = async { f.node5.updateSensors(mapOf(Pipe.SUPPLY to PipeAssignment("28AA01020304A169"))) }
        runCurrent()
        val sent = f.published().last()
        assertEquals("bozaSub/kuca/testnode5/sensors/set", sent.topic)
        assertEquals("""{"pipes":{"supply":{"addr":"28AA01020304A169","offset":0.0}},"id":"id-1"}""", sent.payload)
        f.deliver("bozaSub/kuca/testnode5/result", """{"cmd":"sensors/set","id":"id-1","ok":false,"error":"supply: bad address CRC"}""", retained = false)
        assertEquals(CommandOutcome.Rejected("supply: bad address CRC"), outcome.await())
    }

    @Test
    fun `command without an answer times out`() = runTest {
        val f = Fixture(this)
        f.mqtt.connect("token")
        runCurrent() // let the connect-time config/get go out first

        val outcome = async { f.node5.setMainSwitch(true) }
        advanceTimeBy(10_001)

        assertEquals(CommandOutcome.Timeout, outcome.await())
    }

    @Test
    fun `command while disconnected is not sent`() = runTest {
        val f = Fixture(this)
        assertEquals(CommandOutcome.NotConnected, f.node5.setMainSwitch(true))
        assertTrue(f.published().isEmpty())
    }
}
