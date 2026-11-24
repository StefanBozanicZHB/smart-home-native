package zhb.company.smart_home.data.mqtt

/** In-memory [MqttConnector]: hosts in [unreachableHosts] fail, every other host connects. */
class FakeMqttConnector(
    unreachableHosts: Set<String> = emptySet(),
) : MqttConnector {

    /** Hosts that refuse connections; tests change it to simulate a broker going down or up. */
    var unreachableHosts: Set<String> = unreachableHosts

    /**
     * Runs while [connect] is "blocked", after the client exists but before it is returned: tests
     * use it to send messages, drop the client or call the repository in the middle of an attempt.
     */
    var duringConnect: ((listener: MqttConnectionListener) -> Unit)? = null

    val attempts = mutableListOf<MqttEndpoint>()
    val listeners = mutableListOf<MqttConnectionListener>()
    val connections = mutableListOf<FakeMqttConnection>()
    var lastConnection: FakeMqttConnection? = null
        private set

    override fun connect(
        endpoint: MqttEndpoint,
        topicFilter: String,
        listener: MqttConnectionListener,
    ): MqttConnection? {
        attempts += endpoint
        listeners += listener
        if (endpoint.host in unreachableHosts) return null
        duringConnect?.invoke(listener)
        return FakeMqttConnection(endpoint, topicFilter).also {
            lastConnection = it
            connections += it
        }
    }
}

data class PublishedMessage(val topic: String, val payload: String, val retain: Boolean, val qos: MqttQualityOfService)

class FakeMqttConnection(val endpoint: MqttEndpoint, val topicFilter: String) : MqttConnection {
    val published = mutableListOf<PublishedMessage>()
    var disconnected = false
        private set

    /** When true, publish fails the way a client does after its connection is gone. */
    var refusePublish = false

    override fun publish(topic: String, payload: String, retain: Boolean, qos: MqttQualityOfService): Boolean {
        if (refusePublish || disconnected) return false
        published += PublishedMessage(topic, payload, retain, qos)
        return true
    }

    override fun disconnect() {
        disconnected = true
    }
}
