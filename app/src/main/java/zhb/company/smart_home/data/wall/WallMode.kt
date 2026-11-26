package zhb.company.smart_home.data.wall

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.local.AppPreferences
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.remote.FirebaseAuthApi
import zhb.company.smart_home.data.repository.AuthRepository

/** Why the wall has no connection to the house, shown on the wall instead of the sign-in form. */
enum class WallProblem {
    /** No saved session on this device: sign in once from the normal screens. */
    NOT_SIGNED_IN,

    /** Firebase could not be reached (sign-in or broker config). */
    NO_NETWORK,

    /** Signed in, but no broker answered. */
    NO_BROKER,
}

/**
 * The wall display mode of this device ([AppPreferences.wallMode]). While it is on:
 * - a broker on the home network is tried first and the connection carries the tablet's Last Will;
 * - the session is resumed from the saved token without asking anything, and kept connected;
 * - [TabletReporter] publishes the tablet's state; [WeatherRepository] keeps the forecast fresh.
 * [isOn] is also read synchronously (crash handler, boot receiver path).
 */
class WallMode(
    private val preferences: AppPreferences,
    private val mqtt: SmartHomeRepository,
    private val auth: AuthRepository,
    private val reporter: TabletReporter,
    private val weather: WeatherRepository,
    private val scope: CoroutineScope,
) {
    /** Null until the preference is read at start. */
    private val _enabled = MutableStateFlow<Boolean?>(null)
    val enabled: StateFlow<Boolean?> = _enabled.asStateFlow()

    @Volatile
    var isOn: Boolean = false
        private set

    private val _problem = MutableStateFlow<WallProblem?>(null)
    val problem: StateFlow<WallProblem?> = _problem.asStateFlow()

    private var job: Job? = null

    fun start() {
        scope.launch {
            preferences.wallMode.distinctUntilChanged().collect { on -> apply(on) }
        }
    }

    suspend fun setEnabled(on: Boolean) {
        if (!on && isOn) reporter.publishOffline()
        preferences.setWallMode(on)
    }

    private fun apply(on: Boolean) {
        val wasOn = isOn
        isOn = on
        _enabled.value = on
        mqtt.preferLanBroker = on
        mqtt.will = if (on) TabletReporter.WILL else null
        reporter.setActive(on)
        job?.cancel()
        job = null
        if (on) job = scope.launch { keepConnected() }
        // An open connection was made without (or with) the will: replace it.
        if (on != wasOn && mqtt.connectionState.value is MqttConnectionState.Connected) mqtt.reconnect()
        if (!on) _problem.value = null
    }

    /** Signs in from the saved session and connects whenever there is no session in this process. */
    private suspend fun kotlinx.coroutines.CoroutineScope.keepConnected() {
        while (isActive) {
            launch { weather.refreshIfDue() }
            when {
                !mqtt.hasSession -> connect()
                mqtt.connectionState.value is MqttConnectionState.Connected -> _problem.value = null
                // The first connect found no broker and does not retry by itself; this starts the
                // reconnect loop (growing pauses) that runs until a broker answers.
                mqtt.connectionState.value is MqttConnectionState.Failed -> mqtt.resume()
            }
            delay(CHECK_MS)
        }
    }

    private suspend fun connect() {
        val token = auth.idTokenResult().getOrElse { error ->
            _problem.value = if (error.message == FirebaseAuthApi.NETWORK_ERROR) WallProblem.NO_NETWORK
            else if (auth.hasSavedSession()) WallProblem.NO_NETWORK else WallProblem.NOT_SIGNED_IN
            return
        }
        mqtt.connect(token)
        _problem.value = when (mqtt.connectionState.value) {
            is MqttConnectionState.Connected -> null
            is MqttConnectionState.Failed -> if (mqtt.hasSession) WallProblem.NO_BROKER else WallProblem.NO_NETWORK
            else -> WallProblem.NO_BROKER
        }
    }

    private companion object {
        const val CHECK_MS = 60_000L
    }
}
