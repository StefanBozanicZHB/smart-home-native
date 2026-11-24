package zhb.company.smart_home.domain.garden

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Retained `node6/state` (pool, smart-home-mcu docs/mqtt_topics.md, section Node 6). */
data class Node6State(
    val seq: Long,
    val uptimeSeconds: Long,
    val pump: Boolean,
    val pumpLeftSeconds: Int?,
    /** Pool heating relay, independent of the pump; null if the node does not report it (before `node6-2.0.1`). */
    val heating: Boolean?,
    val heatingLeftSeconds: Int?,
    /** The water temperature sensor answered in the last cycle; null if not reported. */
    val sensorOk: Boolean?,
)

object Node6Json {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseState(payload: String): Node6State? {
        val o = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject ?: return null
        fun prim(key: String) = (o[key] as? JsonPrimitive)?.takeUnless { it is JsonNull || it.isString }
        return Node6State(
            seq = prim("seq")?.longOrNull ?: return null,
            uptimeSeconds = prim("up")?.longOrNull ?: return null,
            pump = prim("pump")?.booleanOrNull ?: false,
            pumpLeftSeconds = prim("pump_left")?.intOrNull,
            heating = prim("heating")?.booleanOrNull,
            heatingLeftSeconds = prim("heating_left")?.intOrNull,
            sensorOk = prim("ds18b20")?.booleanOrNull,
        )
    }

    const val PUMP_LABEL = "Pumpa bazena"
    const val HEATING_LABEL = "Grejanje bazena"

    fun onLabels(state: Node6State?): List<String> = buildList {
        if (state?.pump == true) add(PUMP_LABEL)
        if (state?.heating == true) add(HEATING_LABEL)
    }
}
