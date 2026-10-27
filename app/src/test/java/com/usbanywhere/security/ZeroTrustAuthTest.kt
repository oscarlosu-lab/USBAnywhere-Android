package com.usbanywhere.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ZeroTrustAuthTest {

    @Before
    fun setUp() {
        ZeroTrustAuthManager.clearAllAuthorizations()
        ZeroTrustAuthManager.setAuthEnforced(true)
    }

    @Test
    fun testPinFormatAndRandomness() {
        val pin1 = ZeroTrustAuthManager.activePin.value
        assertEquals(6, pin1.length)
        assertTrue(pin1.all { it.isDigit() })

        val pin2 = ZeroTrustAuthManager.refreshPin()
        assertEquals(6, pin2.length)
        assertTrue(pin2.all { it.isDigit() })
    }

    @Test
    fun testValidPinAuthenticatesPeer() {
        val activePin = ZeroTrustAuthManager.activePin.value
        val peerIp = "100.80.12.34"

        // Incorrect PIN fails
        val badResult = ZeroTrustAuthManager.validatePin("000000", peerIp)
        assertFalse(badResult)
        assertFalse(ZeroTrustAuthManager.authorizedPeers.value.contains(peerIp))

        // Correct PIN succeeds and whitelists peer
        val goodResult = ZeroTrustAuthManager.validatePin(activePin, peerIp)
        assertTrue(goodResult)
        assertTrue(ZeroTrustAuthManager.authorizedPeers.value.contains(peerIp))

        // Subsequent check succeeds even without PIN because peer is whitelisted
        assertTrue(ZeroTrustAuthManager.validatePin("", peerIp))
    }

    @Test
    fun testBruteForceLockout() {
        val peerIp = "192.168.1.99"
        val activePin = ZeroTrustAuthManager.activePin.value

        // 5 consecutive failed attempts
        for (i in 1..5) {
            ZeroTrustAuthManager.validatePin("999999", peerIp)
        }

        // Even if the 6th attempt provides the CORRECT PIN, it must be rejected due to lockout
        val lockedOutResult = ZeroTrustAuthManager.validatePin(activePin, peerIp)
        assertFalse(lockedOutResult)
    }

    @Test
    fun testAuthDisabledAllowsAll() {
        ZeroTrustAuthManager.setAuthEnforced(false)
        val peerIp = "192.168.1.200"

        // Any PIN or empty string succeeds when auth is disabled
        assertTrue(ZeroTrustAuthManager.validatePin("", peerIp))
        assertTrue(ZeroTrustAuthManager.validatePin("wrong", peerIp))
    }
}
