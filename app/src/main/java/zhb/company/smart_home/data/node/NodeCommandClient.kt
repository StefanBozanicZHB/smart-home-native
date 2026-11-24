package zhb.company.smart_home.data.node

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.MqttQualityOfService
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.domain.node.NodeCommandResult

sealed interface CommandOutcome {
    /** [saved] false: active on the node but not stored, so lost on the next restart. */
    data class Accepted(val rev: Long?, val saved: Boolean = true) : CommandOutcome
    data class Rejected(val error: String) : CommandOutcome
    data object Timeout : CommandOutcome
    data object NotConnected : CommandOutcome
}

/**
 * Commands for nodes with the new JSON API: publishes a command with an `id` and waits for the
 * answer with the same `id` on `<node>/result`. The owner feeds every `result` payload to
 * [onResult].
 */
class NodeCommandClient(
    private val mqtt: SmartHomeRepository,
    private val timeoutMs: Long = 10_000,
    private val newId: () -> String = defaultIdGenerator(),
) {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<NodeCommandResult>>()

    /** Called on the MQTT client's thread with a `<node>/result` payload. */
    fun onResult(payload: String) {
        val result = NodeCommandResult.parse(payload) ?: return
        val key = result.id ?: commandKey(result.command)
        pending[key]?.complete(result)
    }

    suspend fun send(topic: String, payload: (id: String) -> String): CommandOutcome {
        if (mqtt.connectionState.value !is MqttConnectionState.Connected) return CommandOutcome.NotConnected
        return sendAndAwait(newId(), topic, payload)
    }

    /**
     * A command whose payload cannot carry an id (a bare JSON `null`, e.g. removing a schedule):
     * the answer is matched by its `cmd` ([command], the topic after `<node>/`) instead.
     */
    suspend fun sendWithoutId(topic: String, command: String, payload: String): CommandOutcome {
        if (mqtt.connectionState.value !is MqttConnectionState.Connected) return CommandOutcome.NotConnected
        return sendAndAwait(commandKey(command), topic) { payload }
    }

    private suspend fun sendAndAwait(id: String, topic: String, payload: (id: String) -> String): CommandOutcome {
        val answer = CompletableDeferred<NodeCommandResult>()
        pending[id] = answer
        return try {
            mqtt.publish(topic, payload(id), qos = MqttQualityOfService.AT_LEAST_ONCE)
            val result = withTimeoutOrNull(timeoutMs) { answer.await() }
            when {
                result == null -> CommandOutcome.Timeout
                result.ok -> CommandOutcome.Accepted(result.rev, result.saved)
                else -> CommandOutcome.Rejected(result.error.orEmpty())
            }
        } finally {
            pending.remove(id)
        }
    }

    companion object {
        private fun commandKey(command: String) = "cmd:$command"

        /** Ids unique per app run and readable in broker logs: `app-<start time>-<counter>`. */
        fun defaultIdGenerator(): () -> String {
            val session = System.currentTimeMillis() % 1_000_000
            val counter = AtomicLong()
            return { "app-$session-${counter.incrementAndGet()}" }
        }
    }
}
