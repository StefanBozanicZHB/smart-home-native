package zhb.company.smart_home.data.node

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.MqttQualityOfService
import zhb.company.smart_home.data.mqtt.SmartHomeRepository

/**
 * One node's share of the MQTT connection, the part every node repository has in common:
 * - passes the node's messages to [onMessage] with the topic relative to [base] (`state`, `zones/ground`);
 * - feeds `<node>/result` to [commands], which match the answers to the commands sent;
 * - asks the node for its state (`config/get`) after every (re)connect. Retained topics arrive on
 *   subscribe anyway; this also covers a node that restarted without its retained data.
 * [onMessage] runs on the MQTT client's thread.
 */
class NodeChannel(
    private val mqtt: SmartHomeRepository,
    scope: CoroutineScope,
    /** `bozaSub/kuca/<node>/`. */
    val base: String,
    commandTimeoutMs: Long,
    newId: () -> String,
    private val onMessage: (path: String, payload: String) -> Unit,
) {
    val commands = NodeCommandClient(mqtt, commandTimeoutMs, newId)

    init {
        mqtt.addMessageListener { topic, payload, _ ->
            if (topic.startsWith(base)) {
                val path = topic.substring(base.length)
                if (path == RESULT) commands.onResult(payload) else onMessage(path, payload)
            }
        }
        scope.launch {
            mqtt.connectionState
                .map { it is MqttConnectionState.Connected }
                .distinctUntilChanged()
                .collect { connected -> if (connected) requestState() }
        }
    }

    suspend fun requestState() {
        mqtt.publish("${base}config/get", "{}", qos = MqttQualityOfService.AT_LEAST_ONCE)
    }

    private companion object {
        const val RESULT = "result"
    }
}
