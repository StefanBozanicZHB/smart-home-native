package zhb.company.smart_home.data.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeStatusTrackerTest {

    private val tracker = NodeStatusTracker(heartbeatTimeoutMs = 180_000)
    private val t0 = 1_000_000L

    private fun status(id: String, now: Long) = tracker.snapshot(now)[id]

    @Test
    fun `retained online counts as seen and stays online while heartbeats arrive`() {
        tracker.onMessage("bozaSub/kuca/node4/status", "online", retained = true, nowMs = t0)
        assertEquals(NodeStatus.ONLINE, status("node4", t0)?.status)

        tracker.onMessage("bozaSub/kuca/node4/heartbeat", """{"fw":"node4-2.1.1","up":60,"rssi":-58,"heap":27560,"mqtt":1}""", false, t0 + 60_000)
        tracker.onMessage("bozaSub/kuca/node4/heartbeat", """{"fw":"node4-2.1.1","up":120,"rssi":-60,"heap":27500,"mqtt":1}""", false, t0 + 120_000)

        val health = status("node4", t0 + 290_000)!!
        assertEquals(NodeStatus.ONLINE, health.status)
        assertEquals(NodeHeartbeat("node4-2.1.1", 120, -60, 27500, 1), health.heartbeat)
        assertEquals(t0 + 120_000, health.lastSeenAtMs)
    }

    @Test
    fun `silence longer than the timeout means offline (no heartbeat)`() {
        tracker.onMessage("bozaSub/kuca/node4/status", "online", retained = true, nowMs = t0)

        assertEquals(NodeStatus.ONLINE, status("node4", t0 + 180_000)?.status)
        val late = status("node4", t0 + 180_001)!!
        assertEquals(NodeStatus.OFFLINE, late.status)
        assertEquals(OfflineReason.NO_HEARTBEAT, late.offlineReason)
    }

    @Test
    fun `any live message from a new node counts, retained ones do not`() {
        tracker.onMessage("bozaSub/kuca/node4/status", "online", retained = true, nowMs = t0)
        tracker.onMessage("bozaSub/kuca/node4/climate", """{"seq":1}""", retained = false, nowMs = t0 + 170_000)
        tracker.onMessage("bozaSub/kuca/node4/info", """{"fw":"node4-2.1.1","ip":"192.168.0.38"}""", retained = true, nowMs = t0 + 175_000)

        val health = status("node4", t0 + 300_000)!!
        assertEquals(NodeStatus.ONLINE, health.status)
        assertEquals(t0 + 170_000, health.lastSeenAtMs)
        assertEquals("192.168.0.38", health.info?.ip)
    }

    @Test
    fun `Last Will offline wins until the node is heard again`() {
        tracker.onMessage("bozaSub/kuca/node5/status", "online", retained = true, nowMs = t0)
        tracker.onMessage("bozaSub/kuca/node5/status", "offline", retained = false, nowMs = t0 + 1_000)

        val offline = status("node5", t0 + 2_000)!!
        assertEquals(NodeStatus.OFFLINE, offline.status)
        assertEquals(OfflineReason.LAST_WILL, offline.offlineReason)

        tracker.onMessage("bozaSub/kuca/node5/status", "online", retained = false, nowMs = t0 + 30_000)
        assertEquals(NodeStatus.ONLINE, status("node5", t0 + 31_000)?.status)
        assertNull(status("node5", t0 + 31_000)?.offlineReason)
    }

    @Test
    fun `retained offline on connect is offline`() {
        tracker.onMessage("bozaSub/kuca/node6/status", "offline", retained = true, nowMs = t0)
        assertEquals(NodeStatus.OFFLINE, status("node6", t0)?.status)
    }

    @Test
    fun `a heartbeat without a retained status still marks the node as new firmware`() {
        // e.g. the retained status was not forwarded by a bridge or lost in a broker restart
        tracker.onMessage("bozaSub/kuca/node4/heartbeat", """{"fw":"node4-2.1.1","up":600,"rssi":-60,"heap":27000,"mqtt":1}""", retained = false, nowMs = t0)
        val h = status("node4", t0 + 1_000)!!
        assertEquals(NodeStatus.ONLINE, h.status)
        assertEquals("node4-2.1.1", h.firmware)

        // a retained info alone: known as new firmware, not seen live yet
        tracker.onMessage("bozaSub/kuca/node1/info", """{"fw":"node1-2.0.0","ip":"192.168.1.51"}""", retained = true, nowMs = t0)
        assertEquals(NodeStatus.UNKNOWN, status("node1", t0)?.status)
        assertEquals("192.168.1.51", status("node1", t0)?.info?.ip)
    }

    @Test
    fun `legacy nodes and unrelated topics are not tracked`() {
        assertFalse(tracker.onMessage("bozaSub/kuca/node8/temp", "28.25", retained = false, nowMs = t0))
        assertFalse(tracker.onMessage("bozaSub/kuca/node7/vrata/stanje", "1", retained = false, nowMs = t0))
        assertFalse(tracker.onMessage("bozaSub/kuca/node2/heartbeat", "{}", retained = false, nowMs = t0))
        assertTrue(tracker.snapshot(t0).isEmpty())
    }

    @Test
    fun `clear forgets everything`() {
        tracker.onMessage("bozaSub/kuca/node4/status", "online", retained = true, nowMs = t0)
        tracker.clear()
        assertTrue(tracker.snapshot(t0).isEmpty())
    }
}

