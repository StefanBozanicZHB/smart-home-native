package zhb.company.smart_home.data.wall

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import zhb.company.smart_home.domain.wall.Forecast
import zhb.company.smart_home.domain.wall.OpenMeteo
import zhb.company.smart_home.domain.wall.SunTimes

/**
 * The forecast for the wall display, from Open-Meteo. Refreshed every [REFRESH_MS]; a failed
 * request keeps the last good answer, which is also kept in [cacheFile] so a restart of the
 * tablet does not start with an empty forecast.
 */
class WeatherRepository(
    private val httpClient: HttpClient,
    private val cacheFile: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _forecast = MutableStateFlow<Forecast?>(null)
    val forecast: StateFlow<Forecast?> = _forecast.asStateFlow()

    private val mutex = Mutex()
    private var lastAttemptMs = Long.MIN_VALUE / 2
    private var cacheLoaded = false

    /** Fetches a new forecast when the last one (or the last failed attempt) is old enough. */
    suspend fun refreshIfDue() = mutex.withLock {
        if (!cacheLoaded) {
            cacheLoaded = true
            withContext(Dispatchers.IO) { runCatching { cacheFile.readText() }.getOrNull() }
                ?.let { text -> OpenMeteo.parse(text, cacheFile.lastModified()) }
                ?.let { _forecast.value = it }
        }
        val now = clock()
        val last = _forecast.value?.fetchedMs
        if (last != null && now - last < REFRESH_MS) return@withLock
        if (now - lastAttemptMs < RETRY_MS) return@withLock
        lastAttemptMs = now
        val text = runCatching {
            val response = httpClient.get(OpenMeteo.url(SunTimes.LATITUDE, SunTimes.LONGITUDE))
            if (response.status.isSuccess()) response.bodyAsText() else error("HTTP ${response.status.value}")
        }.onFailure { e ->
            runCatching { android.util.Log.w("WeatherRepository", "Forecast failed: ${e.javaClass.simpleName}: ${e.message}") }
        }.getOrNull() ?: return@withLock
        val parsed = OpenMeteo.parse(text, now) ?: return@withLock
        _forecast.value = parsed
        withContext(Dispatchers.IO) { runCatching { cacheFile.writeText(text) } }
    }

    companion object {
        const val REFRESH_MS = 30 * 60_000L

        /** After a failure, the next try comes this much later. */
        const val RETRY_MS = 5 * 60_000L

        /** A forecast older than this shows its age on the wall. */
        const val OLD_MS = 2 * 60 * 60_000L
    }
}
