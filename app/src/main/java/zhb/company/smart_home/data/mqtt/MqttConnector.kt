package zhb.company.smart_home.data.mqtt

/** One broker to try: host, port and optional credentials (TLS is used on port 8883). */
data class MqttEndpoint(
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
) {
    val useTls: Boolean get() = port == 8883

    /**
     * A private IPv4 address of a home network (10/8, 172.16/12, 192.168/16). Tailscale's
     * 100.64/10 addresses and host names do not count.
     */
    val isOnLan: Boolean get() {
        val parts = host.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != 4) return false
        val (a, b) = parts
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }

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

/** Last Will: the broker publishes [payload] on [topic] (retained) when the client drops without a disconnect. */
data class MqttWill(val topic: String, val payload: String)

/** Callbacks for one connection attempt. Called on the MQTT client's own threads. */
interface MqttConnectionListener {
    fun onMessage(topic: String, payload: String, retained: Boolean)
    fun onConnected()
    fun onDisconnected(cause: String?)
}

/** A live broker connection, already subscribed to the requested topic filter. */
interface MqttConnection {
    /**
     * Hands the message to the broker. Blocking for a short while; false when the client refused
     * or failed it (e.g. the connection is gone), true when it went out or is still on its way.
     */
    fun publish(topic: String, payload: String, retain: Boolean, qos: MqttQualityOfService): Boolean
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

    /** As [connect], with a Last Will; connectors without will support ignore it. */
    fun connect(endpoint: MqttEndpoint, topicFilter: String, listener: MqttConnectionListener, will: MqttWill?): MqttConnection? =
        connect(endpoint, topicFilter, listener)
}
