package zhb.company.smart_home.data.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import zhb.company.smart_home.data.remote.RtdbReader

/**
 * Chart history written by the RPi (`das_history.py` in the DAS repository) to Firebase:
 * `history/v1/{day,week,month,meta}`. Each document has one time axis and, per series, values as
 * comma-separated text ("21.5,21.4,,21.3"; empty = no data), because Firebase turns arrays with
 * gaps into objects.
 */
enum class HistoryPeriod(val key: String) { DAY("day"), WEEK("week"), MONTH("month") }

/** Series names in the RPi catalog (`[history_series]`); unknown ones are kept as well. */
object HistorySeriesNames {
    const val ROOM_GROUND_T = "room_ground_t"
    const val ROOM_GROUND_H = "room_ground_h"
    const val ROOM_UPPER_T = "room_upper_t"
    const val ROOM_UPPER_H = "room_upper_h"
    // Node 11, bedroom on the ground floor: only measured, not a heating zone.
    const val BEDROOM_GROUND_T = "bedroom_ground_t"
    const val BEDROOM_GROUND_H = "bedroom_ground_h"
    const val OUTDOOR_T = "outdoor_t"
    const val OUTDOOR_H = "outdoor_h"
    const val PUMP = "pump"
    const val LOGGED = "logged"
    // Pipe temperatures, sampled only while the pump runs (RPi catalog `..., pump`).
    const val PIPE_SUPPLY = "pipe_supply"
    const val PIPE_RETURN = "pipe_return"
    const val PIPE_WELL_IN = "pipe_well_in"
    const val PIPE_WELL_OUT = "pipe_well_out"
    fun target(zone: String) = "target_$zone"
    /** Room temperature of a heating zone: `room_ground_t`, `room_upper_t`. */
    fun room(zone: String) = "room_${zone}_t"
    fun online(node: String) = "online_$node"
}

/**
 * One series. [avg]: mean per point (for `bool` the share of time on, 0..1; for `seen` the share
 * of logged time the node was heard). [min]/[max] for measurements and [onHours] for `bool`, in
 * the week and month documents only.
 */
data class HistorySeries(
    val unit: String,
    val label: String,
    val avg: List<Float?>,
    val min: List<Float?>? = null,
    val max: List<Float?>? = null,
    val onHours: List<Float?>? = null,
) {
    val hasData: Boolean get() = avg.any { it != null }
}

/** [times]: start of each point (ms); the last point ends at [endMs]. */
data class HistoryDoc(
    val period: HistoryPeriod,
    val times: List<Long>,
    val endMs: Long,
    val series: Map<String, HistorySeries>,
    val updatedMs: Long?,
) {
    operator fun get(name: String): HistorySeries? = series[name]
}

data class HistoryMeta(val updatedMs: Long?, val loggerLastMs: Long?, val dbMb: Long?, val freeMb: Long?)

object HistoryParser {

    fun csv(text: String?): List<Float?> =
        if (text.isNullOrEmpty()) emptyList() else text.split(',').map { it.toFloatOrNull() }

    fun parse(period: HistoryPeriod, obj: JsonObject, zone: ZoneId = ZoneId.systemDefault()): HistoryDoc? {
        val count = obj.long("count")?.toInt() ?: return null
        if (count <= 0) return null
        val times: List<Long>
        val endMs: Long
        if (period == HistoryPeriod.MONTH) {
            val first = obj.string("first_day")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            // Local days: 23 or 25 hours on clock change days.
            val starts = (0..count).map { first.plusDays(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() }
            times = starts.dropLast(1)
            endMs = starts.last()
        } else {
            val start = obj.long("start_ms") ?: return null
            val step = obj.long("step_ms") ?: return null
            times = (0 until count).map { start + it * step }
            endMs = start + count * step
        }
        val series = (obj["series"] as? JsonObject).orEmpty().mapNotNull { (name, value) ->
            val s = value as? JsonObject ?: return@mapNotNull null
            fun list(key: String) = s.string(key)?.let { fit(csv(it), count) }
            name to HistorySeries(
                unit = s.string("unit") ?: "",
                label = s.string("label") ?: name,
                avg = list("avg") ?: return@mapNotNull null,
                min = list("min"),
                max = list("max"),
                onHours = list("on_h"),
            )
        }.toMap()
        return HistoryDoc(period, times, endMs, series, obj.long("updated_ms"))
    }

    fun parseMeta(obj: JsonObject) = HistoryMeta(
        updatedMs = obj.long("updated_ms"),
        loggerLastMs = obj.long("logger_last_ms"),
        dbMb = obj.long("db_mb"),
        freeMb = obj.long("free_mb"),
    )

    /** Pads or trims to the axis length, so a malformed series cannot break a chart. */
    private fun fit(values: List<Float?>, count: Int): List<Float?> =
        if (values.size == count) values else List(count) { values.getOrNull(it) }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
}

/**
 * Fetches and caches the history documents. The RPi rewrites `day` every 10 min and `week`/`month`
 * once an hour, so a few minutes of cache keeps switching screens and periods instant.
 */
class HistoryRepository(
    private val reader: RtdbReader,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val docs = mutableMapOf<HistoryPeriod, Pair<Long, HistoryDoc?>>()
    private var meta: Pair<Long, HistoryMeta?>? = null

    /** Null when the RPi has not written this period yet. */
    suspend fun get(period: HistoryPeriod, maxAgeMs: Long = MAX_AGE_MS): Result<HistoryDoc?> = mutex.withLock {
        docs[period]?.let { (at, doc) -> if (clock() - at < maxAgeMs) return Result.success(doc) }
        runCatching {
            val doc = (reader.get("$BASE/${period.key}") as? JsonObject)?.let { HistoryParser.parse(period, it) }
            docs[period] = clock() to doc
            doc
        }
    }

    suspend fun meta(maxAgeMs: Long = MAX_AGE_MS): Result<HistoryMeta?> = mutex.withLock {
        meta?.let { (at, m) -> if (clock() - at < maxAgeMs) return Result.success(m) }
        runCatching {
            val m = (reader.get("$BASE/meta") as? JsonObject)?.let { HistoryParser.parseMeta(it) }
            meta = clock() to m
            m
        }
    }

    companion object {
        const val BASE = "history/v1"
        const val MAX_AGE_MS = 5 * 60_000L

        /** How old `meta.updated_ms` may get before the RPi history counts as stopped. */
        const val STALE_MS = 30 * 60_000L

        fun isoDay(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    }
}
