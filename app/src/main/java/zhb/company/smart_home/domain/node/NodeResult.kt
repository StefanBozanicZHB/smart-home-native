package zhb.company.smart_home.domain.node

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

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
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(payload: String): NodeCommandResult? {
            val o = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject ?: return null
            fun prim(key: String) = (o[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
            return NodeCommandResult(
                command = prim("cmd")?.takeIf { it.isString }?.content ?: return null,
                // The node echoes the id as sent; it may be a string or a number.
                id = prim("id")?.content,
                ok = prim("ok")?.takeUnless { it.isString }?.booleanOrNull ?: return null,
                rev = prim("rev")?.takeUnless { it.isString }?.longOrNull,
                error = prim("error")?.takeIf { it.isString }?.content,
                saved = prim("saved")?.takeUnless { it.isString }?.booleanOrNull ?: true,
            )
        }
    }
}