class SignalQualityTest {

    @Test
    fun `rssi thresholds`() {
        assertEquals(SignalQuality.EXCELLENT, SignalQuality.fromRssi(-45))
        assertEquals(SignalQuality.EXCELLENT, SignalQuality.fromRssi(-60))
        assertEquals(SignalQuality.GOOD, SignalQuality.fromRssi(-61))
        assertEquals(SignalQuality.GOOD, SignalQuality.fromRssi(-70))
        assertEquals(SignalQuality.WEAK, SignalQuality.fromRssi(-71))
        assertEquals(SignalQuality.WEAK, SignalQuality.fromRssi(-80))
        assertEquals(SignalQuality.POOR, SignalQuality.fromRssi(-81))
    }

    @Test
    fun `rssi comes from the last heartbeat, else from connect-time info`() {
        val tracker = NodeStatusTracker()
        tracker.onMessage("bozaSub/kuca/node4/status", "online", retained = true, nowMs = 0)
        tracker.onMessage("bozaSub/kuca/node4/info", """{"fw":"node4-2.1.1","rssi":-72}""", retained = true, nowMs = 0)
        assertEquals(-72, tracker.snapshot(0)["node4"]?.rssi)
        assertEquals(SignalQuality.WEAK, tracker.snapshot(0)["node4"]?.signal)

        tracker.onMessage("bozaSub/kuca/node4/heartbeat", """{"fw":"node4-2.1.1","rssi":-58}""", retained = false, nowMs = 1)
        assertEquals(-58, tracker.snapshot(1)["node4"]?.rssi)
        assertEquals(SignalQuality.EXCELLENT, tracker.snapshot(1)["node4"]?.signal)
    }
}

class NodeDetailsTest {

    private val tracker = NodeStatusTracker(heartbeatTimeoutMs = 180_000)
    private val t0 = 10_000_000L

    private fun online(id: String = "node4") = tracker.onMessage("bozaSub/kuca/$id/status", "online", retained = true, nowMs = t0)

    @Test
    fun `info carries mac and ssid`() {
        online()
        tracker.onMessage(
            "bozaSub/kuca/node4/info",
            """{"fw":"node4-2.1.1","git":"6de36a7","ip":"192.168.1.54","mac":"A4:CF:12:00:00:00","host":"node4-living-room-ground","ssid":"HomeWiFi","rssi":-58}""",
            retained = true, nowMs = t0,
        )
        assertEquals(
            NodeInfo("node4-2.1.1", "6de36a7", "192.168.1.54", "node4-living-room-ground", -58, "A4:CF:12:00:00:00", "HomeWiFi"),
            tracker.snapshot(t0)["node4"]?.info,
        )
    }

    @Test
    fun `uptime is extrapolated from the last heartbeat and flags a recent restart`() {
        online()
        tracker.onMessage("bozaSub/kuca/node4/heartbeat", """{"fw":"f","up":600,"rssi":-60,"heap":27000,"mqtt":3}""", false, t0 + 1_000)
        val h = tracker.snapshot(t0 + 61_000)["node4"]!!
        assertEquals(t0 + 1_000 - 600_000, h.bootedAtMs)
        assertEquals(660L, h.uptimeSecondsAt(t0 + 61_000))
        assertTrue(h.recentlyRestarted(t0 + 61_000))
        assertFalse(h.recentlyRestarted(t0 + 1_000 + 30 * 60_000))
        assertEquals(2L, h.reconnects)
    }

    @Test
    fun `no uptime while offline or without heartbeat`() {
        online()
        assertNull(tracker.snapshot(t0)["node4"]!!.uptimeSecondsAt(t0))
        tracker.onMessage("bozaSub/kuca/node4/heartbeat", """{"up":100}""", false, t0)
        assertNull(tracker.snapshot(t0 + 200_000)["node4"]!!.uptimeSecondsAt(t0 + 200_000))
    }

    @Test
    fun `boot message is shown only for the current run`() {
        online()
        tracker.onMessage("bozaSub/kuca/node4/boot", """{"fw":"node4-2.1.1","git":"6de36a7","reset":"Hardware Watchdog"}""", false, t0 + 5_000)
        tracker.onMessage("bozaSub/kuca/node4/heartbeat", """{"up":65}""", false, t0 + 60_000)
        val h = tracker.snapshot(t0 + 60_000)["node4"]!!
        assertEquals("Hardware Watchdog", h.currentBoot?.reset)

        // The node rebooted later without the app seeing the new boot message.
        tracker.onMessage("bozaSub/kuca/node4/heartbeat", """{"up":30}""", false, t0 + 3_600_000)
        assertNull(tracker.snapshot(t0 + 3_600_000)["node4"]!!.currentBoot)
    }
}
