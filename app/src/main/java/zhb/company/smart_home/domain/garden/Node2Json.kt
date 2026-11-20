package zhb.company.smart_home.domain.garden

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Retained `node2/state` (yard, `node2-2.0.0`, smart-home-mcu docs/mqtt_topics.md, section Node 2):
 * the two lights with their automatic switch-off timers and whether the DS18B20 answered.
 */
data class Node2State(
    val seq: Long,
    val uptimeSeconds: Long,
    val neon: Boolean,
    val neonLeftSeconds: Int?,
    val lamp: Boolean,
    val lampLeftSeconds: Int?,
    /** The outdoor temperature sensor answered in the last cycle; null if not reported. */
    val sensorOk: Boolean?,
)

object Node2Json {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseState(payload: String): Node2State? {
        val o = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject ?: return null
        fun prim(key: String) = (o[key] as? JsonPrimitive)?.takeUnless { it is JsonNull || it.isString }
        return Node2State(
            seq = prim("seq")?.longOrNull ?: return null,
            uptimeSeconds = prim("up")?.longOrNull ?: return null,
            neon = prim("neon")?.booleanOrNull ?: false,
            neonLeftSeconds = prim("neon_left")?.intOrNull,
            lamp = prim("lamp")?.booleanOrNull ?: false,
            lampLeftSeconds = prim("lamp_left")?.intOrNull,
            sensorOk = prim("ds18b20")?.booleanOrNull,
        )
    }
}

/** Node 2 lights for the home "devices on" list and GoodNight. */
object Node2Devices {
    const val NEON = "LED traka (dvorište)"
    const val LAMP = "Sijalica (dvorište)"

    fun onLabels(state: Node2State?): List<String> {
        state ?: return emptyList()
        return buildList {
            if (state.neon) add(NEON)
            if (state.lamp) add(LAMP)
        }
    }
}
