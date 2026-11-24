package zhb.company.smart_home.data.mqtt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ties the MQTT connection to app visibility and network changes:
 * - in the background for longer than [pauseAfterMs] → [SmartHomeRepository.pause] (no point
 *   keeping a socket open while nobody looks, and Doze would cut it anyway);
 * - back in the foreground → [SmartHomeRepository.resume] (reconnect at once, retained topics
 *   bring the state up to date);
 * - network available again → [SmartHomeRepository.onNetworkAvailable].
 * Short app switches (under [pauseAfterMs]) keep the connection.
 */
class ConnectionLifecycle(
    private val repository: SmartHomeRepository,
    private val scope: CoroutineScope,
    private val pauseAfterMs: Long = 60_000,
) {
    private var pauseJob: Job? = null

    @Synchronized
    fun onForeground() {
        pauseJob?.cancel()
        pauseJob = null
        repository.resume()
    }

    @Synchronized
    fun onBackground() {
        pauseJob?.cancel()
        pauseJob = scope.launch {
            delay(pauseAfterMs)
            repository.pause()
        }
    }

    /** [network] identifies the default network ([android.net.Network.getNetworkHandle]). */
    fun onNetworkAvailable(network: Long) = repository.onNetworkAvailable(network)
}
