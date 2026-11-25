package zhb.company.smart_home.data.door

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import zhb.company.smart_home.data.mqtt.BrokerCache
import zhb.company.smart_home.data.mqtt.MqttConnectionListener
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.MqttConnector
import zhb.company.smart_home.data.mqtt.MqttEndpoint
import zhb.company.smart_home.data.mqtt.MqttQualityOfService
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.mqtt.toEndpoints
import zhb.company.smart_home.data.node.CommandOutcome
import zhb.company.smart_home.data.node.NodeCommandClient
import zhb.company.smart_home.data.remote.MqttConfigSource
import zhb.company.smart_home.data.repository.AuthRepository
import zhb.company.smart_home.domain.node.NodeCommandResult
import zhb.company.smart_home.domain.node.NodeJson

/**
 * Opens the front door for the home screen button, with as little work as possible:
 * - the app is connected anyway: the command goes over that connection;
 * - otherwise one short connection to the broker saved at the last sign-in ([BrokerCache]),
 *   subscribed only to `node3/result` (no retained state of the whole house), closed as soon as the
 *   node answers or after [answerTimeoutMs]. Only when nothing is saved yet is the Firebase config read.
 * Nothing runs between taps.
 */
class DoorOpener(
    private val mqtt: SmartHomeRepository,
    private val node3: Node3Repository,
    private val auth: AuthRepository,
    private val brokers: BrokerCache,
    private val configSource: MqttConfigSource,
    private val connector: MqttConnector,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val answerTimeoutMs: Long = 5_000,
    private val newId: () -> String = NodeCommandClient.defaultIdGenerator(),
) {
    private val busy = AtomicBoolean(false)

    /** Null when a previous tap is still being handled (a second tap meanwhile does nothing). */
    suspend fun open(): CommandOutcome? {
        if (!busy.compareAndSet(false, true)) return null
        return try {
            when {
                // Signed out (or the session was revoked): the button does nothing.
                !auth.hasSavedSession() -> CommandOutcome.NotConnected
                mqtt.connectionState.value is MqttConnectionState.Connected -> node3.openDoor()
                else -> {
                    val endpoints = brokers.load() ?: fetchBrokers() ?: return CommandOutcome.NotConnected
                    withContext(ioDispatcher) { sendOnce(endpoints) }
                }
            }
        } finally {
            busy.set(false)
        }
    }

    private suspend fun fetchBrokers(): List<MqttEndpoint>? {
        val token = auth.idTokenResult().getOrNull() ?: return null
        val endpoints = configSource.fetchMqttClientConfig(token).getOrNull()?.toEndpoints()?.takeIf { it.isNotEmpty() } ?: return null
        brokers.save(endpoints)
        return endpoints
    }

    /** Called on the IO dispatcher: [MqttConnector.connect] blocks. */
    private suspend fun sendOnce(endpoints: List<MqttEndpoint>): CommandOutcome {
        val id = newId()
        val answer = CompletableDeferred<NodeCommandResult>()
        val listener = object : MqttConnectionListener {
            override fun onMessage(topic: String, payload: String, retained: Boolean) {
                if (topic != RESULT_TOPIC) return
                NodeCommandResult.parse(payload)?.takeIf { it.id == id }?.let { answer.complete(it) }
            }
            override fun onConnected() = Unit
            override fun onDisconnected(cause: String?) = Unit
        }
        for (endpoint in endpoints) {
            val connection = connector.connect(endpoint, RESULT_TOPIC, listener) ?: continue
            try {
                if (!connection.publish(Node3Topics.DOOR_OPEN, NodeJson.idOnly(id), false, MqttQualityOfService.AT_LEAST_ONCE)) {
                    return CommandOutcome.NotConnected
                }
                val result = withTimeoutOrNull(answerTimeoutMs) { answer.await() } ?: return CommandOutcome.Timeout
                return if (result.ok) CommandOutcome.Accepted(result.rev, result.saved) else CommandOutcome.Rejected(result.error.orEmpty())
            } finally {
                connection.disconnect()
            }
        }
        return CommandOutcome.NotConnected
    }

    private companion object {
        const val RESULT_TOPIC = "${Node3Topics.BASE}result"
    }
}
