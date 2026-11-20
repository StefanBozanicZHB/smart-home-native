package zhb.company.smart_home.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import zhb.company.smart_home.data.garden.Node1Repository
import zhb.company.smart_home.data.garden.Node2Repository
import zhb.company.smart_home.data.mqtt.MqttTopics
import zhb.company.smart_home.data.mqtt.SmartHomeRepository

/**
 * Turns off every currently-on device in one batch, mirroring `Parameters.goodNight()`. Nodes with
 * the JSON API (Node 1 fountain and sprinklers, Node 2 yard lights) get their commands; they are
 * sent together and their answers awaited.
 */
class GoodNightUseCase(
    private val repository: SmartHomeRepository,
    private val node1: Node1Repository,
    private val node2: Node2Repository,
) {

    suspend operator fun invoke(): Boolean {
        val state = repository.state.value
        val onDevices = PowerDevices.ALL.filter { it.isOn(state) }
        onDevices.forEach { repository.publish(it.controlTopic, it.offPayload) }
        if (state.acOn) {
            repository.publish(MqttTopics.NODE8_AC_COMMAND, AcCommandBuilder.POWER_OFF_COMMAND)
            repository.setAcPower(false)
        }
        val yard = node1.snapshot.value.state
        val lights = node2.state.value
        val jsonCommands = buildList<suspend () -> Unit> {
            if (yard?.fountain?.pump == true) add { node1.setPump(false) }
            if (yard?.fountain?.light == true) add { node1.setLight(false) }
            if (yard?.run != null) add { node1.stopRun() }
            if (lights?.neon == true) add { node2.setNeon(false) }
            if (lights?.lamp == true) add { node2.setLamp(false) }
        }
        coroutineScope { jsonCommands.map { async { it() } }.awaitAll() }
        return onDevices.isNotEmpty() || state.acOn || jsonCommands.isNotEmpty()
    }
}
