package zhb.company.smart_home.ui.garden

import java.time.LocalDate
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

@OptIn(ExperimentalCoroutinesApi::class)
class GardenViewModelsTest {

    private fun config(rev: Int, zones: String = "[10,10,0,15,10,10]") =
        """{"rev":$rev,"auto":true,"days":["mon","wed","fri"],"starts":["06:00","20:30"],"zones":$zones,"soak":{"on":0,"off":0},"pause_until":null}"""

    private fun state(run: String = "null") =
        """{"seq":1,"up":60,"time":"10:00","day":"fri","auto":true,"valve":null,"run":$run,"next":null,"paused":false,""" +
            """"fountain":{"pump":false,"light":false},"cfg_rev":4,"storage":null}"""

    private val running = """{"src":"schedule","zone":2,"phase":"on","phase_left":95,"zones":[1,2],"left":[0,95]}"""

    private class Fixture(scope: TestScope) {
        val connector = FakeMqttConnector()
        val mqtt = SmartHomeRepository(
            MqttConfigSource { Result.success(MqttClientConfigDto(mqttServer = "broker", mqttPort = "1883")) },
            connector,
            UnconfinedTestDispatcher(scope.testScheduler),
        )
        private var ids = 0
        val node1 = Node1Repository(mqtt, scope.backgroundScope, newId = { "id-${++ids}" })
        val node2 = Node2Repository(mqtt, scope.backgroundScope, newId = { "n2-${++ids}" })
        val garden = GardenViewModel(mqtt, node1, node2, today = { LocalDate.of(2026, 11, 20) })
        val plan = IrrigationPlanViewModel(mqtt, node1, today = { LocalDate.of(2026, 11, 20) })

        fun deliver(path: String, payload: String, retained: Boolean = true) = mqtt.handleMessage("bozaSub/kuca/node1/$path", payload, retained)
        fun sent(path: String) = connector.lastConnection?.published.orEmpty().filter { it.topic == "bozaSub/kuca/node1/$path" }
    }

    private fun TestScope.fixture(): Fixture {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return Fixture(this).also { f ->
            backgroundScope.launch { f.garden.state.collect {} }
            backgroundScope.launch { f.plan.state.collect {} }
        }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a zone without minutes asks how long, a running run asks before it is replaced`() = runTest {
        val f = fixture()
        f.mqtt.connect("token")
        f.deliver("config", config(4))
        f.deliver("state", state())
        runCurrent()

        f.garden.onIntent(GardenIntent.StartZone(3))
        assertEquals(GardenDialog.AskMinutes(3), f.garden.state.value.dialog)
        f.garden.onIntent(GardenIntent.StartZoneFor(3, 7))
        runCurrent()
        assertEquals("""{"zones":[3],"minutes":7,"id":"id-1"}""", f.sent("run/start").single().payload)
        f.deliver("result", """{"cmd":"run/start","id":"id-1","ok":true,"rev":4}""", retained = false)
        runCurrent()
        assertEquals(GardenMessage.Started, f.garden.state.value.message)

        f.deliver("state", state(running))
        f.garden.onIntent(GardenIntent.StartZone(1))
        assertEquals(GardenDialog.ConfirmReplace(listOf(1), null), f.garden.state.value.dialog)
        assertEquals(1, f.sent("run/start").size)
        f.garden.onIntent(GardenIntent.ConfirmReplace)
        runCurrent()
        assertEquals("""{"zones":[1],"id":"id-2"}""", f.sent("run/start").last().payload)
    }

    @Test
    fun `plan edits stay a draft, saving sends only the changes and drops the draft at the new revision`() = runTest {
        val f = fixture()
        f.mqtt.connect("token")
        f.deliver("config", config(4))
        runCurrent()
        assertFalse(f.plan.state.value.dirty)

        f.plan.onIntent(PlanIntent.SetZone(3, 5))
        f.plan.onIntent(PlanIntent.SetZone(3, 0)) // back to the node's value
        assertFalse(f.plan.state.value.dirty)

        f.plan.onIntent(PlanIntent.SetZone(3, 5))
        f.plan.onIntent(PlanIntent.RemoveStart(0))
        assertTrue(f.plan.state.value.dirty)
        f.plan.onIntent(PlanIntent.Save)
        runCurrent()
        assertEquals("""{"starts":["20:30"],"zones":[10,10,5,15,10,10],"id":"id-1"}""", f.sent("config/set").single().payload)

        f.deliver("result", """{"cmd":"config/set","id":"id-1","ok":true,"rev":5}""", retained = false)
        runCurrent()
        assertEquals(PlanMessage.Saved, f.plan.state.value.message)
        f.deliver("config", config(5, "[10,10,5,15,10,10]").replace("\"06:00\",", ""))
        runCurrent()
        assertFalse(f.plan.state.value.dirty)
        assertEquals(listOf("20:30"), f.plan.state.value.draft?.starts)
    }

    @Test
    fun `rain pause is sent at once and is not part of the draft`() = runTest {
        val f = fixture()
        f.mqtt.connect("token")
        f.deliver("config", config(4))
        runCurrent()

        f.plan.onIntent(PlanIntent.PauseFor(3))
        runCurrent()
        assertEquals("""{"pause_until":"2026-11-22","id":"id-1"}""", f.sent("config/set").single().payload)
        f.deliver("result", """{"cmd":"config/set","id":"id-1","ok":true,"rev":5}""", retained = false)
        f.deliver("config", config(5).replace("\"pause_until\":null", "\"pause_until\":\"2026-11-22\""))
        runCurrent()
        assertEquals("2026-11-22", f.plan.state.value.pausedUntil)
        assertFalse(f.plan.state.value.dirty)
        assertEquals("2026-11-22", f.garden.state.value.irrigation.pausedUntil)
        assertNull(f.garden.state.value.irrigation.run)
    }
}
