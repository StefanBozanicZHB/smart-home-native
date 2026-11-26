package zhb.company.smart_home.ui.wall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.door.Node3Repository
import zhb.company.smart_home.data.garden.Node1Repository
import zhb.company.smart_home.data.garden.Node2Repository
import zhb.company.smart_home.data.heating.Node5Repository
import zhb.company.smart_home.data.history.HistoryDoc
import zhb.company.smart_home.data.history.HistoryPeriod
import zhb.company.smart_home.data.history.HistoryRepository
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.NodeStatus
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.wall.DoorLog
import zhb.company.smart_home.data.wall.DoorLogEntry
import zhb.company.smart_home.data.wall.TabletBattery
import zhb.company.smart_home.data.wall.TabletReporter
import zhb.company.smart_home.data.wall.WallMode
import zhb.company.smart_home.data.wall.WallProblem
import zhb.company.smart_home.data.wall.WeatherRepository
import zhb.company.smart_home.domain.heating.HeatPumpStatus
import zhb.company.smart_home.domain.model.Reading
import zhb.company.smart_home.domain.model.SmartHomeState
import zhb.company.smart_home.domain.wall.Forecast
import zhb.company.smart_home.domain.wall.HeatingDay
import zhb.company.smart_home.domain.wall.HeatingToday
import zhb.company.smart_home.ui.format.Readings
import zhb.company.smart_home.ui.system.NodeLabels

/** One room tile: temperature and humidity as shown, and since when it is stale (null = current). */
data class WallRoom(val name: Int, val temperature: String, val humidity: String, val staleSinceMs: Long?, val here: Boolean = false)

data class WallOutdoor(
    /** Measured by Node 2, "--" when missing. */
    val measured: String,
    val staleSinceMs: Long?,
    val todayMin: Double?,
    val todayMax: Double?,
)

data class WallHeating(
    val status: HeatPumpStatus,
    val flowTemp: String,
    val today: HeatingToday,
    val outOfSeason: Boolean,
    /** End of the last run within the last 24 h, else null. */
    val lastRunEndMs: Long?,
    /** The last day with a run in the month history. */
    val lastRunDay: LocalDate?,
)

data class WallDoor(val known: Boolean, val lockOpen: Boolean, val readerOk: Boolean, val events: List<DoorLogEntry>)

data class WallYard(
    val neonOn: Boolean,
    val neonAt: String?,
    val lampOn: Boolean,
    val lampAt: String?,
    val fountainOn: Boolean?,
    val fountainAt: String?,
    val poolTemperature: String,
    val poolStaleSinceMs: Long?,
)

data class WallFooter(val nodesOnline: Int, val nodesTotal: Int, val connected: Boolean, val battery: TabletBattery?)

data class WallState(
    val nowMs: Long = System.currentTimeMillis(),
    val outdoor: WallOutdoor = WallOutdoor("--", null, null, null),
    val forecast: Forecast? = null,
    val rooms: List<WallRoom> = emptyList(),
    val heating: WallHeating? = null,
    val door: WallDoor = WallDoor(false, false, true, emptyList()),
    val yard: WallYard? = null,
    val footer: WallFooter = WallFooter(0, NodeLabels.ACTIVE.size, false, null),
    val problem: WallProblem? = null,
)

