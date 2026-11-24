package zhb.company.smart_home.ui.heating

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import zhb.company.smart_home.data.heating.Node5Repository
import zhb.company.smart_home.data.mqtt.FakeMqttConnector
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.remote.MqttConfigSource
import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto
import zhb.company.smart_home.domain.heating.DayType
import zhb.company.smart_home.ui.command.SLOW_ANSWER_MS

@OptIn(ExperimentalCoroutinesApi::class)
class HeatingViewModelTest {

    private val config = """{"rev":1,"main_switch":true,"zones":["ground"],"limits":{"temp_min":5.0,"temp_max":30.0,"step_max":2.0,"segments_max":8}}"""

    private fun zone(rev: Int, workdayDay: String = "21.5", weekendDay: String = "22.0") =
        """{"rev":$rev,"hysteresis":{"lower":0.2,"upper":0.1},""" +
            """"workday":[["00:00",19.5],["06:00",$workdayDay]],"weekend":[["00:00",19.5],["08:00",$weekendDay]]}"""

    private class Fixture(scope: TestScope) {
        val connector = FakeMqttConnector()
        val mqtt = SmartHomeRepository(
            MqttConfigSource { Result.success(MqttClientConfigDto(mqttServer = "broker", mqttPort = "1883")) },
            connector,
            UnconfinedTestDispatcher(scope.testScheduler),
        )
        private var ids = 0
        val node5 = Node5Repository(mqtt, scope.backgroundScope, newId = { "id-${++ids}" })
        val vm = HeatingViewModel(mqtt, node5)

        fun deliver(topic: String, payload: String) = mqtt.handleMessage("bozaSub/kuca/node5/$topic", payload, retained = false)
    }

    private fun TestScope.fixture(): Fixture {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return Fixture(this).also { f -> backgroundScope.launch { f.vm.state.collect {} } }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `saving the weekend does not flag the workday draft as changed on the node`() = runTest {
        val f = fixture()
        f.mqtt.connect("token")
        f.deliver("config", config)
        f.deliver("zones/ground", zone(rev = 1))
        runCurrent()

        f.vm.onIntent(HeatingIntent.SelectDay(DayType.WORKDAY))
        f.vm.onIntent(HeatingIntent.ChangeSegmentTemp(1, 1)) // workday draft, not saved
        f.vm.onIntent(HeatingIntent.SelectDay(DayType.WEEKEND))
        f.vm.onIntent(HeatingIntent.ChangeSegmentTemp(1, 1))
        f.vm.onIntent(HeatingIntent.SaveSchedule)
        runCurrent()

        // The node applies it: zone and config republished with rev 2, then the answer.
        f.deliver("zones/ground", zone(rev = 2, weekendDay = "22.1"))
        f.deliver("result", """{"cmd":"zones/ground/set","id":"id-1","ok":true,"rev":2}""")
        runCurrent()

        val weekend = f.vm.state.value
        assertFalse("weekend saved", weekend.scheduleDirty)

        f.vm.onIntent(HeatingIntent.SelectDay(DayType.WORKDAY))
        val workday = f.vm.state.value
        assertTrue("workday edit still pending", workday.scheduleDirty)
        assertFalse("own save is not a remote change", workday.remoteChangedWhileEditing)
        assertEquals(216, workday.editorSegments[1].tempTenths)
    }

    @Test
    fun `a change by someone else still warns, but only for a real draft`() = runTest {
        val f = fixture()
        f.mqtt.connect("token")
        f.deliver("config", config)
        f.deliver("zones/ground", zone(rev = 1))
        runCurrent()

        f.vm.onIntent(HeatingIntent.SelectDay(DayType.WORKDAY))
        f.vm.onIntent(HeatingIntent.ChangeSegmentTemp(1, 1))
        f.deliver("zones/ground", zone(rev = 5, weekendDay = "23.0")) // another phone saved
        runCurrent()
        assertTrue(f.vm.state.value.remoteChangedWhileEditing)

        f.vm.onIntent(HeatingIntent.ChangeSegmentTemp(1, -1)) // back to the node's program
        runCurrent()
        assertFalse(f.vm.state.value.scheduleDirty)
        assertFalse(f.vm.state.value.remoteChangedWhileEditing)
    }

    @Test
    fun `the main switch moves at once, nothing else waits, and a hint only when the answer is late`() = runTest {
        val f = fixture()
        f.mqtt.connect("token")
        f.deliver("config", config)
        f.deliver("zones/ground", zone(rev = 1))
        runCurrent()

        f.vm.onIntent(HeatingIntent.ToggleMainSwitch(false))
        f.vm.onIntent(HeatingIntent.ConfirmMainSwitchOff)
        runCurrent()
        assertFalse(f.vm.state.value.mainSwitch)
        assertNull(f.vm.state.value.slowCommand)

        advanceTimeBy(SLOW_ANSWER_MS + 1)
        assertEquals(HeatingCommand.MAIN_SWITCH, f.vm.state.value.slowCommand)

        f.deliver("config", config.replace(""""main_switch":true""", """"main_switch":false"""))
        f.deliver("result", """{"cmd":"main_switch/set","id":"id-1","ok":true,"rev":2}""")
        runCurrent()
        assertFalse(f.vm.state.value.mainSwitch)
        assertNull(f.vm.state.value.slowCommand)
        assertEquals(HeatingMessage.Saved, f.vm.state.value.message)
    }

    @Test
    fun `a saved program stays in the editor until the node shows it`() = runTest {
        val f = fixture()
        f.mqtt.connect("token")
        f.deliver("config", config)
        f.deliver("zones/ground", zone(rev = 1))
        runCurrent()

        f.vm.onIntent(HeatingIntent.SelectDay(DayType.WORKDAY))
        f.vm.onIntent(HeatingIntent.ChangeSegmentTemp(1, 1))
        f.vm.onIntent(HeatingIntent.SaveSchedule)
        runCurrent()

        // The answer comes before the republished zone: the editor keeps 21.6, not the old 21.5.
        f.deliver("result", """{"cmd":"zones/ground/set","id":"id-1","ok":true,"rev":2}""")
        runCurrent()
        assertEquals(216, f.vm.state.value.editorSegments[1].tempTenths)

        f.deliver("zones/ground", zone(rev = 2, workdayDay = "21.6"))
        runCurrent()
        assertEquals(216, f.vm.state.value.editorSegments[1].tempTenths)
        assertFalse(f.vm.state.value.scheduleDirty)
        assertEquals(HeatingMessage.Saved, f.vm.state.value.message)
    }
}
