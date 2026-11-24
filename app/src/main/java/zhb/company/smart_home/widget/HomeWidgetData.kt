package zhb.company.smart_home.widget

import androidx.core.content.edit
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import zhb.company.smart_home.data.remote.RtdbError
import zhb.company.smart_home.data.remote.toRtdbError
import zhb.company.smart_home.data.heating.Node5Repository
import zhb.company.smart_home.data.history.HistoryCharts
import zhb.company.smart_home.data.history.HistoryPeriod
import zhb.company.smart_home.data.history.HistoryRepository
import zhb.company.smart_home.data.history.HistorySeriesNames
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.NodeStatus
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.repository.AuthRepository

/** What the home screen widget shows: three temperatures and the heat pump. */
@Serializable
data class WidgetSnapshot(
    val outdoor: Float? = null,
    val ground: Float? = null,
    val upper: Float? = null,
    val heating: WidgetHeating = WidgetHeating.UNKNOWN,
    /** When these values were true (live MQTT, or the end of the RPi history). */
    val atMs: Long = 0,
)

enum class WidgetHeating { RUNNING, IDLE, OFF, UNREACHABLE, UNKNOWN }

/**
 * Keeps the last [WidgetSnapshot] and redraws the widget when it changes. Two sources write
 * here: live MQTT values while the app runs ([WidgetLiveSync]) and, in the background, the
 * RPi history in Firebase every 30 min ([WidgetRefreshWorker]). A value missing from the new
 * snapshot keeps the previous one, and an older snapshot never replaces a newer one.
 */
class WidgetStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("home_widget", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    fun read(): WidgetSnapshot? = prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString<WidgetSnapshot>(it) }.getOrNull() }

    suspend fun update(next: WidgetSnapshot) {
        val changed = mutex.withLock {
            val prev = read()
            if (prev != null && next.atMs < prev.atMs) return@withLock false
            val merged = WidgetSnapshot(
                outdoor = next.outdoor ?: prev?.outdoor,
                ground = next.ground ?: prev?.ground,
                upper = next.upper ?: prev?.upper,
                heating = if (next.heating == WidgetHeating.UNKNOWN) prev?.heating ?: WidgetHeating.UNKNOWN else next.heating,
                atMs = next.atMs,
            )
            // Live values change every few seconds; redraw only for a visible change or once a minute.
            val visible = prev == null || merged.copy(atMs = 0) != prev.copy(atMs = 0) || merged.atMs - prev.atMs > 60_000
            if (visible) prefs.edit { putString(KEY, json.encodeToString(merged)) }
            visible
        }
        if (changed) runCatching { HomeWidget().updateAll(context) }
    }

    private companion object {
        const val KEY = "snapshot"
    }
}

/** While the app is connected, every live reading goes to the widget (a few seconds later). */
class WidgetLiveSync(
    private val repository: SmartHomeRepository,
    private val node5: Node5Repository,
    private val store: WidgetStore,
    private val scope: CoroutineScope,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            combine(repository.state, repository.connectionState, node5.snapshot) { home, connection, heating ->
                if (connection !is MqttConnectionState.Connected) return@combine null
                val live = heating.live
                val main = heating.config?.mainSwitch ?: live?.mainSwitch
                WidgetSnapshot(
                    outdoor = home.temperatureOutdoor.toFloatOrNull(),
                    ground = home.temperatureLivingRoomDown.toFloatOrNull(),
                    upper = home.temperatureLivingRoomUp.toFloatOrNull(),
                    heating = when {
                        // Retained config of an offline node would say "off" or "idle": not true now.
                        home.nodeStatus(heating.nodeId) == NodeStatus.OFFLINE -> WidgetHeating.UNREACHABLE
                        live == null -> WidgetHeating.UNKNOWN
                        main == false -> WidgetHeating.OFF
                        live.pump -> WidgetHeating.RUNNING
                        else -> WidgetHeating.IDLE
                    },
                )
            }.filterNotNull().distinctUntilChanged().debounce(3_000).collect { store.update(it.copy(atMs = System.currentTimeMillis())) }
        }
    }
}

/** Background refresh from `history/v1/day` (written by the RPi every 10 min). */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val history: HistoryRepository by inject()
    private val auth: AuthRepository by inject()
    private val store: WidgetStore by inject()

    override suspend fun doWork(): Result {
        // Redraw first: even when nothing new can be fetched, old values then show as old.
        runCatching { HomeWidget().updateAll(applicationContext) }
        if (!auth.hasSavedSession()) return Result.success() // signed out
        val day = history.get(HistoryPeriod.DAY, maxAgeMs = 60_000).getOrElse { error ->
            // Only network trouble is worth retrying; a revoked session would fail again and again.
            return if (error.toRtdbError() == RtdbError.HTTP) Result.retry() else Result.success()
        } ?: return Result.success()
        fun latest(name: String) = HistoryCharts.latest(day, name)
        val pump = latest(HistorySeriesNames.PUMP)
        val points = listOfNotNull(
            latest(HistorySeriesNames.OUTDOOR_T), latest(HistorySeriesNames.ROOM_GROUND_T),
            latest(HistorySeriesNames.ROOM_UPPER_T), pump,
        )
        if (points.isEmpty()) return Result.success()
        val step = if (day.times.size > 1) day.times[1] - day.times[0] else 600_000L
        store.update(
            WidgetSnapshot(
                outdoor = latest(HistorySeriesNames.OUTDOOR_T)?.second,
                ground = latest(HistorySeriesNames.ROOM_GROUND_T)?.second,
                upper = latest(HistorySeriesNames.ROOM_UPPER_T)?.second,
                // History has no main switch: "not running" may also mean switched off.
                heating = when {
                    pump == null -> WidgetHeating.UNKNOWN
                    pump.second > 0f -> WidgetHeating.RUNNING
                    else -> WidgetHeating.IDLE
                },
                atMs = points.maxOf { it.first } + step,
            ),
        )
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "home_widget_refresh"
        private const val NOW = "home_widget_refresh_now"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every 30 min while a widget exists; safe to call repeatedly. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).setConstraints(network).build(),
            )
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setConstraints(network).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
