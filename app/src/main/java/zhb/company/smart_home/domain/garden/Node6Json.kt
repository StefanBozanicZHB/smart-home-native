package zhb.company.smart_home.domain.garden

import zhb.company.smart_home.domain.node.bool
import zhb.company.smart_home.domain.node.int
import zhb.company.smart_home.domain.node.long
import zhb.company.smart_home.domain.node.parseJsonObject

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
    fun parseState(payload: String): Node6State? {
        val o = parseJsonObject(payload) ?: return null
        return Node6State(
            seq = o.long("seq") ?: return null,
            uptimeSeconds = o.long("up") ?: return null,
            pump = o.bool("pump") ?: false,
            pumpLeftSeconds = o.int("pump_left"),
            heating = o.bool("heating"),
            heatingLeftSeconds = o.int("heating_left"),
            sensorOk = o.bool("ds18b20"),
        )
    }

    const val PUMP_LABEL = "Pumpa bazena"
    const val HEATING_LABEL = "Grejanje bazena"

    fun onLabels(state: Node6State?): List<String> = buildList {
        if (state?.pump == true) add(PUMP_LABEL)
        if (state?.heating == true) add(HEATING_LABEL)
    }
}
