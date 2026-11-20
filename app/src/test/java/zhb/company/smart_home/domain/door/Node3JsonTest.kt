package zhb.company.smart_home.domain.door

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Payloads from smart-home-mcu docs/mqtt_topics.md, section Node 3 (commit 84fdf93). */
class Node3JsonTest {

    private val state = Node3Json.parseState("""{"seq":8,"up":480,"lock":"locked","tags":12,"reader_ok":true}""")

    @Test
    fun `state from the docs`() {
        assertEquals(Node3State(8, 480, lockOpen = false, tags = 12, readerOk = true), state)
        assertEquals(true, Node3Json.parseState("""{"seq":9,"up":481,"lock":"open"}""")?.lockOpen)
        assertNull(Node3Json.parseState("""{"seq":9,"up":481,"lock":"ajar"}"""))
        assertNull(Node3Json.parseState(""))
    }

    @Test
    fun `access events from the docs`() {
        val granted = Node3Json.parseAccess("""{"seq":4,"at":3600,"result":"granted","method":"tag","name":"Stefan","uid_end":"3344"}""")!!
        assertEquals(AccessEvent(4, 3600, granted = true, method = AccessMethod.TAG, name = "Stefan", uidEnd = "3344"), granted)
        val denied = Node3Json.parseAccess("""{"seq":5,"at":400,"result":"denied","method":"tag","name":null,"uid_end":"A1B2"}""")!!
        assertEquals(false, denied.granted)
        assertNull(denied.name)
        assertNull(Node3Json.parseAccess("""{"seq":5,"at":400,"result":"maybe"}"""))
    }

    @Test
    fun `age of the last access only within the same run of the node`() {
        val event = AccessEvent(4, 300, granted = true, method = AccessMethod.APP, name = null, uidEnd = null)
        assertEquals(180L, event.secondsBefore(state))
        // the node restarted after the event: uptime is smaller than the event's
        assertNull(event.copy(atUptimeSeconds = 3600).secondsBefore(state))
        assertNull(event.secondsBefore(null))
    }
}