/** Everything on the wall screen, refreshed by the house state and once a minute. */
class WallViewModel(
    mqtt: SmartHomeRepository,
    node1: Node1Repository,
    node2: Node2Repository,
    node3: Node3Repository,
    node5: Node5Repository,
    doorLog: DoorLog,
    weather: WeatherRepository,
    reporter: TabletReporter,
    wallMode: WallMode,
    private val history: HistoryRepository,
) : ViewModel() {

    private val day = MutableStateFlow<HistoryDoc?>(null)
    private val month = MutableStateFlow<HistoryDoc?>(null)
    private val monthLoaded = MutableStateFlow(false)

    init {
        // The RPi writes the day history every 10 min; the month one hourly.
        viewModelScope.launch {
            while (isActive) {
                history.get(HistoryPeriod.DAY).onSuccess { day.value = it }
                history.get(HistoryPeriod.MONTH, maxAgeMs = 60 * 60_000L).onSuccess { month.value = it; monthLoaded.value = true }
                delay(HistoryRepository.MAX_AGE_MS)
            }
        }
    }

    private val minute: Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(60_000 - now % 60_000)
        }
    }

    private val house = combine(mqtt.state, mqtt.connectionState, node5.snapshot, minute) { s, c, h, now -> Quad(s, c, h, now) }
    private val yardDoor = combine(node1.snapshot, node2.state, node3.snapshot, doorLog.entries) { n1, n2, n3, log -> Quad(n1, n2, n3, log) }
    private val extras = combine(weather.forecast, reporter.battery, wallMode.problem, day, combine(month, monthLoaded) { m, l -> m to l }) { f, b, p, d, m ->
        Extras(f, b, p, d, m.first, m.second)
    }

    val state: StateFlow<WallState> = combine(house, yardDoor, extras) { (home, conn, heat, now), (n1, n2, n3, log), x ->
        val connected = conn is MqttConnectionState.Connected
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val nowTime = java.time.LocalTime.now(zone).let { "%02d:%02d".format(it.hour, it.minute) }

        val status = HeatPumpStatus.from(connected, heat.config, heat.live, heat.pump, heat.safeMode)
        val runningSince = (status as? HeatPumpStatus.Running)?.minutes?.let { now - it * 60_000 }
        val lastRunDay = HeatingDay.lastRunDay(x.month, zone)
        val range = HeatingDay.outdoorRange(x.day, dayStart)
        val forecastToday = x.forecast?.daysFrom(today, 1)?.firstOrNull()

        WallState(
            nowMs = now,
            outdoor = WallOutdoor(
                measured = Readings.temperature(home.temperatureOutdoor),
                staleSinceMs = home.staleSince(Reading.OUTDOOR_TEMPERATURE),
                todayMin = range?.first ?: forecastToday?.min,
                todayMax = range?.second ?: forecastToday?.max,
            ),
            forecast = x.forecast,
            rooms = rooms(home),
            heating = WallHeating(
                status = status,
                flowTemp = Readings.temperature(heat.live?.pipes?.supply),
                today = HeatingDay.today(x.day, dayStart, now, runningSince),
                outOfSeason = HeatingDay.outOfSeason(
                    mainSwitchOn = heat.config?.mainSwitch ?: heat.live?.mainSwitch,
                    running = heat.live?.pump == true,
                    lastRunDay = lastRunDay,
                    monthKnown = x.monthLoaded,
                    today = today,
                ),
                lastRunEndMs = HeatingDay.lastRunEnd(x.day),
                lastRunDay = lastRunDay,
            ),
            door = WallDoor(
                known = n3.state != null,
                lockOpen = n3.state?.lockOpen == true,
                readerOk = n3.state?.readerOk != false,
                events = log,
            ),
            yard = WallYard(
                neonOn = n2?.neon == true,
                neonAt = n2?.today?.get("neon")?.on?.takeIf { it > nowTime },
                lampOn = n2?.lamp == true,
                lampAt = n2?.today?.get("lamp")?.on?.takeIf { it > nowTime },
                fountainOn = n1.state?.fountain?.pump,
                fountainAt = n1.state?.fountain?.today?.get("pump")?.on?.takeIf { it > nowTime },
                poolTemperature = Readings.temperature(home.temperaturePool),
                poolStaleSinceMs = home.staleSince(Reading.POOL_TEMPERATURE),
            ),
            footer = WallFooter(
                nodesOnline = NodeLabels.ACTIVE.count { home.nodeStatus(it) == NodeStatus.ONLINE },
                nodesTotal = NodeLabels.ACTIVE.size,
                connected = connected,
                battery = x.battery,
            ),
            problem = x.problem,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WallState())

    private fun rooms(home: SmartHomeState): List<WallRoom> = buildList {
        add(WallRoom(R_UPPER, Readings.temperature(home.temperatureLivingRoomUp), Readings.percent(home.humidityLivingRoomUp), home.staleSince(Reading.UPPER_TEMPERATURE), here = true))
        add(WallRoom(R_GROUND, Readings.temperature(home.temperatureLivingRoomDown), Readings.percent(home.humidityLivingRoomDown), home.staleSince(Reading.GROUND_TEMPERATURE)))
        // Shown once Node 11 has reported (it is not installed yet).
        if (Reading.BEDROOM_TEMPERATURE in home.readingAt) {
            add(WallRoom(R_BEDROOM, Readings.temperature(home.temperatureBedroomDown), Readings.percent(home.humidityBedroomDown), home.staleSince(Reading.BEDROOM_TEMPERATURE)))
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private data class Extras(
        val forecast: Forecast?,
        val battery: TabletBattery?,
        val problem: WallProblem?,
        val day: HistoryDoc?,
        val month: HistoryDoc?,
        val monthLoaded: Boolean,
    )

    private companion object {
        val R_UPPER = zhb.company.smart_home.R.string.wall_room_upper
        val R_GROUND = zhb.company.smart_home.R.string.wall_room_ground
        val R_BEDROOM = zhb.company.smart_home.R.string.wall_room_bedroom
    }
}
