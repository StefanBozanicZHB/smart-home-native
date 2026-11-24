package zhb.company.smart_home.data.mqtt

import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import zhb.company.smart_home.data.remote.MqttConfigSource
import zhb.company.smart_home.domain.model.Reading
import zhb.company.smart_home.domain.model.SmartHomeState

/**
 * Owns the MQTT connection and the live house state: node health and the plain measurements in
 * [state]. Nodes with a JSON API are handled by their own repositories, which get every message
 * through [addMessageListener].
 *
 * Connection lifecycle:
 * - [connect] after sign-in fetches the broker config (primary + backup) and connects once;
 * - if the connection later drops, it reconnects by itself with growing pauses
 *   ([RECONNECT_DELAYS_MS]), trying the primary then the backup broker each time;
 * - [pause] (app in the background) disconnects on purpose and stops reconnecting; [resume]
 *   reconnects at once; [onNetworkAvailable] skips the current pause when the network returns;
 * - [disconnect] (sign-out) forgets the session.
 * Every new connection resubscribes, so retained messages refresh the whole state.
 *
 * Connect attempts run one at a time ([attemptMutex]). [connect], [pause] and [disconnect] start a
 * new [epoch]: an attempt that finishes after that (a blocking connect cannot be interrupted) closes
 * its client instead of becoming the connection, so there is never more than one live client.
 */
