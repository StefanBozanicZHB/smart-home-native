package zhb.company.smart_home.data.mqtt

import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** [MqttConnector] backed by the HiveMQ MQTT 3.1.1 async client. Payloads are UTF-8 (JSON). */
class HiveMqConnector(
    private val timeoutSeconds: Long = 5,
    private val keepAliveSeconds: Int = 20,
    /** How long [MqttConnection.publish] waits to see whether the client refuses a message. */
    private val publishCheckMs: Long = 2_000,
) : MqttConnector {

    override fun connect(
        endpoint: MqttEndpoint,
        topicFilter: String,
        listener: MqttConnectionListener,
    ): MqttConnection? {
        // A timed-out attempt keeps connecting in the background; once given up it is closed
        // (now, or as soon as it does connect), so it never lives on next to the next attempt.
        val givenUp = AtomicBoolean(false)
        val clientRef = AtomicReference<Mqtt3AsyncClient>()
        return try {
            val builder = Mqtt3Client.builder()
                .identifier("native_client_${System.currentTimeMillis()}")
                .serverHost(endpoint.host)
                .serverPort(endpoint.port)
                .addConnectedListener {
                    if (givenUp.get()) clientRef.get()?.disconnect() else listener.onConnected()
                }
                .addDisconnectedListener { context ->
                    if (!givenUp.get()) listener.onDisconnected(context.cause.message)
                }
                .let { if (endpoint.useTls) it.sslWithDefaultConfig() else it }
                .let {
                    if (endpoint.username.isBlank()) {
                        it
                    } else {
                        it.simpleAuth(
                            Mqtt3SimpleAuth.builder()
                                .username(endpoint.username)
                                .password(endpoint.password.toByteArray(Charsets.UTF_8))
                                .build()
                        )
                    }
                }
            val client = builder.buildAsync().also(clientRef::set)
            client.connectWith()
                .cleanSession(true)
                .keepAlive(keepAliveSeconds)
                .send()
                .get(timeoutSeconds, TimeUnit.SECONDS)

            client.subscribeWith()
                .topicFilter(topicFilter)
                .qos(MqttQos.AT_MOST_ONCE)
                .callback { publish ->
                    if (!givenUp.get()) {
                        listener.onMessage(
                            topic = publish.topic.toString(),
                            payload = String(publish.payloadAsBytes, Charsets.UTF_8),
                            retained = publish.isRetain,
                        )
                    }
                }
                .send()
                .get(timeoutSeconds, TimeUnit.SECONDS)

            HiveMqConnection(client, publishCheckMs)
        } catch (e: Exception) {
            givenUp.set(true)
            clientRef.get()?.disconnect()
            // Why it failed (timeout, refused, not authorized, TLS); never the credentials.
            val cause = (e.cause ?: e)
            runCatching { android.util.Log.w("HiveMqConnector", "${endpoint.host}:${endpoint.port}: ${cause.javaClass.simpleName}: ${cause.message}") }
            null
        }
    }

    private class HiveMqConnection(private val client: Mqtt3AsyncClient, private val checkMs: Long) : MqttConnection {
        override fun publish(topic: String, payload: String, retain: Boolean, qos: MqttQualityOfService): Boolean = try {
            client.publishWith()
                .topic(topic)
                .qos(qos.toHiveMq())
                .payload(payload.toByteArray(Charsets.UTF_8))
                .retain(retain)
                .send()
                .get(checkMs, TimeUnit.MILLISECONDS)
            true
        } catch (_: TimeoutException) {
            true // not acknowledged yet, but not refused either: it is on its way
        } catch (_: ExecutionException) {
            false // refused, e.g. the client is no longer connected
        } catch (_: Exception) {
            false
        }

        override fun disconnect() {
            client.disconnect()
        }
    }
}

private fun MqttQualityOfService.toHiveMq(): MqttQos = when (this) {
    MqttQualityOfService.AT_MOST_ONCE -> MqttQos.AT_MOST_ONCE
    MqttQualityOfService.AT_LEAST_ONCE -> MqttQos.AT_LEAST_ONCE
    MqttQualityOfService.EXACTLY_ONCE -> MqttQos.EXACTLY_ONCE
}
