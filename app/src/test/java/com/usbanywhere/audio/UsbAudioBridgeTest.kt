package com.usbanywhere.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UsbAudioBridgeTest {

    @Before
    fun setUp() {
        UsbAudioBridge.resetStats()
        UsbAudioBridge.setEnabled(true)
        UsbAudioBridge.setLatencyProfile(UsbAudioBridge.AudioBufferLatency.BALANCED_4MS)
    }

    @Test
    fun testAudioProfileClassification() {
        assertTrue(UsbAudioProfile.isAudioInterface(0x01))
        assertFalse(UsbAudioProfile.isAudioInterface(0x03)) // HID

        assertTrue(UsbAudioProfile.isAudioStreaming(0x01, 0x02))
        assertFalse(UsbAudioProfile.isAudioStreaming(0x01, 0x01)) // Audio Control
    }

    @Test
    fun testAudioBridgeLatencyProfiles() {
        assertEquals(4, UsbAudioBridge.latencyProfile.value.ms)

        UsbAudioBridge.setLatencyProfile(UsbAudioBridge.AudioBufferLatency.ULTRA_LOW_2MS)
        assertEquals(2, UsbAudioBridge.latencyProfile.value.ms)

        UsbAudioBridge.setLatencyProfile(UsbAudioBridge.AudioBufferLatency.SAFE_8MS)
        assertEquals(8, UsbAudioBridge.latencyProfile.value.ms)
    }

    @Test
    fun testAudioTransferMetricsTracking() {
        // Record 192 bytes transfer (1ms of 48kHz 16-bit stereo)
        UsbAudioBridge.recordAudioTransfer(192, recoveredJitter = false)
        assertEquals(192L, UsbAudioBridge.stats.value.totalBytesTransferred)
        assertEquals(0L, UsbAudioBridge.stats.value.packetsRecovered)
        assertTrue(UsbAudioBridge.stats.value.isStreaming)

        // Record another transfer with jitter packet recovered
        UsbAudioBridge.recordAudioTransfer(192, recoveredJitter = true)
        assertEquals(384L, UsbAudioBridge.stats.value.totalBytesTransferred)
        assertEquals(1L, UsbAudioBridge.stats.value.packetsRecovered)
    }
}
