package com.usbanywhere.gaming.dualsense

import com.usbanywhere.gaming.simracing.FfbJitterGuard
import com.usbanywhere.gaming.simracing.SimRacingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualSenseAndSimRacingTest {

    @Test
    fun testDualSenseProfileDetection() {
        // DualSense PS5
        assertTrue(DualSenseProfile.isDualSense(0x054C, 0x0CE6))
        assertTrue(DualSenseProfile.isSonyController(0x054C, 0x0CE6))
        assertEquals("Sony DualSense (PS5)", DualSenseProfile.getDisplayName(0x054C, 0x0CE6))

        // DualSense Edge
        assertTrue(DualSenseProfile.isDualSense(0x054C, 0x0DF2))

        // DualShock 4
        assertFalse(DualSenseProfile.isDualSense(0x054C, 0x05C4))
        assertTrue(DualSenseProfile.isSonyController(0x054C, 0x05C4))

        // Non-Sony
        assertFalse(DualSenseProfile.isSonyController(0x045E, 0x028E))
    }

    @Test
    fun testIsochronousJitterBuffer() {
        val buffer = IsochronousJitterBuffer(targetQueueDepth = 2, maxQueueDepth = 4)

        // Empty buffer pop causes underflow
        val emptyPop = buffer.popNext()
        assertNull(emptyPop)
        assertEquals(1, buffer.getStats().underflowCount)

        // Push 3 packets
        buffer.push(byteArrayOf(1, 2, 3), sequenceNumber = 1)
        buffer.push(byteArrayOf(4, 5, 6), sequenceNumber = 2)
        buffer.push(byteArrayOf(7, 8, 9), sequenceNumber = 3)

        assertEquals(3, buffer.getStats().currentQueueDepth)

        val p1 = buffer.popNext()
        assertNotNull(p1)
        assertEquals(1L, p1?.sequenceNumber)

        // Push past max depth (4) to trigger stale drop
        buffer.push(byteArrayOf(1), sequenceNumber = 4)
        buffer.push(byteArrayOf(2), sequenceNumber = 5)
        buffer.push(byteArrayOf(3), sequenceNumber = 6) // Exceeds max 4, drops oldest!

        assertTrue(buffer.getStats().droppedStaleCount > 0)
    }

    @Test
    fun testSimRacingProfileDetection() {
        // Logitech G29
        assertTrue(SimRacingProfile.isRacingWheel(0x046D, 0xC24F))
        assertEquals("Logitech G29 Driving Force", SimRacingProfile.getWheelName(0x046D, 0xC24F))

        // Thrustmaster T300RS
        assertTrue(SimRacingProfile.isRacingWheel(0x044F, 0xB66E))

        // Fanatec
        assertTrue(SimRacingProfile.isRacingWheel(0x0EB7, 0x0001))

        // Standard Keyboard
        assertFalse(SimRacingProfile.isRacingWheel(0x046D, 0xC31C))
    }

    @Test
    fun testFfbJitterGuard() {
        val guard = FfbJitterGuard(maxAllowedGapMs = 50L)

        val frame1 = byteArrayOf(0x01, 0x10)
        val frame2 = byteArrayOf(0x01, 0x20)
        val staleFrame = byteArrayOf(0x01, 0x05)

        // Normal sequence
        assertTrue(guard.processFrame(1, frame1, nowMs = 1000L))
        assertTrue(guard.processFrame(2, frame2, nowMs = 1010L))

        // Stale frame arriving late (sequence 1 arriving after sequence 2)
        assertFalse(guard.processFrame(1, staleFrame, nowMs = 1015L))
        assertEquals(1, guard.getStats().staleDropped)

        // Gap spike simulation (next frame arrives after 100ms > 50ms)
        assertTrue(guard.processFrame(3, frame1, nowMs = 1120L))
        assertEquals(1, guard.getStats().gapSpikesDetected)
    }
}
