package zhb.company.smart_home.data.wall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import zhb.company.smart_home.BuildConfig
import zhb.company.smart_home.data.mqtt.MqttConnectionState
import zhb.company.smart_home.data.mqtt.MqttQualityOfService
import zhb.company.smart_home.data.mqtt.MqttWill
import zhb.company.smart_home.data.mqtt.SmartHomeRepository

/** The tablet's battery, from the sticky `ACTION_BATTERY_CHANGED`. */
data class TabletBattery(val percent: Int, val charging: Boolean, val plugged: String?, val temperatureC: Double?)

/**
 * Reports the wall tablet over MQTT like a node, so the logger records it and Node 8 can later
 * drive its charger (`docs/wall_display.md`): retained `tablet1/status` (`online`, and `offline`
 * as the Last Will) and retained `tablet1/state` every [INTERVAL_MS] and whenever the battery level
 * or charging changes. Active only while the wall mode is on.
 */
class TabletReporter(
    private val context: Context,
    private val mqtt: SmartHomeRepository,
    private val scope: CoroutineScope,
) {
    private val _battery = MutableStateFlow<TabletBattery?>(null)
    val battery: StateFlow<TabletBattery?> = _battery.asStateFlow()

    /** Set by the wall screen: true while it is dimmed for the night. */
    val night = MutableStateFlow(false)

    private var job: Job? = null
    private var seq = 0L
    private var receiver: BroadcastReceiver? = null

    @Synchronized
    fun setActive(active: Boolean) {
        if (active == (job != null)) return
        if (!active) {
            job?.cancel()
            job = null
            receiver?.let { runCatching { context.unregisterReceiver(it) } }
            receiver = null
            return
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                _battery.value = parse(intent)
            }
        }
        receiver = r
        // Sticky: returns the current state at once, then calls the receiver on every change.
        context.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { _battery.value = parse(it) }
        job = scope.launch {
            val connected = mqtt.connectionState.map { it is MqttConnectionState.Connected }.distinctUntilChanged()
            // Level and charging changes publish at once; temperature alone waits for the next minute.
            val trigger = combine(connected, battery.map { it?.percent to it?.charging }.distinctUntilChanged(), night) { c, _, _ -> c }
            launch {
                connected.collect { if (it) mqtt.publish(STATUS_TOPIC, ONLINE, retain = true, qos = MqttQualityOfService.AT_LEAST_ONCE) }
            }
            trigger.collectLatest { isConnected ->
                if (!isConnected) return@collectLatest
                while (isActive) {
                    publishState()
                    delay(INTERVAL_MS)
                }
            }
        }
    }

    /** Before the wall mode is turned off on a connected tablet: it is gone, not dropped. */
    suspend fun publishOffline() {
        mqtt.publish(STATUS_TOPIC, OFFLINE, retain = true, qos = MqttQualityOfService.AT_LEAST_ONCE)
    }

    private suspend fun publishState() {
        val b = battery.value
        val payload = buildJsonObject {
            put("seq", ++seq)
            put("up", (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 1000)
            put("battery", b?.percent?.let(::JsonPrimitive) ?: JsonNull)
            put("charging", b?.charging?.let(::JsonPrimitive) ?: JsonNull)
            put("plugged", b?.plugged?.let(::JsonPrimitive) ?: JsonNull)
            put("battery_temp", b?.temperatureC?.let(::JsonPrimitive) ?: JsonNull)
            put("screen", if (night.value) "night" else "day")
            put("app", BuildConfig.VERSION_NAME)
        }.toString()
        mqtt.publish(STATE_TOPIC, payload, retain = true, qos = MqttQualityOfService.AT_LEAST_ONCE)
    }

    private fun parse(intent: Intent): TabletBattery? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> null
        }
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.div(10.0)
        return TabletBattery(
            percent = level * 100 / scale,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            plugged = plugged,
            temperatureC = temp,
        )
    }

    companion object {
        const val STATUS_TOPIC = "bozaSub/kuca/tablet1/status"
        const val STATE_TOPIC = "bozaSub/kuca/tablet1/state"
        const val ONLINE = "online"
        const val OFFLINE = "offline"
        const val INTERVAL_MS = 60_000L
        val WILL = MqttWill(STATUS_TOPIC, OFFLINE)
    }
}
