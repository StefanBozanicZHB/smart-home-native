package zhb.company.smart_home.data.mqtt

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zhb.company.smart_home.data.remote.MqttConfigSource
import zhb.company.smart_home.data.remote.dto.MqttClientConfigDto

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectTest {

    private val config = MqttClientConfigDto(
        mqttServer = "primary", mqttPort = "1883", backupMqttServer = "backup", backupMqttPort = "8883",
    )

    private fun TestScope.repository(connector: FakeMqttConnector) = SmartHomeRepository(
        configSource = MqttConfigSource { Result.success(config) },
        connector = connector,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
        healthCheckIntervalMs = 60 * 60_000,
    )

    private fun FakeMqttConnector.dropActive() = listeners.last().onDisconnected("network lost")

    @Test
    fun `a dropped connection reconnects by itself`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)
        repo.connect("token")
        val first = connector.lastConnection!!

        connector.dropActive()
        assertEquals(MqttConnectionState.Disconnected("network lost"), repo.connectionState.value)

        advanceTimeBy(1_001)
        assertEquals(MqttConnectionState.Connected, repo.connectionState.value)
        val second = connector.lastConnection!!
        assertTrue(second !== first)
    }

    @Test
    fun `while the broker is down it retries with growing pauses, primary then backup`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)
        repo.connect("token")
        connector.unreachableHosts = setOf("primary", "backup")
        connector.attempts.clear()
        connector.dropActive()

        advanceTimeBy(1_001) // attempt 1 after 1 s
        assertEquals(listOf("primary", "backup"), connector.attempts.map { it.host })
        assertEquals(MqttConnectionState.Reconnecting(1), repo.connectionState.value)
        advanceTimeBy(2_000) // attempt 2 after 2 s more
        advanceTimeBy(5_000) // attempt 3 after 5 s more
        assertEquals(6, connector.attempts.size)
        assertEquals(MqttConnectionState.Reconnecting(3), repo.connectionState.value)

        connector.unreachableHosts = setOf("primary") // backup comes back
        advanceTimeBy(10_001)
        assertEquals(MqttConnectionState.Connected, repo.connectionState.value)
        assertEquals("backup", connector.lastConnection!!.endpoint.host)
    }

    @Test
    fun `pause disconnects and stops reconnecting, resume reconnects at once`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)
        repo.connect("token")
        val first = connector.lastConnection!!

        repo.pause()
        assertTrue(first.disconnected)
        assertEquals(MqttConnectionState.Paused, repo.connectionState.value)
        connector.attempts.clear()
        advanceTimeBy(10 * 60_000)
        assertTrue("no reconnects while paused", connector.attempts.isEmpty())

        repo.resume()
        runCurrent()
        assertEquals(MqttConnectionState.Connected, repo.connectionState.value)
        assertEquals(1, connector.attempts.size)
    }

    @Test
    fun `network coming back skips the current pause, but not more often than every 5 s`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)
        repo.connect("token")
        connector.unreachableHosts = setOf("primary", "backup")
        connector.dropActive()
        advanceTimeBy(1_001 + 2_000 + 5_000) // three failed attempts, now waiting 10 s
        connector.attempts.clear()

        repo.onNetworkAvailable()
        runCurrent()
        assertEquals("immediate attempt on both brokers", 2, connector.attempts.size)

        repo.onNetworkAvailable() // e.g. a signal-strength change right after
        runCurrent()
        assertEquals("rate limited", 2, connector.attempts.size)

        connector.unreachableHosts = emptySet()
        advanceTimeBy(5_001)
        repo.onNetworkAvailable()
        runCurrent()
        assertEquals(MqttConnectionState.Connected, repo.connectionState.value)
    }

    @Test
    fun `sign-out forgets the session and never reconnects`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)
        repo.connect("token")
        assertTrue(repo.hasSession)

        repo.disconnect()
        connector.attempts.clear()
        advanceTimeBy(10 * 60_000)
        repo.onNetworkAvailable()
        repo.resume()
        runCurrent()

        assertFalse(repo.hasSession)
        assertTrue(connector.attempts.isEmpty())
        assertEquals(MqttConnectionState.Idle, repo.connectionState.value)
    }

    // --- attempts that finish late or overlap (a blocking connect cannot be interrupted) -----------

    @Test
    fun `pause during a connect attempt closes the new client instead of keeping it`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)
        repo.connect("token")
        connector.dropActive()
        connector.duringConnect = { repo.pause() }

        advanceTimeBy(1_001)

        assertTrue("the client that connected after pause is closed", connector.lastConnection!!.disconnected)
        assertEquals(MqttConnectionState.Paused, repo.connectionState.value)
        assertFalse(repo.publish("t", "1"))
    }

    @Test
    fun `sign-out during a connect attempt leaves nothing connected`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)
        repo.connect("token")
        connector.dropActive()
        connector.duringConnect = { repo.disconnect() }

        advanceTimeBy(1_001)

        assertTrue(connector.lastConnection!!.disconnected)
        assertEquals(MqttConnectionState.Idle, repo.connectionState.value)
        assertFalse(repo.hasSession)
    }

    @Test
    fun `a client that drops before it is adopted is closed and the next broker is used`() = runTest {
        val connector = FakeMqttConnector()
        connector.duringConnect = { listener ->
            if (connector.attempts.last().host == "primary") listener.onDisconnected("dropped")
        }
        val repo = repository(connector)

        repo.connect("token")

        val (primary, backup) = connector.connections
        assertTrue("dead primary client closed", primary.disconnected)
        assertEquals("backup", backup.endpoint.host)
        assertFalse(backup.disconnected)
        assertEquals(MqttConnectionState.Connected, repo.connectionState.value)
    }

    @Test
    fun `resume during a running attempt does not open a second connection`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)
        connector.duringConnect = { repo.resume() }

        repo.connect("token")
        connector.duringConnect = null
        runCurrent()

        assertEquals(1, connector.connections.size)
        assertFalse(connector.connections.single().disconnected)
        assertEquals(MqttConnectionState.Connected, repo.connectionState.value)
    }

    @Test
    fun `retained messages during the subscribe count, messages from a replaced client do not`() = runTest {
        val connector = FakeMqttConnector()
        connector.duringConnect = { listener -> listener.onMessage(MqttTopics.NODE4_TEMPERATURE, "21.5", true) }
        val repo = repository(connector)
        repo.connect("token")
        assertEquals("21.50", repo.state.value.temperatureLivingRoomDown)

        val oldListener = connector.listeners.last()
        connector.duringConnect = null
        connector.dropActive()
        advanceTimeBy(1_001)
        oldListener.onMessage(MqttTopics.NODE4_TEMPERATURE, "30.0", true)

        assertEquals("21.50", repo.state.value.temperatureLivingRoomDown)
        connector.listeners.last().onMessage(MqttTopics.NODE4_TEMPERATURE, "22.0", false)
        assertEquals("22.00", repo.state.value.temperatureLivingRoomDown)
    }

    @Test
    fun `publish reports a refused message`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)
        repo.connect("token")
        assertTrue(repo.publish("t", "1"))

        connector.lastConnection!!.refusePublish = true
        assertFalse(repo.publish("t", "1"))
    }

    @Test
    fun `background longer than a minute pauses, a quick app switch does not`() = runTest {
        val connector = FakeMqttConnector()
        val repo = repository(connector)
        val lifecycle = ConnectionLifecycle(repo, backgroundScope, pauseAfterMs = 60_000)
        repo.connect("token")

        lifecycle.onBackground()
        advanceTimeBy(30_000)
        lifecycle.onForeground()
        advanceTimeBy(60_000)
        assertEquals(MqttConnectionState.Connected, repo.connectionState.value)

        lifecycle.onBackground()
        advanceTimeBy(60_001)
        assertEquals(MqttConnectionState.Paused, repo.connectionState.value)

        lifecycle.onForeground()
        runCurrent()
        assertEquals(MqttConnectionState.Connected, repo.connectionState.value)
    }
}
