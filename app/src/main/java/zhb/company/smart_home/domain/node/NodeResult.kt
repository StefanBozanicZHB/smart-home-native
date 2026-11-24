package zhb.company.smart_home.domain.node


/**
 * Answer to a command on `<node>/result` (smart-home-mcu docs/mqtt_topics.md, "API conventions"):
 * `{"cmd":"zones/ground/set","id":"req-17","ok":true,"rev":9}` or `{"ok":false,"error":"..."}`.
 * [saved] false: the change is active but could not be stored on the node (lost on restart).
 */
data class NodeCommandResult(
    val command: String,
    val id: String?,
    val ok: Boolean,
    val rev: Long?,
    val error: String?,
    val saved: Boolean = true,
) {
    companion object {
        fun parse(payload: String): NodeCommandResult? {
            val o = parseJsonObject(payload) ?: return null
            return NodeCommandResult(
                command = o.string("cmd") ?: return null,
                // The node echoes the id as sent; it may be a string or a number.
                id = o.primitive("id")?.content,
                ok = o.bool("ok") ?: return null,
                rev = o.long("rev"),
                error = o.string("error"),
                saved = o.bool("saved") ?: true,
            )
        }
    }
}
