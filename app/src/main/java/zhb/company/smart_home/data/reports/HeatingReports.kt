package zhb.company.smart_home.data.reports

import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import zhb.company.smart_home.data.remote.RtdbError
import zhb.company.smart_home.data.remote.RtdbReader

/**
 * Daily heating reports written by the RPi (`das_report.py` in the DAS repository) to Firebase
 * Realtime Database:
 * - `reports/heating_index/<yyyy-MM-dd>`: [ReportSummary] for the list;
 * - `reports/heating/<yyyy-MM-dd>`: the full [HeatingReport].
 * Days without heating have no report.
 */
class HeatingReportsApi(private val reader: RtdbReader) {

    /** Newest first. */
    suspend fun list(limit: Int = 60): Result<List<ReportSummary>> = runCatching {
        val obj = reader.get("reports/heating_index") {
            parameter("orderBy", "\"\$key\"")
            parameter("limitToLast", limit)
        } as? JsonObject ?: return@runCatching emptyList()
        obj.mapNotNull { (date, value) ->
            (value as? JsonObject)?.let { json.decodeFromJsonElement<ReportSummary>(it).copy(date = date) }
        }.sortedByDescending { it.date }
    }

    suspend fun get(date: String): Result<HeatingReport?> = runCatching {
        (reader.get("reports/heating/$date") as? JsonObject)?.let { json.decodeFromJsonElement<HeatingReport>(it) }
    }

    private companion object {
        val json = RtdbReader.json
    }
}

typealias ReportsError = RtdbError

@Serializable
data class ReportSummary(
    val date: String = "",
    @SerialName("run_s") val runSeconds: Long = 0,
    val starts: Int = 0,
    @SerialName("room_min") val roomMin: Double? = null,
    @SerialName("room_max") val roomMax: Double? = null,
    @SerialName("outdoor_min") val outdoorMin: Double? = null,
    @SerialName("outdoor_max") val outdoorMax: Double? = null,
    @SerialName("safe_mode_s") val safeModeSeconds: Long = 0,
    @SerialName("completeness_pct") val completenessPct: Double? = null,
)

@Serializable
data class HeatingReport(
    val date: String = "",
    val summary: String? = null,
    val pump: Pump = Pump(),
    val zones: Map<String, Zone> = emptyMap(),
    val outdoor: Outdoor? = null,
    @SerialName("pipes_while_running") val pipes: Map<String, Double> = emptyMap(),
    @SerialName("safe_mode") val safeMode: SafeMode = SafeMode(),
    val efficiency: Efficiency = Efficiency(),
    val data: DataQuality = DataQuality(),
    @SerialName("generated_at_ms") val generatedAtMs: Long? = null,
) {
    @Serializable
    data class Pump(
        @SerialName("run_s") val runSeconds: Long = 0,
        val starts: Int = 0,
        @SerialName("start_src") val startSources: Map<String, Int> = emptyMap(),
        val cycles: Cycles? = null,
        @SerialName("main_switch_on_s") val mainSwitchOnSeconds: Long = 0,
    )

    @Serializable
    data class Cycles(
        val count: Int = 0,
        @SerialName("avg_s") val avgSeconds: Long = 0,
        @SerialName("min_s") val minSeconds: Long = 0,
        @SerialName("max_s") val maxSeconds: Long = 0,
    )

    @Serializable
    data class Range(val min: Double? = null, val avg: Double? = null, val max: Double? = null)

    @Serializable
    data class Zone(
        val room: Range? = null,
        @SerialName("target_avg") val targetAvg: Double? = null,
        @SerialName("deviation_avg") val deviationAvg: Double? = null,
        @SerialName("below_target_s") val belowTargetSeconds: Long = 0,
        @SerialName("demand_s") val demandSeconds: Long = 0,
    )

    @Serializable
    data class Outdoor(val min: Double? = null, val avg: Double? = null, val max: Double? = null, val hdd: Double? = null)

    @Serializable
    data class SafeMode(@SerialName("total_s") val totalSeconds: Long = 0, val reasons: Map<String, Long> = emptyMap())

    @Serializable
    data class Efficiency(@SerialName("run_h_per_hdd") val runHoursPerHdd: Double? = null)

    @Serializable
    data class DataQuality(
        val samples: Int = 0,
        val expected: Int = 0,
        @SerialName("completeness_pct") val completenessPct: Double? = null,
        val reboots: Int = 0,
    )
}
