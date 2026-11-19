package zhb.company.smart_home.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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

        val changed = GoodNightUseCase(repo)()

        assertTrue(changed)
        val sent = connector.lastConnection!!.published.drop(1).map { it.topic to it.payload } // drop "s" request
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

        assertFalse(GoodNightUseCase(repo)())
        assertEquals(1, connector.lastConnection!!.published.size) // only the "s" request
    }
}
