package zhb.company.smart_home.data.heating

/**
 * Node 5 topics under a switchable node id: `node5` (the real heat pump) or `testnode5`
 * (the bench-test board built with `pio run -e node5_bench`).
 */
class Node5Topics(val nodeId: String) {
    val base = "bozaSub/kuca/$nodeId/"

    // commands (JSON, never retained)
    val mainSwitchSet = "${base}main_switch/set"
    fun zoneSet(zone: String) = "${base}zones/$zone/set"
    val configGet = "${base}config/get"

    /** Topic relative to [base] (e.g. `zones/ground`), or null if it belongs to another node. */
    fun relative(topic: String): String? = if (topic.startsWith(base)) topic.substring(base.length) else null

    companion object {
        const val REAL = "node5"
        const val BENCH = "testnode5"
        val ALLOWED = setOf(REAL, BENCH)

        // relative state/answer paths (retained unless noted)
        const val CONFIG = "config"
        const val STATE = "state"
        const val PUMP = "pump"
        const val SAFE_MODE = "safe_mode"
        const val RESULT = "result" // not retained
        const val ZONES_PREFIX = "zones/"
    }
}
