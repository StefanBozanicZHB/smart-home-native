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
import kotlinx.coroutines.withContext
import zhb.company.smart_home.data.remote.MqttConfigSource
import zhb.company.smart_home.domain.SprinklerConfigCodec
import zhb.company.smart_home.domain.computeListPowerDevice
import zhb.company.smart_home.domain.model.AcFanSpeed
import zhb.company.smart_home.domain.model.AcMode
import zhb.company.smart_home.domain.model.SmartHomeState

/**
 * Single source of truth for the live house state, connecting to the MQTT
 * broker and dispatching every incoming message into [state]. Mirrors the
 * Flutter app's `Mqtt` provider + `Parameters` ChangeNotifier combined.
 *
 * Connection lifecycle:
 * - [connect] after sign-in fetches the broker config (primary + backup) and connects once;
 * - if the connection later drops, it reconnects by itself with growing pauses
 *   ([RECONNECT_DELAYS_MS]), trying the primary then the backup broker each time;
 * - [pause] (app in the background) disconnects on purpose and stops reconnecting; [resume]
 *   reconnects at once; [onNetworkAvailable] skips the current pause when the network returns;
 * - [disconnect] (sign-out) forgets the session.
 * Every new connection resubscribes, so retained messages refresh the whole state.
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

    @Volatile
    private var attemptInProgress = false
    private var lastNetworkRetryMs = Long.MIN_VALUE / 2

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
        _connectionState.value = MqttConnectionState.Connecting
        clearNodeStatus()
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

    /** The device got a working network: do not wait for the current reconnect pause. */
    fun onNetworkAvailable() {
        if (!hasSession || paused || connection != null || attemptInProgress) return
        // The system reports network changes often (e.g. signal strength); retry at most every 5 s.
        val now = clock()
        if (now - lastNetworkRetryMs < NETWORK_RETRY_MIN_INTERVAL_MS) return
        lastNetworkRetryMs = now
        reconnectNow()
    }

    private fun closeConnection() {
        val current = connection
        connection = null // set first, so the listener ignores the disconnect event we cause
        current?.disconnect()
        clearNodeStatus()
    }

    @Synchronized
    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    /** Connection lost unexpectedly (network, broker restart): report it and start reconnecting. */
    private fun onConnectionLost(cause: String?) {
        connection = null
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
    private suspend fun connectToAnyEndpoint(): Boolean = withContext(ioDispatcher) {
        attemptInProgress = true
        try {
            connectOnce()
        } finally {
            attemptInProgress = false
        }
    }

    private fun connectOnce(): Boolean {
        for (endpoint in endpoints) {
            val connected = tryConnect(endpoint) ?: run {
                logWarning("Broker ${endpoint.host}:${endpoint.port} not reachable")
                null
            } ?: continue
            if (paused) {
                connected.disconnect()
                return false
            }
            connection = connected
            _connectionState.value = MqttConnectionState.Connected
            // Legacy nodes publish their state on request (nodes with the new firmware use retained topics).
            connected.publish(MqttTopics.NODE1_REQUEST_STATUS, "s", retain = false, qos = MqttQualityOfService.EXACTLY_ONCE)
            return true
        }
        return false
    }

    private fun tryConnect(endpoint: MqttEndpoint): MqttConnection? {
        // Set once connect() returns. The callbacks compare it with the active connection, so a
        // stale (e.g. failed primary or replaced) attempt cannot overwrite a newer connection's
        // status with its late connected/disconnected events.
        var attempt: MqttConnection? = null
        val listener = object : MqttConnectionListener {
            override fun onMessage(topic: String, payload: String, retained: Boolean) =
                handleMessage(topic, payload, retained)

            override fun onConnected() {
                if (attempt != null && connection === attempt) {
                    _connectionState.value = MqttConnectionState.Connected
                }
            }

            override fun onDisconnected(cause: String?) {
                if (attempt != null && connection === attempt) onConnectionLost(cause)
            }
        }
        return connector.connect(endpoint, MqttTopics.SUBSCRIBE_ALL, listener).also { attempt = it }
    }

    suspend fun publish(
        topic: String,
        payload: String,
        retain: Boolean = false,
        qos: MqttQualityOfService = MqttQualityOfService.EXACTLY_ONCE,
    ) {
        val current = connection ?: return
        withContext(ioDispatcher) { current.publish(topic, payload, retain, qos) }
    }

    /** AC power on/off has no dedicated status topic on the wire - tracked client-side only. */
    fun setAcPower(on: Boolean) {
        _state.update { it.copy(acOn = on).let { s -> s.copy(listPowerDevice = computeListPowerDevice(s)) } }
    }

    /** Applies one incoming MQTT message to [state]. Internal so tests can feed messages directly. */
    internal fun handleMessage(topic: String, payload: String, retained: Boolean) {
        val nodeMatch = MqttTopics.NODE_NUMBER_REGEX.find(topic)
        // The tracker is mutated outside _state.update, whose lambda may run more than once.
        val health = synchronized(nodeStatus) {
            if (nodeStatus.onMessage(topic, payload, retained, clock())) nodeStatus.snapshot(clock()) else null
        }
        _state.update { current ->
            var next = current
            if (health != null) next = next.copy(nodeHealth = health)
            if (!retained && nodeMatch != null) {
                val node = nodeMatch.groupValues[1].toIntOrNull()
                if (node != null) {
                    next = next.copy(nodeOnline = next.nodeOnline + (node to true))
                }
            }
            next = dispatch(topic, payload, next)
            next.copy(listPowerDevice = computeListPowerDevice(next))
        }
        messageListeners.forEach { it(topic, payload, retained) }
    }

    /** Re-evaluates heartbeat timeouts; called periodically, since a silent node sends nothing to react to. */
    fun refreshNodeHealth() {
        val health = synchronized(nodeStatus) { nodeStatus.snapshot(clock()) }
        _state.update { if (it.nodeHealth == health) it else it.copy(nodeHealth = health) }
    }

    /** Without a broker connection nothing is known about any node (instead of keeping stale "online"). */
    private fun clearNodeStatus() {
        synchronized(nodeStatus) { nodeStatus.clear() }
        _state.update { it.copy(nodeHealth = emptyMap(), nodeOnline = emptyMap()) }
    }

    private fun dispatch(topic: String, payload: String, state: SmartHomeState): SmartHomeState {
        val onOff = payload == "1"
        return when {
            // node0/sprinkler/status ('auto' field) is the single source of truth for
            // sprinklerTimerAuto - it's also mirrored on node0/timer and node1/stanje/stanje
            // (bit 8), but those two can carry stale retained values that race with this one on
            // connect (arrival order across topics isn't guaranteed), which showed up as the
            // auto-timer toggle randomly flipping between app runs with no real change on the
            // device side. node1/stanje/stanje's other bits (sprinklers/fountain) are unaffected
            // and still read from parseNode1Bitmask below.
            topic == MqttTopics.NODE0_SPRINKLER_CONFIG_STATUS -> {
                val dto = SprinklerConfigCodec.decode(payload) ?: return state
                state.copy(
                    sprinklerStartTime = dto.start,
                    sprinklerDurationOnMinutes = dto.on / 60,
                    sprinklerDurationOffSeconds = dto.off,
                    sprinklerSets = dto.sets,
                    sprinklerTimerAuto = dto.auto == 1,
                )
            }
            topic == MqttTopics.NODE1_ALL_STATUS -> parseNode1Bitmask(payload, state)
            topic == MqttTopics.NODE1_ALL_SPRINKLERS_STATUS -> state.copy(sprinklerAll = onOff)
            topic.matches(Regex("""bozaSub/kuca/node1/prskalica(\d)/stanje""")) -> {
                val index = Regex("""prskalica(\d)""").find(topic)?.groupValues?.get(1)?.toIntOrNull() ?: return state
                updateSprinkler(state, index, onOff)
            }
            topic == MqttTopics.NODE1_FOUNTAIN_PUMP_STATUS -> state.copy(fountainPump = onOff)
            topic == MqttTopics.NODE1_FOUNTAIN_LIGHT_STATUS -> state.copy(fountainLight = onOff)

            topic == MqttTopics.NODE2_TEMPERATURE -> state.copy(temperatureOutdoor = formatTemperature(payload))
            topic == MqttTopics.NODE2_HUMIDITY -> state.copy(humidityOutdoor = payload)
            topic == MqttTopics.NODE2_LED_STRIP_STATUS -> state.copy(ledStripTerrace = onOff)
            topic == MqttTopics.NODE2_BULB_STATUS -> state.copy(bulbTerrace = onOff)

            topic == MqttTopics.NODE3_DOOR_STATUS -> state.copy(mainDoor = onOff)

            topic == MqttTopics.NODE4_TEMPERATURE -> state.copy(temperatureLivingRoomDown = formatTemperature(payload))
            topic == MqttTopics.NODE4_HUMIDITY -> state.copy(humidityLivingRoomDown = payload)


            topic == MqttTopics.NODE6_POOL_PUMP_STATUS -> state.copy(poolPump = onOff)
            topic == MqttTopics.NODE6_POOL_HEATING_STATUS -> state.copy(poolHeating = onOff)
            topic == MqttTopics.NODE6_POOL_TEMPERATURE -> state.copy(temperaturePool = formatTemperature(payload))

            topic == MqttTopics.NODE7_FINGER_ALL_STATUS ||
                topic == MqttTopics.NODE7_FINGER_ADD_STATUS ||
                topic == MqttTopics.NODE7_FINGER_DEL_STATUS ->
                state.copy(fingerSensorStatus = if (state.fingerSensorStatus.isBlank()) payload else "$payload\n${state.fingerSensorStatus}")
            topic == MqttTopics.node7BulbStatus(1) -> state.copy(bulbSmallHome1 = onOff)
            topic == MqttTopics.node7BulbStatus(2) -> state.copy(bulbSmallHome2 = onOff)
            topic == MqttTopics.node7BulbStatus(3) -> state.copy(bulbSmallHome3 = onOff)
            topic == MqttTopics.NODE7_DOOR_STATUS -> state.copy(doorSmallHome = onOff)

            topic == MqttTopics.NODE8_TEMPERATURE -> {
                val calibrated = payload.toDoubleOrNull()?.plus(MqttTopics.NODE8_TEMP_CALIBRATION_OFFSET)
                state.copy(temperatureLivingRoomUp = formatTemperature(calibrated?.toString() ?: payload))
            }
            topic == MqttTopics.NODE8_HUMIDITY -> state.copy(humidityLivingRoomUp = payload)
            topic == MqttTopics.NODE8_GAS -> state.copy(gasLivingRoomUp = payload)
            topic == MqttTopics.NODE8_AC_MODE -> state.copy(acMode = AcMode.fromCode(payload))
            topic == MqttTopics.NODE8_AC_FAN -> state.copy(acFanSpeed = AcFanSpeed.fromCode(payload))
            topic == MqttTopics.NODE8_AC_TEMP -> state.copy(acTemp = payload.toIntOrNull() ?: state.acTemp)

            else -> state
        }
    }

    /** Every temperature reading is displayed with exactly 2 decimal places; non-numeric payloads pass through untouched. */
    private fun formatTemperature(raw: String): String {
        val value = raw.toDoubleOrNull() ?: return raw
        return String.format(Locale.US, "%.2f", value)
    }

    private fun updateSprinkler(state: SmartHomeState, index: Int, on: Boolean): SmartHomeState {
        val updated = when (index) {
            1 -> state.copy(sprinkler1 = on)
            2 -> state.copy(sprinkler2 = on)
            3 -> state.copy(sprinkler3 = on)
            4 -> state.copy(sprinkler4 = on)
            5 -> state.copy(sprinkler5 = on)
            6 -> state.copy(sprinkler6 = on)
            else -> state
        }
        val activeIndex = listOf(
            1 to updated.sprinkler1, 2 to updated.sprinkler2, 3 to updated.sprinkler3,
            4 to updated.sprinkler4, 5 to updated.sprinkler5, 6 to updated.sprinkler6,
        ).firstOrNull { it.second }?.first ?: -1
        return updated.copy(sprinklerPower = activeIndex)
    }

    private fun parseNode1Bitmask(payload: String, state: SmartHomeState): SmartHomeState {
        if (payload.length < 9) return state
        fun bit(i: Int) = payload[i] == '1'
        // bit(8) (auto-timer) is intentionally not read here - node0/sprinkler/status is the
        // single source of truth for sprinklerTimerAuto, see dispatch()'s comment above.
        val withSprinklers = state.copy(
            sprinkler1 = bit(0), sprinkler2 = bit(1), sprinkler3 = bit(2),
            sprinkler4 = bit(3), sprinkler5 = bit(4), sprinkler6 = bit(5),
            fountainPump = bit(6), fountainLight = bit(7),
        )
        val activeIndex = listOf(
            1 to withSprinklers.sprinkler1, 2 to withSprinklers.sprinkler2, 3 to withSprinklers.sprinkler3,
            4 to withSprinklers.sprinkler4, 5 to withSprinklers.sprinkler5, 6 to withSprinklers.sprinkler6,
        ).firstOrNull { it.second }?.first ?: -1
        return withSprinklers.copy(sprinklerPower = activeIndex)
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
