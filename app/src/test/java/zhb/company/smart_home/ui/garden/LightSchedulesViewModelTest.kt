package zhb.company.smart_home.ui.garden

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zhb.company.smart_home.data.garden.Node1Repository
import zhb.company.smart_home.data.garden.Node2Repository
import zhb.company.smart_home.data.mqtt.FakeMqttConnector
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.remote.MqttConfigSource
import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto
import zhb.company.smart_home.domain.garden.Weekday
import zhb.company.smart_home.domain.schedule.OutputSchedule
import zhb.company.smart_home.domain.schedule.ScheduleProblem
import zhb.company.smart_home.domain.schedule.ScheduleTime
import zhb.company.smart_home.domain.schedule.SunEvent
import zhb.company.smart_home.domain.schedule.TodayWindow

@OptIn(ExperimentalCoroutinesApi::class)
class LightSchedulesViewModelTest {

    private val lamp = OutputId(ScheduleNode.NODE2, "lamp")
    private val neon = OutputId(ScheduleNode.NODE2, "neon")
    private val everyDay = """["mon","tue","wed","thu","fri","sat","sun"]"""

    private val node1Config = """{"rev":4,"auto":true,"days":["mon"],"starts":["06:00"],"zones":[10,10,0,15,10,10],"soak":{"on":0,"off":0},"pause_until":null"""

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
        val vm = LightSchedulesViewModel(mqtt, node1, node2)

        fun deliver(topic: String, payload: String, retained: Boolean = true) = mqtt.handleMessage("bozaSub/kuca/$topic", payload, retained)
        fun sent(topic: String) = connector.lastConnection?.published.orEmpty().filter { it.topic == "bozaSub/kuca/$topic" }
    }

    private fun TestScope.fixture(): Fixture {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return Fixture(this).also { f -> backgroundScope.launch { f.vm.state.collect {} } }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `outputs appear only for nodes that announce schedules`() = runTest {
        val f = fixture()
        f.deliver("node1/config", "$node1Config}") // node1-2.0.0: no "fountain"
        runCurrent()
        assertTrue(f.vm.state.value.outputs.isEmpty())

        f.deliver("node2/config", """{"rev":3,"schedules":{"neon":null,"lamp":{"on":"sunset+20","off":"23:30","days":$everyDay}}}""")
        f.deliver("node2/state", """{"seq":5,"up":300,"neon":false,"neon_today":null,"lamp":true,"lamp_today":{"on":"16:52","off":"23:30"},"ds18b20":true}""")
        runCurrent()
        assertEquals(listOf(neon, lamp), f.vm.state.value.outputs.map { it.id })
        val l = f.vm.state.value.outputs.last()
        assertEquals(OutputSchedule.DEFAULT, l.schedule)
        assertEquals(TodayWindow("16:52", "23:30"), l.today)

        f.deliver("node1/config", """$node1Config,"fountain":{"pump":null,"light":null}}""")
        runCurrent()
        assertEquals(listOf("neon", "lamp", "pump", "light"), f.vm.state.value.outputs.map { it.id.name })
    }

    @Test
    fun `switching to a schedule suggests sunset+20 to 23_30, saving sends it and waits for the node`() = runTest {
        val f = fixture()
        f.mqtt.connect("token")
        f.deliver("node2/config", """{"rev":3,"schedules":{"neon":null,"lamp":null}}""")
        runCurrent()

        f.vm.onIntent(ScheduleIntent.SetScheduled(neon, true))
        f.vm.onIntent(ScheduleIntent.SetOff(neon, ScheduleTime.Sun(SunEvent.SUNRISE, -30)))
        f.vm.onIntent(ScheduleIntent.ToggleDay(neon, Weekday.MON))
        val out = f.vm.state.value.outputs.first()
        assertTrue(out.dirty)
        f.vm.onIntent(ScheduleIntent.Save(neon))
        runCurrent()
        assertEquals(
            """{"on":"sunset+20","off":"sunrise-30","days":["tue","wed","thu","fri","sat","sun"],"id":"n2-1"}""",
            f.sent("node2/neon/schedule/set").single().payload,
        )

        // The node stores it, republishes its config and answers.
        f.deliver("node2/config", """{"rev":4,"schedules":{"neon":{"on":"sunset+20","off":"sunrise-30","days":["tue","wed","thu","fri","sat","sun"]},"lamp":null}}""")
        f.deliver("node2/result", """{"cmd":"neon/schedule/set","id":"n2-1","ok":true,"rev":4}""", retained = false)
        runCurrent()
        val saved = f.vm.state.value.outputs.first()
        assertFalse(saved.dirty)
        assertEquals(ScheduleTime.Sun(SunEvent.SUNRISE, -30), saved.schedule?.off)
        assertEquals(ScheduleMessage.Saved, f.vm.state.value.message)
    }

    @Test
    fun `a rejected schedule keeps the draft and shows the node's error`() = runTest {
        val f = fixture()
        f.mqtt.connect("token")
        f.deliver("node2/config", """{"rev":3,"schedules":{"neon":null,"lamp":null}}""")
        runCurrent()

        f.vm.onIntent(ScheduleIntent.SetScheduled(lamp, true))
        f.vm.onIntent(ScheduleIntent.Save(lamp))
        runCurrent()
        f.deliver("node2/result", """{"cmd":"lamp/schedule/set","id":"n2-1","ok":false,"error":"on: invalid time"}""", retained = false)
        runCurrent()
        assertEquals(ScheduleMessage.Rejected("on: invalid time"), f.vm.state.value.message)
        assertTrue(f.vm.state.value.outputs.last().dirty)
    }

    @Test
    fun `manual removes the schedule with null`() = runTest {
        val f = fixture()
        f.mqtt.connect("token")
        f.deliver("node1/config", """$node1Config,"fountain":{"pump":{"on":"sunset","off":"22:00","days":$everyDay},"light":null}}""")
        runCurrent()
        val pump = OutputId(ScheduleNode.NODE1, "pump")

        f.vm.onIntent(ScheduleIntent.SetScheduled(pump, false))
        assertNull(f.vm.state.value.outputs.first().schedule)
        f.vm.onIntent(ScheduleIntent.Save(pump))
        runCurrent()
        assertEquals("null", f.sent("node1/fountain/pump/schedule/set").single().payload)
        f.deliver("node1/config", """$node1Config,"fountain":{"pump":null,"light":null}}""")
        f.deliver("node1/result", """{"cmd":"fountain/pump/schedule/set","id":null,"ok":true,"rev":5}""", retained = false)
        runCurrent()
        assertEquals(ScheduleMessage.Removed, f.vm.state.value.message)
        assertFalse(f.vm.state.value.outputs.first().dirty)
    }

    @Test
    fun `no days is reported before sending`() = runTest {
        val f = fixture()
        f.mqtt.connect("token")
        f.deliver("node2/config", """{"rev":3,"schedules":{"neon":{"on":"sunset","off":"23:00","days":["sun"]},"lamp":null}}""")
        runCurrent()
        f.vm.onIntent(ScheduleIntent.ToggleDay(neon, Weekday.SUN))
        assertEquals(ScheduleProblem.NO_DAYS, f.vm.state.value.outputs.first().problem)
        f.vm.onIntent(ScheduleIntent.Save(neon))
        runCurrent()
        assertTrue(f.sent("node2/neon/schedule/set").isEmpty())
    }
}
