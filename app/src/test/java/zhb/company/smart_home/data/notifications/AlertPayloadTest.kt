package zhb.company.smart_home.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPayloadTest {

    /** What das_notifier.py 1.4.0 sends for "node offline" (all values are strings in FCM). */
    private val offline = mapOf(
        "type" to "node_status", "node" to "node5", "state" to "offline", "since_ms" to "1700000000000",
        "at_ms" to "1700000120000", "reason" to "silence", "title" to "Čvor nije dostupan: Grejanje",
        "body" to "node5 se ne javlja od 14:03.", "channel_id" to "node_status", "tag" to "node-node5",
        "id" to "node-node5-1700000000000-offline", "feedback" to "1", "sent_ms" to "1700000121000",
    )

    @Test
    fun dataOnlyMessageWithFeedback() {
        val p = AlertPayload.parse(offline)!!
        assertEquals("Čvor nije dostupan: Grejanje", p.title)
        assertEquals(PushNotifications.CHANNEL_NODES, p.channel)
        assertEquals("node-node5", p.tag)
        assertTrue(p.feedback)
        assertEquals(1700000120000L, p.atMs)
    }

    @Test
    fun receiptKeepsTimesAsNumbers() {
        val f = AlertPayload.parse(offline)!!.receiptFields(1700000121500L, "1.0.0")
        assertEquals(1700000121500L, f["received_ms"])
        assertEquals(1700000121000L, f["sent_ms"])
        assertEquals("node5", f["node"])
        assertEquals(true, f["feedback_offered"])
        assertFalse(f.containsKey("body"))
        assertFalse(f.containsKey("room"))
    }

    @Test
    fun unsafeOrMissingIdDisablesFeedback() {
        assertNull(AlertPayload.parse(offline + ("id" to "a/b"))!!.id)
        assertFalse(AlertPayload.parse(offline + ("id" to "a/b"))!!.feedback)
        assertFalse(AlertPayload.parse(offline - "id")!!.feedback)
        assertFalse(AlertPayload.parse(offline - "feedback")!!.feedback)
    }

    @Test
    fun olderNotificationMessagesStillWork() {
        val p = AlertPayload.parse(
            mapOf("type" to "heating", "event" to "safe_mode"),
            fallbackTitle = "Grejanje u bezbednom režimu", fallbackChannel = null, fallbackTag = "safe-node5",
        )!!
        assertEquals(PushNotifications.CHANNEL_HEATING, p.channel)
        assertEquals("safe-node5", p.tag)
        assertNull(AlertPayload.parse(mapOf("type" to "system")))
    }
}