class SmartHomeRepository(
    private val configSource: MqttConfigSource,
    private val connector: MqttConnector,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * Background scope for the node health check and automatic reconnects. Null in tests that
     * call [refreshNodeHealth] themselves and do not need reconnects.
     */
    private val scope: CoroutineScope? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val nodeStatus: NodeStatusTracker = NodeStatusTracker(),
    healthCheckIntervalMs: Long = 15_000,
) {
    private val _state = MutableStateFlow(SmartHomeState())
    val state: StateFlow<SmartHomeState> = _state.asStateFlow()

    private val _connectionState = MutableStateFlow<MqttConnectionState>(MqttConnectionState.Idle)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    @Volatile
    private var connection: MqttConnection? = null

    /** Brokers from the last successful config fetch; empty = no session (signed out or new process). */
    @Volatile
    private var endpoints: List<MqttEndpoint> = emptyList()

    @Volatile
    private var paused = false

    private var reconnectJob: Job? = null

    /** Only one connect attempt at a time. */
    private val attemptMutex = Mutex()

    /** Bumped when the session or its wish to be connected changes; older attempts are discarded. */
    @Volatile
    private var epoch = 0L
    private var lastNetworkRetryMs = Long.MIN_VALUE / 2

    /** The default network last reported by [onNetworkAvailable]; null until the first report. */
    @Volatile
    private var currentNetwork: Long? = null

    /** True once the user has signed in and the broker config is known in this process. */
    val hasSession: Boolean get() = endpoints.isNotEmpty()

    private val messageListeners = CopyOnWriteArrayList<(topic: String, payload: String, retained: Boolean) -> Unit>()

    /** Extra consumers of raw messages (e.g. the Node 5 repository). Called on the MQTT client thread. */
    fun addMessageListener(listener: (topic: String, payload: String, retained: Boolean) -> Unit) {
        messageListeners += listener
    }

    init {
        scope?.launch {
            while (isActive) {
                delay(healthCheckIntervalMs)
                refreshNodeHealth()
            }
        }
    }

    suspend fun connect(idToken: String) {
        cancelReconnect()
        paused = false
        // A new sign-in starts from scratch, with the config fetched below.
        closeConnection()
        _connectionState.value = MqttConnectionState.Connecting
        val config = configSource.fetchMqttClientConfig(idToken).getOrElse {
            logWarning("MQTT config from Firebase failed: ${it.message}")
            _connectionState.value = MqttConnectionState.Failed(it.message ?: "Konfiguracija nije pronađena")
            return
        }
        endpoints = listOfNotNull(
            MqttEndpoint.parse(config.mqttServer, config.mqttPort, config.mqttUsername, config.mqttPassword),
            MqttEndpoint.parse(config.backupMqttServer, config.backupMqttPort, config.backupMqttUsername, config.backupMqttPassword),
        )
        if (!connectToAnyEndpoint()) {
            _connectionState.value = MqttConnectionState.Failed("Konektivnost sa serverom ne postoji!")
        }
    }

    /** Sign-out: close the connection and forget the session. */
    fun disconnect() {
        cancelReconnect()
        endpoints = emptyList()
        paused = false
        closeConnection()
        _connectionState.value = MqttConnectionState.Idle
    }

    /** App went to the background: disconnect on purpose, no reconnects until [resume]. */
    fun pause() {
        if (!hasSession || paused) return
        paused = true
        cancelReconnect()
        closeConnection()
        _connectionState.value = MqttConnectionState.Paused
    }

    /** App is visible again: reconnect at once if needed. */
    fun resume() {
        paused = false
        if (hasSession && connection == null) reconnectNow()
    }

    /**
     * The device has a working default network ([network] identifies it, e.g. Wi-Fi or mobile data).
     * Without a connection: do not wait for the current reconnect pause. With one, when the default
     * network is another than before (Wi-Fi gone, mobile data took over): the old socket belongs to
     * the network that left and would look alive until the keep-alive fails (20-30 s), so it is
     * replaced at once.
     */
    fun onNetworkAvailable(network: Long? = null) {
        val previous = currentNetwork
        if (network != null) currentNetwork = network
        if (!hasSession || paused) return
        if (network != null && previous != null && network != previous && connection != null) {
            closeConnection()
            reconnectNow()
            return
        }
        if (connection != null || attemptMutex.isLocked) return
        // The system reports network changes often (e.g. signal strength); retry at most every 5 s.
        val now = clock()
        if (now - lastNetworkRetryMs < NETWORK_RETRY_MIN_INTERVAL_MS) return
        lastNetworkRetryMs = now
        reconnectNow()
    }

    /** Closes the connection on purpose; an attempt still in flight is discarded when it returns. */
    private fun closeConnection() {
        val current = synchronized(this) {
            epoch++
            connection.also { connection = null } // cleared first, so its listener ignores the disconnect we cause
        }
        current?.disconnect()
        clearNodeStatus()
    }

    @Synchronized
    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    /**
     * [lost] dropped unexpectedly (network, broker restart): report it and start reconnecting.
     * Ignored unless [lost] is the current connection, so a late event from an old client is harmless.
     */
    private fun onConnectionLost(lost: MqttConnection, cause: String?) {
        synchronized(this) {
            if (connection !== lost) return
            connection = null
        }
        clearNodeStatus()
        _connectionState.value = MqttConnectionState.Disconnected(cause ?: "Veza sa serverom je prekinuta")
        if (!paused) startReconnectLoop(immediate = false)
    }

    private fun reconnectNow() = startReconnectLoop(immediate = true)

    @Synchronized
    private fun startReconnectLoop(immediate: Boolean) {
        val scope = scope ?: return
        cancelReconnect()
        reconnectJob = scope.launch {
            var attempt = 0
            while (isActive && !paused && hasSession && connection == null) {
                if (attempt > 0 || !immediate) delay(RECONNECT_DELAYS_MS[minOf(attempt, RECONNECT_DELAYS_MS.lastIndex)])
                if (!isActive || paused || !hasSession) return@launch
                attempt++
                _connectionState.value = MqttConnectionState.Reconnecting(attempt)
                if (connectToAnyEndpoint()) return@launch
            }
        }
    }

    /** Tries the brokers in order; on success the state is [MqttConnectionState.Connected]. */
    private suspend fun connectToAnyEndpoint(): Boolean = attemptMutex.withLock {
        // An attempt that was waiting for the lock may find the previous one already connected.
        if (connection != null) {
            _connectionState.value = MqttConnectionState.Connected
            return@withLock true
        }
        val started = epoch
        withContext(ioDispatcher) { connectOnce(started) }
    }

    private fun connectOnce(started: Long): Boolean {
        for (endpoint in endpoints) {
            if (epoch != started) return false
            val attempt = Attempt()
            val connected = connector.connect(endpoint, MqttTopics.SUBSCRIBE_ALL, attempt)
            if (connected == null) {
                attempt.abandoned = true
                logWarning("Broker ${endpoint.host}:${endpoint.port} not reachable")
                continue
            }
            attempt.connection = connected
            val adopted = synchronized(this) {
                // Paused, signed out or signed in again meanwhile, or dropped before it was adopted.
                val usable = epoch == started && !paused && hasSession && !attempt.lost
                if (usable) connection = connected
                usable
            }
            if (!adopted) {
                attempt.abandoned = true
                connected.disconnect()
                if (epoch != started || paused || !hasSession) return false
                continue // dropped right away: try the next broker
            }
            _connectionState.value = MqttConnectionState.Connected
            return true
        }
        return false
    }

    /**
     * Listener of one connect attempt. Messages count while the attempt is under way (retained
     * messages arrive during the subscribe) and while its client is the current connection;
     * afterwards (timed out, replaced, closed) its client is ignored.
     */
    private inner class Attempt : MqttConnectionListener {
        /** Set when the connector returns the client. */
        @Volatile
        var connection: MqttConnection? = null

        @Volatile
        var abandoned = false

        /** The client reported a disconnect, possibly before it was adopted as the connection. */
        @Volatile
        var lost = false

        private val current: Boolean
            get() {
                if (abandoned) return false
                val mine = connection
                return mine == null || mine === this@SmartHomeRepository.connection
            }

        override fun onMessage(topic: String, payload: String, retained: Boolean) {
            if (current) handleMessage(topic, payload, retained)
        }

        override fun onConnected() = Unit // the state is set when the client is adopted

        override fun onDisconnected(cause: String?) {
            // Flag first: connectOnce checks it while adopting, onConnectionLost covers the rest.
            lost = true
            connection?.let { onConnectionLost(it, cause) }
        }
    }

    /**
     * Publishes on the current connection. False when there is none or the client refused the
     * message; true means it was handed to the broker (an answer, if any, comes separately).
     */
    suspend fun publish(
        topic: String,
        payload: String,
        retain: Boolean = false,
        qos: MqttQualityOfService = MqttQualityOfService.EXACTLY_ONCE,
    ): Boolean {
        val current = connection ?: return false
        return withContext(ioDispatcher) { current.publish(topic, payload, retain, qos) }
    }

    /** Applies one incoming MQTT message to [state]. Internal so tests can feed messages directly. */
    internal fun handleMessage(topic: String, payload: String, retained: Boolean) {
        // The tracker is mutated outside _state.update, whose lambda may run more than once.
        val health = synchronized(nodeStatus) {
            if (nodeStatus.onMessage(topic, payload, retained, clock())) nodeStatus.snapshot(clock()) else null
        }
        _state.update { current ->
            dispatch(topic, payload, if (health != null) current.copy(nodeHealth = health) else current)
        }
        messageListeners.forEach { it(topic, payload, retained) }
    }

    /** Re-evaluates heartbeat timeouts; called periodically, since a silent node sends nothing to react to. */
    fun refreshNodeHealth() {
        val health = synchronized(nodeStatus) { nodeStatus.snapshot(clock()) }
        // Also the moment a silent reading turns stale: nothing arrives to react to.
        _state.update { (if (it.nodeHealth == health) it else it.copy(nodeHealth = health)).withStale(clock()) }
    }

    /** Without a broker connection nothing is known about any node (instead of keeping stale "online"). */
    private fun clearNodeStatus() {
        synchronized(nodeStatus) { nodeStatus.clear() }
        _state.update { it.copy(nodeHealth = emptyMap()).withStale(clock()) }
    }

    private fun dispatch(topic: String, payload: String, state: SmartHomeState): SmartHomeState {
        // An empty payload (value withdrawn by the node) passes through and shows as "--".
        return when (topic) {
            MqttTopics.NODE2_TEMPERATURE -> state.copy(temperatureOutdoor = formatTemperature(payload)).arrived(Reading.OUTDOOR_TEMPERATURE)
            MqttTopics.NODE4_TEMPERATURE -> state.copy(temperatureLivingRoomDown = formatTemperature(payload)).arrived(Reading.GROUND_TEMPERATURE)
            MqttTopics.NODE4_HUMIDITY -> state.copy(humidityLivingRoomDown = payload).arrived(Reading.GROUND_HUMIDITY)
            MqttTopics.NODE6_TEMPERATURE -> state.copy(temperaturePool = formatTemperature(payload)).arrived(Reading.POOL_TEMPERATURE)
            MqttTopics.NODE8_TEMPERATURE -> state.copy(temperatureLivingRoomUp = formatTemperature(payload)).arrived(Reading.UPPER_TEMPERATURE)
            MqttTopics.NODE8_HUMIDITY -> state.copy(humidityLivingRoomUp = payload).arrived(Reading.UPPER_HUMIDITY)

            else -> state
        }.withStale(clock())
    }

    private fun SmartHomeState.arrived(reading: Reading) = copy(readingAt = readingAt + (reading to clock()))

    /** Keeps [SmartHomeState.staleReadings] current; returns the same instance when nothing changed. */
    private fun SmartHomeState.withStale(nowMs: Long): SmartHomeState {
        val stale = computeStale(nowMs)
        return if (stale == staleReadings) this else copy(staleReadings = stale)
    }

    /** Every temperature reading is displayed with exactly 2 decimal places; non-numeric payloads pass through untouched. */
    private fun formatTemperature(raw: String): String {
        val value = raw.toDoubleOrNull() ?: return raw
        return String.format(Locale.US, "%.2f", value)
    }
}

/** Pauses between automatic reconnect attempts; the last one repeats. */
internal val RECONNECT_DELAYS_MS = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)

/** Network-change events closer together than this do not trigger another immediate reconnect. */
internal const val NETWORK_RETRY_MIN_INTERVAL_MS = 5_000L

/** Logcat warning; a no-op in JVM unit tests, where android.util.Log is not available. */
private fun logWarning(message: String) {
    runCatching { android.util.Log.w("SmartHomeRepository", message) }
}
