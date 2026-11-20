package zhb.company.smart_home.domain.node

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Command payloads shared by every node with the new JSON API. */
object NodeJson {
    /**
     * An on/off output (Node 1 fountain pump and light, Node 2 neon and lamp): `{"on":true,"minutes":120,"id":...}`.
     * [minutes] (1-1440) switches it off by itself after that time; ignored when switching off.
     */
    fun switchCommand(id: String, on: Boolean, minutes: Int? = null): String = buildJsonObject {
        put("on", on)
        if (on && minutes != null) put("minutes", minutes)
        put("id", id)
    }.toString()
}
