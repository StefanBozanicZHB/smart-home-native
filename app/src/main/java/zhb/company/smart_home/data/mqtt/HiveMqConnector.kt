package zhb.company.smart_home.data.mqtt

import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth
import java.util.concurrent.TimeUnit

/** [MqttConnector] backed by the HiveMQ MQTT 3.1.1 async client. */
class HiveMqConnector(
    private val timeoutSeconds: Long = 5,
    private val keepAliveSeconds: Int = 20,
) : MqttConnector {

    override fun connect(
        endpoint: MqttEndpoint,
        topicFilter: String,
        listener: MqttConnectionListener,
    ): MqttConnection? = try {
        val builder = Mqtt3Client.builder()
            .identifier("native_client_${System.currentTimeMillis()}")
            .serverHost(endpoint.host)
            .serverPort(endpoint.port)
            .addConnectedListener { listener.onConnected() }
            .addDisconnectedListener { context -> listener.onDisconnected(context.cause.message) }
        if (endpoint.useTls) builder.sslWithDefaultConfig()
        if (endpoint.username.isNotBlank()) {
            builder.simpleAuth(
                Mqtt3SimpleAuth.builder()
                    .username(endpoint.username)
                    .password(endpoint.password.toByteArray(Charsets.UTF_8))
                    .build()
            )
        }
        val client = builder.buildAsync()
        client.connectWith()
            .cleanSession(true)
            .keepAlive(keepAliveSeconds)
            .send()
            .get(timeoutSeconds, TimeUnit.SECONDS)

        client.subscribeWith()
            .topicFilter(topicFilter)
            .qos(MqttQos.AT_MOST_ONCE)
            .callback { publish ->
                listener.onMessage(
                    topic = publish.topic.toString(),
                    payload = String(publish.payloadAsBytes, Charsets.ISO_8859_1),
                    retained = publish.isRetain,
                )
            }
            .send()
            .get(timeoutSeconds, TimeUnit.SECONDS)

        HiveMqConnection(client)
    } catch (_: Exception) {
        null
    }

    private class HiveMqConnection(private val client: Mqtt3AsyncClient) : MqttConnection {
        override fun publish(topic: String, payload: String, retain: Boolean, qos: MqttQualityOfService) {
            try {
                client.publishWith()
                    .topic(topic)
                    .qos(qos.toHiveMq())
                    .payload(payload.toByteArray(Charsets.ISO_8859_1))
                    .retain(retain)
                    .send()
            } catch (_: Exception) {
                // best-effort, publish failures are not surfaced
            }
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
