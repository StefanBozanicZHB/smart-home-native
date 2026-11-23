package zhb.company.smart_home.ui.system

/** Human names for MQTT node ids, shared by the home alerts and the system screen. */
object NodeLabels {
    private val LABELS = mapOf(
        "node1" to "Fontana i prskalice",
        "node2" to "Dvorište",
        "node3" to "Ulazna vrata",
        "node4" to "Dnevni boravak (prizemlje)",
        "node5" to "Grejanje",
        "node6" to "Bazen",
        "node8" to "Dnevni boravak (sprat)",
    )

    fun of(nodeId: String): String = LABELS[nodeId] ?: nodeId

    /**
     * Nodes in the house that are in use. Anything else on the broker (an old test board's
     * retained `status`, a retired node) is not shown and does not raise alerts.
     */
    val ACTIVE: List<String> = LABELS.keys.sorted()

    fun isActive(nodeId: String): Boolean = nodeId in ACTIVE
}
