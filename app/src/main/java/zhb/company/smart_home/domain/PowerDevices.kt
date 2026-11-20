package zhb.company.smart_home.domain

import zhb.company.smart_home.data.mqtt.MqttTopics
import zhb.company.smart_home.domain.model.SmartHomeState

data class PowerDevice(
    val label: String,
    val controlTopic: String,
    val offPayload: String = "0",
    val isOn: (SmartHomeState) -> Boolean,
)

/**
 * Every switchable device the "devices on" count / GoodNight scene can see.
 * NOTE: doors/gate are intentionally excluded from this list (and therefore
 * from GoodNight) since silently toggling a lock felt unsafe to infer from
 * the source app's description - the original Flutter logic wasn't fully
 * verifiable either way.
 */
object PowerDevices {
    val ALL: List<PowerDevice> = listOf(
        PowerDevice("LED traka (dvorište)", MqttTopics.NODE2_LED_STRIP_CONTROL) { it.ledStripTerrace },
        PowerDevice("Sijalica (dvorište)", MqttTopics.NODE2_BULB_CONTROL) { it.bulbTerrace },
        PowerDevice("Sijalica 1 (šupa)", MqttTopics.node7BulbControl(1)) { it.bulbSmallHome1 },
        PowerDevice("Sijalica 2 (šupa)", MqttTopics.node7BulbControl(2)) { it.bulbSmallHome2 },
        PowerDevice("Sijalica 3 (šupa)", MqttTopics.node7BulbControl(3)) { it.bulbSmallHome3 },
        PowerDevice("Pumpa bazena", MqttTopics.NODE6_POOL_PUMP_CONTROL) { it.poolPump },
        PowerDevice("Grejanje bazena", MqttTopics.NODE6_POOL_HEATING_CONTROL) { it.poolHeating },
    )
}

fun computeListPowerDevice(state: SmartHomeState): List<String> {
    val base = PowerDevices.ALL.filter { it.isOn(state) }.map { it.label }
    return if (state.acOn) base + "Klima uređaj" else base
}
