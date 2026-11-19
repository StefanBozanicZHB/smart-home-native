package zhb.company.smart_home.domain

import zhb.company.smart_home.data.mqtt.MqttTopics
import zhb.company.smart_home.data.mqtt.SmartHomeRepository

/** Turns off every currently-on device in one batch, mirroring `Parameters.goodNight()`. */
class GoodNightUseCase(private val repository: SmartHomeRepository) {

    suspend operator fun invoke(): Boolean {
        val state = repository.state.value
        val onDevices = PowerDevices.ALL.filter { it.isOn(state) }
        onDevices.forEach { repository.publish(it.controlTopic, it.offPayload) }
        if (state.acOn) {
            repository.publish(MqttTopics.NODE8_AC_COMMAND, AcCommandBuilder.POWER_OFF_COMMAND)
            repository.setAcPower(false)
        }
        return onDevices.isNotEmpty() || state.acOn
    }
}
