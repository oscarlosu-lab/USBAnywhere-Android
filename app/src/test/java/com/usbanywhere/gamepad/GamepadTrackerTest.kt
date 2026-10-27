package com.usbanywhere.gamepad

import com.usbanywhere.client.bridge.VirtualInputBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadTrackerTest {

    @Test
    fun testPollingRateCalculation1000Hz() {
        val tracker = PollingRateTracker(windowSize = 32)
        var timeNs = 1_000_000_000L // 1 second
        val step1000HzNs = 1_000_000L // 1 millisecond = 1000 Hz

        // Feed 50 events at exactly 1ms interval (1000 Hz)
        var lastStats = PollingRateStats()
        for (i in 0 until 50) {
            timeNs += step1000HzNs
            lastStats = tracker.recordEvent(timeNs)
        }

        assertEquals(1000.0, lastStats.avgHz, 1.0)
        assertEquals(1000.0, lastStats.currentHz, 1.0)
        assertEquals(1000.0, lastStats.maxHz, 1.0)
        assertTrue(lastStats.jitterMs < 0.05) // Jitter should be virtually zero for exact timestamps
    }

    @Test
    fun testPollingRateCalculation8000Hz() {
        val tracker = PollingRateTracker(windowSize = 64)
        var timeNs = 2_000_000_000L
        val step8000HzNs = 125_000L // 125 microseconds = 8000 Hz

        var lastStats = PollingRateStats()
        for (i in 0 until 100) {
            timeNs += step8000HzNs
            lastStats = tracker.recordEvent(timeNs)
        }

        assertEquals(8000.0, lastStats.avgHz, 10.0)
        assertEquals(8000.0, lastStats.maxHz, 10.0)
        assertEquals(100L, lastStats.totalReports)
    }

    @Test
    fun testIdleGapRecovery() {
        val tracker = PollingRateTracker(windowSize = 16)
        var timeNs = 3_000_000_000L
        val step500HzNs = 2_000_000L // 2ms = 500 Hz

        // Active movement
        for (i in 0 until 20) {
            timeNs += step500HzNs
            tracker.recordEvent(timeNs)
        }

        // 1 second of inactivity (idle)
        timeNs += 1_000_000_000L
        tracker.recordEvent(timeNs)

        // Resume movement
        timeNs += step500HzNs
        val resumeStats = tracker.recordEvent(timeNs)

        // Average should recover smoothly to 500 Hz without being wrecked by the 1-second gap
        assertEquals(500.0, resumeStats.currentHz, 5.0)
    }

    @Test
    fun testVirtualInputBridgeForwarding() {
        val bridge = VirtualInputBridge()
        // Report: LX=255 (full right), LY=0 (full up), RX=128, RY=128, buttons=0x01 (btnA)
        val report = byteArrayOf(
            255.toByte(), 0.toByte(), 128.toByte(), 128.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
        )

        val state = bridge.decodeHidReport(report)
        assertTrue(state.leftStickX > 0.9f)
        assertTrue(state.leftStickY < -0.9f)
        assertTrue(state.buttonA)
        assertEquals(false, state.buttonB)
    }
}
