package zhb.company.smart_home.data.garden

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zhb.company.smart_home.data.mqtt.FakeMqttConnector
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.data.remote.MqttConfigSource
import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto
import zhb.company.smart_home.domain.schedule.OutputSchedule

/** `<output>/schedule/set` on Node 1 and Node 2 and the answers on `<node>/result`. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleCommandsTest {

    private class Fixture(scope: TestScope) {
        val connector = FakeMqttConnector()
        val mqtt = SmartHomeRepository(
            MqttConfigSource { Result.success(MqttClientConfigDto(mqttServer = "broker", mqttPort = "1883")) },
            connector,
            UnconfinedTestDispatcher(scope.testScheduler),
        )
        private var ids = 0
        val node1 = Node1Repository(mqtt, scope.backgroundScope, newId = { "n1-${++ids}" })
        val node2 = Node2Repository(mqtt, scope.backgroundScope, newId = { "n2-${++ids}" })

        fun sent(topic: String) = connector.lastConnection?.published.orEmpty().filter { it.topic == topic }
        fun deliver(topic: String, payload: String) = mqtt.handleMessage("bozaSub/kuca/$topic", payload, retained = false)
    }

    @Test
    fun `node2 config appears only with the new firmware`() = runTest {
        val f = Fixture(this)
        assertNull(f.node2.config.value)
        f.mqtt.handleMessage("bozaSub/kuca/node2/config", """{"rev":3,"schedules":{"neon":null,"lamp":null}}""", retained = true)
        assertEquals(listOf("neon", "lamp"), f.node2.config.value?.schedules?.keys?.toList())
    }

    @Test
    fun `setting a schedule is answered by id`() = runTest {
        val f = Fixture(this)
        f.mqtt.connect("token")
        runCurrent()
        val outcome = async { f.node2.setSchedule("lamp", OutputSchedule.DEFAULT) }
        runCurrent()
        assertEquals(
            """{"on":"sunset+20","off":"23:30","days":["mon","tue","wed","thu","fri","sat","sun"],"id":"n2-1"}""",
            f.sent("bozaSub/kuca/node2/lamp/schedule/set").single().payload,
        )
        f.deliver("node2/result", """{"cmd":"lamp/schedule/set","id":"n2-1","ok":true,"rev":4}""")
        assertEquals(CommandOutcome.Accepted(4, saved = true), outcome.await())
    }

    @Test
    fun `removing sends a bare null and is matched by cmd`() = runTest {
        val f = Fixture(this)
        f.mqtt.connect("token")
        runCurrent()
        val outcome = async { f.node1.setFountainSchedule("light", null) }
        runCurrent()
        assertEquals("null", f.sent("bozaSub/kuca/node1/fountain/light/schedule/set").single().payload)
        // An answer for another output does not complete it.
        f.deliver("node1/result", """{"cmd":"fountain/pump/schedule/set","id":null,"ok":true,"rev":5}""")
        f.deliver("node1/result", """{"cmd":"fountain/light/schedule/set","id":null,"ok":true,"rev":6}""")
        assertEquals(CommandOutcome.Accepted(6, saved = true), outcome.await())
    }

    @Test
    fun `the node's error is passed on`() = runTest {
        val f = Fixture(this)
        f.mqtt.connect("token")
        runCurrent()
        val outcome = async { f.node1.setFountainSchedule("pump", OutputSchedule.DEFAULT) }
        runCurrent()
        f.deliver("node1/result", """{"cmd":"fountain/pump/schedule/set","id":"n1-1","ok":false,"error":"off: invalid time"}""")
        assertEquals(CommandOutcome.Rejected("off: invalid time"), outcome.await())
    }
}
