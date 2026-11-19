package zhb.company.smart_home.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SprinklerConfigDto(
    val start: String,
    val on: Int,
    val off: Int,
    val sets: Int,
    val auto: Int,
)

/**
 * Encodes/decodes the sprinkler auto-timer config JSON exchanged on
 * `bozaSub/kuca/node0/sprinkler/{config,status}`. The wire "on" duration is
 * in seconds; the UI displays it in minutes (matching the Flutter app, which
 * divides by 60 for display only).
 */
object SprinklerConfigCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(payload: String): SprinklerConfigDto? = runCatching { json.decodeFromString<SprinklerConfigDto>(payload) }.getOrNull()

    fun encode(
        startTime: String,
        onDurationMinutes: Int,
        offDurationSeconds: Int,
        sets: Int,
        auto: Boolean,
    ): String = json.encodeToString(
        SprinklerConfigDto(
            start = startTime,
            on = onDurationMinutes * 60,
            off = offDurationSeconds,
            sets = sets,
            auto = if (auto) 1 else 0,
        )
    )
}
