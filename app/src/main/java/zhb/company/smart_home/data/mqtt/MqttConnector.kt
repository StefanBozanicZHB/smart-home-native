package zhb.company.smart_home.data.mqtt

/** One broker to try: host, port and optional credentials (TLS is used on port 8883). */
data class MqttEndpoint(
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
) {
    val useTls: Boolean get() = port == 8883

    companion object {
        /** Builds an endpoint from the raw strings stored in Firebase; null if host or port is unusable. */
        fun parse(host: String, port: String, username: String, password: String): MqttEndpoint? {
            if (host.isBlank()) return null
            val portInt = port.trim().toIntOrNull() ?: return null
            return MqttEndpoint(host.trim(), portInt, username, password)
        }
    }
}

enum class MqttQualityOfService { AT_MOST_ONCE, AT_LEAST_ONCE, EXACTLY_ONCE }

/** Callbacks for one connection attempt. Called on the MQTT client's own threads. */
interface MqttConnectionListener {
    fun onMessage(topic: String, payload: String, retained: Boolean)
    fun onConnected()
    fun onDisconnected(cause: String?)
}

/** A live broker connection, already subscribed to the requested topic filter. */
interface MqttConnection {
    /** Best-effort, fire-and-forget publish; failures are not reported. */
    fun publish(topic: String, payload: String, retain: Boolean, qos: MqttQualityOfService)
    fun disconnect()
}

/**
 * Opens broker connections. The production implementation is [HiveMqConnector];
 * tests use a fake so [SmartHomeRepository] can be exercised without a broker.
 */
interface MqttConnector {
    /**
     * Connects to [endpoint] and subscribes to [topicFilter]. Blocking; returns null
     * if the broker cannot be reached or the subscription fails.
     */
    fun connect(endpoint: MqttEndpoint, topicFilter: String, listener: MqttConnectionListener): MqttConnection?
}
