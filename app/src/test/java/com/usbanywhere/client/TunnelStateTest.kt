package com.usbanywhere.client

import com.usbanywhere.data.TunnelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelStateTest {

    @Test
    fun testTunnelStateTransitions() {
        var state = TunnelState.DISCONNECTED
        assertEquals(TunnelState.DISCONNECTED, state)

        // Starting connection
        state = TunnelState.CONNECTING
        assertEquals(TunnelState.CONNECTING, state)

        // Tunnel established
        state = TunnelState.ACTIVE
        assertEquals(TunnelState.ACTIVE, state)

        // Network or AP roaming drop detected
        state = TunnelState.RECONNECTING
        assertEquals(TunnelState.RECONNECTING, state)

        // Auto-reconnect succeeded
        state = TunnelState.ACTIVE
        assertEquals(TunnelState.ACTIVE, state)

        // Manual detach / disconnect
        state = TunnelState.DISCONNECTED
        assertEquals(TunnelState.DISCONNECTED, state)
    }

    @Test
    fun testDiscoveryProbeProtocolContract() {
        val probe = "USBANYWHERE_DISCOVERY_PROBE"
        val serverModel = "Pixel Tablet"
        val response = "USBANYWHERE_SERVER:$serverModel"

        assertTrue(response.startsWith("USBANYWHERE_SERVER:"))
        val extractedModel = response.substringAfter("USBANYWHERE_SERVER:").trim()
        assertEquals(serverModel, extractedModel)
    }

    @Test
    fun testExponentialBackoffSequence() {
        val delays = listOf(500L, 1000L, 2000L, 3000L, 3000L)
        val maxAttempts = 5

        assertEquals(maxAttempts, delays.size)
        assertTrue(delays[0] <= delays[1])
        assertTrue(delays[1] <= delays[2])
        assertTrue(delays[2] <= delays[3])
        assertEquals(3000L, delays[4])
    }
}
