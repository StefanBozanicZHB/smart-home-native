package zhb.company.smart_home.data.mqtt

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zhb.company.smart_home.data.remote.MqttConfigSource
import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto

@OptIn(ExperimentalCoroutinesApi::class)
class SmartHomeRepositoryTest {

    private val config = MqttClientConfigDto(
        mqttServer = "primary", mqttPort = "1883", mqttUsername = "u", mqttPassword = "p",
        backupMqttServer = "backup", backupMqttPort = "8883",
    )

    private fun repository(
        connector: FakeMqttConnector,
        configSource: MqttConfigSource = MqttConfigSource { Result.success(config) },
    ) = SmartHomeRepository(configSource, connector, UnconfinedTestDispatcher())

    // --- connect ---

    @Test
    fun `connect uses the primary broker and requests legacy node state`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)

        repo.connect("token")

        assertEquals(MqttConnectionState.Connected, repo.connectionState.value)
        assertEquals(listOf(MqttEndpoint("primary", 1883, "u", "p")), connector.attempts)
        val connection = connector.lastConnection!!
        assertEquals(MqttTopics.SUBSCRIBE_ALL, connection.topicFilter)
        assertEquals(MqttTopics.NODE1_REQUEST_STATUS, connection.published.single().topic)
        assertEquals("s", connection.published.single().payload)
    }

    @Test
    fun `connect falls back to the backup broker`() = runTest {
        val connector = FakeMqttConnector(unreachableHosts = setOf("primary"))
        val repo = repository(connector)

        repo.connect("token")

        assertEquals(MqttConnectionState.Connected, repo.connectionState.value)
        assertEquals(listOf("primary", "backup"), connector.attempts.map { it.host })
        assertTrue(connector.lastConnection!!.endpoint.useTls)
    }

    @Test
    fun `connect fails when no broker is reachable`() = runTest {
        val repo = repository(FakeMqttConnector(unreachableHosts = setOf("primary", "backup")))

        repo.connect("token")

        assertTrue(repo.connectionState.value is MqttConnectionState.Failed)
    }

    @Test
    fun `connect reports a config error without touching the broker`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector, MqttConfigSource { Result.failure(IllegalStateException("no config")) })

        repo.connect("token")

        assertEquals(MqttConnectionState.Failed("no config"), repo.connectionState.value)
        assertTrue(connector.attempts.isEmpty())
    }

    @Test
    fun `broker drop on the active connection is reported as Disconnected`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)
        repo.connect("token")

        connector.listeners.last().onDisconnected("network lost")

        assertEquals(MqttConnectionState.Disconnected("network lost"), repo.connectionState.value)
    }

    @Test
    fun `late callbacks from a failed attempt are ignored`() = runTest {
        val connector = FakeMqttConnector(unreachableHosts = setOf("primary"))
        val repo = repository(connector)
        repo.connect("token")

        connector.listeners.first().onDisconnected("stale primary")

        assertEquals(MqttConnectionState.Connected, repo.connectionState.value)
    }

    // --- publish ---

    @Test
    fun `publish is dropped while not connected and forwarded once connected`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)

        repo.publish("bozaSub/kuca/node2/neonka", "1")
        repo.connect("token")
        repo.publish("bozaSub/kuca/node2/neonka", "1", retain = true)

        val sent = connector.lastConnection!!.published.last()
        assertEquals(PublishedMessage("bozaSub/kuca/node2/neonka", "1", true, MqttQualityOfService.EXACTLY_ONCE), sent)
        assertEquals(2, connector.lastConnection!!.published.size) // "s" request + this one
    }

    @Test
    fun `disconnect closes the connection and returns to Idle`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)
        repo.connect("token")

        repo.disconnect()

        assertTrue(connector.lastConnection!!.disconnected)
        assertEquals(MqttConnectionState.Idle, repo.connectionState.value)
    }

    // --- incoming messages ---

    @Test
    fun `temperatures are formatted with two decimals`() {
        val repo = repository(FakeMqttConnector())

        repo.handleMessage(MqttTopics.NODE2_TEMPERATURE, "7.5", retained = true)
        repo.handleMessage(MqttTopics.NODE4_TEMPERATURE, "nan", retained = true)

        assertEquals("7.50", repo.state.value.temperatureOutdoor)
        assertEquals("nan", repo.state.value.temperatureLivingRoomDown)
    }

    @Test
    fun `node6 water temperature from the English topic, a withdrawn value shows as missing`() {
        val repo = repository(FakeMqttConnector())
        repo.handleMessage("bozaSub/kuca/node6/temperature", "24.31", retained = true)
        repo.handleMessage("bozaSub/kuca/node6/pool/temperature", "-127", retained = true)
        assertEquals("24.31", repo.state.value.temperaturePool)
        repo.handleMessage("bozaSub/kuca/node6/temperature", "", retained = true)
        assertEquals("--", zhb.company.smart_home.ui.format.Readings.temperature(repo.state.value.temperaturePool))
    }

    @Test
    fun `node2 outdoor temperature from the English topic, a withdrawn value shows as missing`() {
        val repo = repository(FakeMqttConnector())

        repo.handleMessage("bozaSub/kuca/node2/temperature", "11.94", retained = true)
        repo.handleMessage("bozaSub/kuca/node2/dvoriste/temperatura", "30", retained = true)
        assertEquals("11.94", repo.state.value.temperatureOutdoor)

        // DS18B20 failed for 5 min: the node deletes the retained value.
        repo.handleMessage("bozaSub/kuca/node2/temperature", "", retained = true)
        assertEquals("--", zhb.company.smart_home.ui.format.Readings.temperature(repo.state.value.temperatureOutdoor))
    }

    @Test
    fun `node8 temperature gets the calibration offset`() {
        val repo = repository(FakeMqttConnector())

        repo.handleMessage(MqttTopics.NODE8_TEMPERATURE, "28.25", retained = false)

        assertEquals("22.25", repo.state.value.temperatureLivingRoomUp)
    }

    @Test
    fun `only live (non-retained) messages mark a node online`() {
        val repo = repository(FakeMqttConnector())

        repo.handleMessage(MqttTopics.NODE8_HUMIDITY, "40", retained = true)
        assertFalse(repo.state.value.isNodeOnline(8))

        repo.handleMessage(MqttTopics.NODE8_HUMIDITY, "41", retained = false)
        assertTrue(repo.state.value.isNodeOnline(8))
    }

    @Test
    fun `new-firmware nodes use status and heartbeat, legacy nodes keep the old rule`() {
        var now = 0L
        val repo = SmartHomeRepository(
            MqttConfigSource { Result.success(config) }, FakeMqttConnector(), UnconfinedTestDispatcher(), clock = { now },
        )

        // node4: retained "online", then a heartbeat; offline after 3 min of silence.
        repo.handleMessage("bozaSub/kuca/node4/status", "online", retained = true)
        assertTrue(repo.state.value.isNodeOnline(4))
        now = 60_000
        repo.handleMessage("bozaSub/kuca/node4/heartbeat", """{"fw":"node4-2.1.1","up":60}""", retained = false)
        now = 240_000
        repo.refreshNodeHealth()
        assertTrue(repo.state.value.isNodeOnline(4))
        now = 240_001
        repo.refreshNodeHealth()
        assertFalse(repo.state.value.isNodeOnline(4))
        assertEquals(NodeStatus.OFFLINE, repo.state.value.nodeStatus("node4"))

        // node5: Last Will.
        repo.handleMessage("bozaSub/kuca/node5/status", "offline", retained = true)
        assertEquals(NodeStatus.OFFLINE, repo.state.value.nodeStatus("node5"))

        // node8 (legacy): unknown until a live message, then online with no timeout.
        assertEquals(NodeStatus.UNKNOWN, repo.state.value.nodeStatus("node8"))
        repo.handleMessage(MqttTopics.NODE8_HUMIDITY, "40", retained = false)
        now = 10_000_000
        repo.refreshNodeHealth()
        assertTrue(repo.state.value.isNodeOnline(8))
    }

    @Test
    fun `losing the broker connection makes every node unknown`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)
        repo.connect("token")
        repo.handleMessage("bozaSub/kuca/node4/status", "online", retained = true)
        repo.handleMessage(MqttTopics.NODE8_HUMIDITY, "40", retained = false)

        connector.listeners.last().onDisconnected("network lost")

        assertEquals(NodeStatus.UNKNOWN, repo.state.value.nodeStatus("node4"))
        assertEquals(NodeStatus.UNKNOWN, repo.state.value.nodeStatus("node8"))
    }

    @Test
    fun `AC state is read from the same retained topics as the Flutter app`() {
        val repo = repository(FakeMqttConnector())

        repo.handleMessage("bozaSub/kuca/node8/ac/mode", "C", retained = true)
        repo.handleMessage("bozaSub/kuca/node8/ac/fan", "1", retained = true)
        repo.handleMessage("bozaSub/kuca/node8/ac/temp", "24", retained = true)

        val state = repo.state.value
        assertEquals(zhb.company.smart_home.domain.model.AcMode.HEAT, state.acMode)
        assertEquals(zhb.company.smart_home.domain.model.AcFanSpeed.MAX, state.acFanSpeed)
        assertEquals(24, state.acTemp)
    }

    @Test
    fun `stale node4 light states are ignored (node4 is sensor-only)`() {
        val repo = repository(FakeMqttConnector())

        repo.handleMessage("bozaSub/kuca/node4/dnevniBoravak/sijalica1/stanje", "1", retained = true)

        assertTrue(repo.state.value.listPowerDevice.isEmpty())
    }

    @Test
    fun `node4 room climate comes from the English topics, deleted value shows as missing`() {
        val repo = repository(FakeMqttConnector())

        repo.handleMessage("bozaSub/kuca/node4/temperature", "21.53", retained = true)
        repo.handleMessage("bozaSub/kuca/node4/humidity", "45.2", retained = true)
        repo.handleMessage("bozaSub/kuca/node4/dnevniBoravak/temperatura", "19.00", retained = true)
        assertEquals("21.53", repo.state.value.temperatureLivingRoomDown)
        assertEquals("45.2", repo.state.value.humidityLivingRoomDown)

        // AHT10 failed for 5 min: the node deletes the retained values.
        repo.handleMessage("bozaSub/kuca/node4/temperature", "", retained = true)
        repo.handleMessage("bozaSub/kuca/node4/humidity", "", retained = true)
        assertEquals("--", zhb.company.smart_home.ui.format.Readings.temperature(repo.state.value.temperatureLivingRoomDown))
        assertEquals("--", zhb.company.smart_home.ui.format.Readings.percent(repo.state.value.humidityLivingRoomDown))
    }

    @Test
    fun `AC power is tracked client-side and listed as a power device`() {
        val repo = repository(FakeMqttConnector())

        repo.setAcPower(true)

        assertTrue(repo.state.value.acOn)
        assertTrue("Klima uređaj" in repo.state.value.listPowerDevice)
    }
}
