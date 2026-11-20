package zhb.company.smart_home.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationInboxTest {

    private fun e(key: String, at: Long, tag: String = "node-node3", problem: Boolean, feedback: String? = null) =
        InboxEntry(key = key, id = key, title = key, channel = "node_status", tag = tag, feedbackOffered = problem, atMs = at, receivedMs = at, feedback = feedback)

    @Test
    fun `problem is resolved by the next entry with the same tag`() {
        val off = e("off", 100, problem = true)
        val on = e("on", 200, problem = false)
        val other = e("x", 150, tag = "node-node5", problem = false)
        val all = listOf(on, other, off) // newest first
        assertEquals(on, NotificationInbox.resolution(off, all))
        assertNull("not a problem", NotificationInbox.resolution(on, all))
    }

    @Test
    fun `a newer problem in between means the first one is not known to be resolved`() {
        val off1 = e("off1", 100, problem = true)
        val off2 = e("off2", 150, problem = true)
        val on = e("on", 200, problem = false)
        val all = listOf(on, off2, off1)
        assertNull(NotificationInbox.resolution(off1, all))
        assertEquals(on, NotificationInbox.resolution(off2, all))
        assertNull(NotificationInbox.resolution(off2, listOf(off2, off1)))
    }

    @Test
    fun `needs label only for unlabeled problems with an id`() {
        assertTrue(e("a", 1, problem = true).needsLabel)
        assertFalse(e("a", 1, problem = true, feedback = "ok").needsLabel)
        assertFalse(e("a", 1, problem = false).needsLabel)
        assertFalse(e("a", 1, problem = true).copy(id = null).needsLabel)
    }
}
