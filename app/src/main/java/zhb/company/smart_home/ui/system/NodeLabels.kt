package zhb.company.smart_home.ui.system

/** Human names for MQTT node ids, shared by the home alerts and the system screen. */
object NodeLabels {
    private val LABELS = mapOf(
        "node0" to "Prskalice (tajmer)",
        "node1" to "Prskalice / fontana",
        "node2" to "Dvorište",
        "node3" to "Ulazna vrata",
        "node4" to "Dnevni boravak (prizemlje)",
        "node5" to "Grejanje",
        "node6" to "Bazen",
        "node7" to "Šupa",
        "node8" to "Dnevni boravak (sprat)",
        "testnode5" to "Grejanje – test ploča",
    )

    /** Nodes that exist but are not in use (smart-home-mcu docs/nodes.md). */
    val NOT_IN_USE = setOf("node7", "node9")

    fun of(nodeId: String): String = LABELS[nodeId] ?: nodeId
}
