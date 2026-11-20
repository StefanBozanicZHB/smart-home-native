package zhb.company.smart_home.data.mqtt

/** In-memory [MqttConnector]: hosts in [unreachableHosts] fail, every other host connects. */
class FakeMqttConnector(
    unreachableHosts: Set<String> = emptySet(),
) : MqttConnector {

    /** Hosts that refuse connections; tests change it to simulate a broker going down or up. */
    var unreachableHosts: Set<String> = unreachableHosts

    val attempts = mutableListOf<MqttEndpoint>()
    val listeners = mutableListOf<MqttConnectionListener>()
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
        return FakeMqttConnection(endpoint, topicFilter).also { lastConnection = it }
    }
}

data class PublishedMessage(val topic: String, val payload: String, val retain: Boolean, val qos: MqttQualityOfService)

class FakeMqttConnection(val endpoint: MqttEndpoint, val topicFilter: String) : MqttConnection {
    val published = mutableListOf<PublishedMessage>()
    var disconnected = false
        private set

    override fun publish(topic: String, payload: String, retain: Boolean, qos: MqttQualityOfService) {
        published += PublishedMessage(topic, payload, retain, qos)
    }

    override fun disconnect() {
        disconnected = true
    }
}
