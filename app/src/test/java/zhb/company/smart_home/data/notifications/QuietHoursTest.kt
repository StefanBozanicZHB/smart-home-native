package zhb.company.smart_home.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
    private val night = QuietHours.of(true, "22:00", "07:00")
    private fun at(h: Int, m: Int = 0) = h * 60 + m

    private fun payload(channel: String, feedback: Boolean) = AlertPayload(
        id = "x", title = "T", body = null, channel = channel, tag = null, feedback = feedback, atMs = null, data = emptyMap(),
    )

    @Test
    fun windowAcrossMidnight() {
        assertTrue(night.isQuietAt(at(22)))
        assertTrue(night.isQuietAt(at(3, 30)))
        assertTrue(night.isQuietAt(at(6, 59)))
        assertFalse(night.isQuietAt(at(7)))
        assertFalse(night.isQuietAt(at(21, 59)))
    }

    @Test
    fun windowWithinADayAndDisabled() {
        val nap = QuietHours.of(true, "13:00", "15:00")
        assertTrue(nap.isQuietAt(at(14)))
        assertFalse(nap.isQuietAt(at(15)))
        assertFalse(night.copy(enabled = false).isQuietAt(at(3)))
        assertFalse(QuietHours.of(true, "08:00", "08:00").isQuietAt(at(8)))
    }

    @Test
    fun urgentProblemsAlwaysAlert() {
        assertFalse(night.silence(payload(PushNotifications.CHANNEL_NODES, feedback = true), at(3)))
        assertFalse(night.silence(payload(PushNotifications.CHANNEL_HEATING, feedback = true), at(3)))
        assertTrue(night.silence(payload(PushNotifications.CHANNEL_NODES, feedback = false), at(3)), "back online")
        assertTrue(night.silence(payload(PushNotifications.CHANNEL_SYSTEM, feedback = true), at(3)), "logger")
        assertTrue(night.silence(payload(PushNotifications.CHANNEL_REPORTS, feedback = false), at(3)))
        assertFalse(night.silence(payload(PushNotifications.CHANNEL_REPORTS, feedback = false), at(12)))
    }

    @Test
    fun parsing() {
        assertEquals(at(7, 5), QuietHours.parseTime("7:05"))
        assertEquals(at(23, 59), QuietHours.parseTime(" 23:59 "))
        assertNull(QuietHours.parseTime("24:00"))
        assertNull(QuietHours.parseTime("7.30"))
        assertEquals(at(22), QuietHours.of(true, "bad", "07:00").startMinute)
    }

    private fun assertTrue(condition: Boolean, message: String) = org.junit.Assert.assertTrue(message, condition)
}
