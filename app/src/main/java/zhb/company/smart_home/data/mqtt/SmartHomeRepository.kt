package zhb.company.smart_home.data.mqtt

import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import zhb.company.smart_home.data.remote.FirebaseRtdbApi
import zhb.company.smart_home.domain.HeatingScheduleCodec
import zhb.company.smart_home.domain.SprinklerConfigCodec
import zhb.company.smart_home.domain.computeListPowerDevice
import zhb.company.smart_home.domain.model.AcFanSpeed
import zhb.company.smart_home.domain.model.AcMode
import zhb.company.smart_home.domain.model.SmartHomeState

/**
 * Single source of truth for the live house state, connecting to the MQTT
 * broker and dispatching every incoming message into [state]. Mirrors the
 * Flutter app's `Mqtt` provider + `Parameters` ChangeNotifier combined.
 */
class SmartHomeRepository(
    private val firebaseRtdbApi: FirebaseRtdbApi,
) {
    private val _state = MutableStateFlow(SmartHomeState())
    val state: StateFlow<SmartHomeState> = _state.asStateFlow()

    private val _connectionState = MutableStateFlow<MqttConnectionState>(MqttConnectionState.Idle)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    private var client: Mqtt3AsyncClient? = null

    suspend fun connect(idToken: String) {
        _connectionState.value = MqttConnectionState.Connecting
        val configResult = firebaseRtdbApi.fetchMqttClientConfig(idToken)
        val config = configResult.getOrElse {
            _connectionState.value = MqttConnectionState.Failed(it.message ?: "Konfiguracija nije pronađena")
            return
        }
        withContext(Dispatchers.IO) {
            val connected = tryConnect(config.mqttServer, config.mqttPort, config.mqttUsername, config.mqttPassword)
                ?: tryConnect(config.backupMqttServer, config.backupMqttPort, config.backupMqttUsername, config.backupMqttPassword)

            if (connected == null) {
                _connectionState.value = MqttConnectionState.Failed("Konektivnost sa serverom ne postoji!")
                return@withContext
            }
            client = connected
            _connectionState.value = MqttConnectionState.Connected
            publishBlocking(connected, MqttTopics.NODE1_REQUEST_STATUS, "s")
        }
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        _connectionState.value = MqttConnectionState.Idle
    }

    private fun tryConnect(host: String, port: String, username: String, password: String): Mqtt3AsyncClient? {
        if (host.isBlank() || port.isBlank()) return null
        val portInt = port.trim().toIntOrNull() ?: return null
        val tls = port.trim() == "8883"
        return try {
            // Captured by the listeners below to tell "this attempt's client is still the active
            // one" apart from a stale/backup client whose late callback would otherwise stomp on
            // a newer connection's status.
            var attemptClient: Mqtt3AsyncClient? = null
            val builder = Mqtt3Client.builder()
                .identifier("native_client_${System.currentTimeMillis()}")
                .serverHost(host.trim())
                .serverPort(portInt)
                .addConnectedListener {
                    if (attemptClient != null && client === attemptClient) {
                        _connectionState.value = MqttConnectionState.Connected
                    }
                }
                .addDisconnectedListener { context ->
                    if (attemptClient != null && client === attemptClient) {
                        client = null
                        _connectionState.value = MqttConnectionState.Disconnected(
                            context.cause?.message ?: "Veza sa serverom je prekinuta"
                        )
                    }
                }
            if (tls) builder.sslWithDefaultConfig()
            if (username.isNotBlank()) {
                builder.simpleAuth(
                    Mqtt3SimpleAuth.builder()
                        .username(username)
                        .password(password.toByteArray(Charsets.UTF_8))
                        .build()
                )
            }
            val asyncClient = builder.buildAsync()
            attemptClient = asyncClient
            asyncClient.connectWith()
                .cleanSession(true)
                .keepAlive(20)
                .send()
                .get(5, TimeUnit.SECONDS)

            asyncClient.subscribeWith()
                .topicFilter(MqttTopics.SUBSCRIBE_ALL)
                .qos(MqttQos.AT_MOST_ONCE)
                .callback { publish -> onMessage(publish) }
                .send()
                .get(5, TimeUnit.SECONDS)

            asyncClient
        } catch (e: Exception) {
            null
        }
    }

    suspend fun publish(
        topic: String,
        payload: String,
        retain: Boolean = false,
        qos: MqttQos = MqttQos.EXACTLY_ONCE,
    ) {
        val currentClient = client ?: return
        withContext(Dispatchers.IO) { publishBlocking(currentClient, topic, payload, retain, qos) }
    }

    private fun publishBlocking(
        target: Mqtt3AsyncClient,
        topic: String,
        payload: String,
        retain: Boolean = false,
        qos: MqttQos = MqttQos.EXACTLY_ONCE,
    ) {
        try {
            target.publishWith()
                .topic(topic)
                .qos(qos)
                .payload(payload.toByteArray(Charsets.ISO_8859_1))
                .retain(retain)
                .send()
        } catch (_: Exception) {
            // best-effort, matches original app which does not surface publish failures to the UI
        }
    }

    /** AC power on/off has no dedicated status topic on the wire - tracked client-side only. */
    fun setAcPower(on: Boolean) {
        _state.update { it.copy(acOn = on).let { s -> s.copy(listPowerDevice = computeListPowerDevice(s)) } }
    }

    private fun onMessage(publish: Mqtt3Publish) {
        val topic = publish.topic.toString()
        val payload = String(publish.payloadAsBytes, Charsets.ISO_8859_1)
        val nodeMatch = MqttTopics.NODE_NUMBER_REGEX.find(topic)
        _state.update { current ->
            var next = current
            if (!publish.isRetain && nodeMatch != null) {
                val node = nodeMatch.groupValues[1].toIntOrNull()
                if (node != null) {
                    next = next.copy(nodeOnline = next.nodeOnline + (node to true))
                }
            }
            next = dispatch(topic, payload, next)
            next.copy(listPowerDevice = computeListPowerDevice(next))
        }
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
            topic == MqttTopics.node4BulbStatus(1) -> state.copy(bulbLivingRoomDown1 = onOff)
            topic == MqttTopics.node4BulbStatus(2) -> state.copy(bulbLivingRoomDown2 = onOff)
            topic == MqttTopics.node4BulbStatus(3) -> state.copy(bulbLivingRoomDown3 = onOff)

            topic == MqttTopics.NODE5_TUBE_TEMP_STATUS -> state.copy(temperatureTubeHeating = formatTemperature(payload))
            topic == MqttTopics.NODE5_SCHEDULE_WORKDAY_STATUS -> {
                val segments = HeatingScheduleCodec.decode(payload)
                state.copy(workdaySegments = segments, numberOfSegmentsWorking = segments.size)
            }
            topic == MqttTopics.NODE5_SCHEDULE_WEEKEND_STATUS -> {
                val segments = HeatingScheduleCodec.decode(payload)
                state.copy(weekendSegments = segments, numberOfSegmentsWeekend = segments.size)
            }
            topic == MqttTopics.NODE5_HEATING_TYPE_STATUS -> when (payload) {
                "1" -> state.copy(isPellets = true, isGas = false, isElectro = false)
                "2" -> state.copy(isPellets = false, isGas = true, isElectro = false)
                else -> state.copy(isPellets = false, isGas = false, isElectro = true)
            }
            topic == MqttTopics.NODE5_MAIN_SWITCH_STATUS -> state.copy(isMainSwitch = onOff)
            topic == MqttTopics.NODE5_UPPER_LIMIT_STATUS -> state.copy(upStep = (payload.toDoubleOrNull() ?: 0.0) / 10.0)
            topic == MqttTopics.NODE5_LOWER_LIMIT_STATUS -> state.copy(downStep = (payload.toDoubleOrNull() ?: 0.0) / 10.0)
            topic == MqttTopics.NODE5_HEATING_RELAY_STATUS -> state.copy(isTempRelay = onOff)
            topic == MqttTopics.NODE5_PELLET_LEVEL_STATUS -> state.copy(percentPelletsTank = payload.toDoubleOrNull() ?: 0.0)
            topic == MqttTopics.NODE5_ALARM_LEVEL_STATUS -> state.copy(percentAlarmLimit = payload.toIntOrNull() ?: 0)

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
