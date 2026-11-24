package zhb.company.smart_home.data.heating

/** Node 5 topics (`bozaSub/kuca/node5/...`). */
class Node5Topics(val nodeId: String = REAL) {
    val base = "bozaSub/kuca/$nodeId/"

    // commands (JSON, never retained)
    val mainSwitchSet = "${base}main_switch/set"
    fun zoneSet(zone: String) = "${base}zones/$zone/set"
    val sensorsSet = "${base}sensors/set"

    companion object {
        const val REAL = "node5"

        // relative state/answer paths (retained unless noted)
        const val CONFIG = "config"
        const val STATE = "state"
        const val PUMP = "pump"
        const val SAFE_MODE = "safe_mode"
        const val SENSORS = "sensors"
        const val ZONES_PREFIX = "zones/"
    }
}
