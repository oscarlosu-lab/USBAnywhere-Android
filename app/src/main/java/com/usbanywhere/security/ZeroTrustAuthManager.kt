package com.usbanywhere.security

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Zero-Trust Mutual Authentication, dynamic 6-digit cryptographic PINs,
 * and authorized peer whitelisting for USB/IP tunnels traversing WAN / Internet.
 */
object ZeroTrustAuthManager {

    private const val TAG = "ZeroTrustAuthManager"
    private const val MAX_FAILED_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_MS = 60_000L

    private val secureRandom = SecureRandom()

    private val _isAuthEnforced = MutableStateFlow(true)
    val isAuthEnforced: StateFlow<Boolean> = _isAuthEnforced.asStateFlow()

    private val _activePin = MutableStateFlow(generateRandomPin())
    val activePin: StateFlow<String> = _activePin.asStateFlow()

    private val _authorizedPeers = MutableStateFlow<Set<String>>(emptySet())
    val authorizedPeers: StateFlow<Set<String>> = _authorizedPeers.asStateFlow()

    private val failedAttempts = ConcurrentHashMap<String, Int>()
    private val lockoutTimestamps = ConcurrentHashMap<String, Long>()

    /**
     * Generates a new cryptographically secure 6-digit PIN.
     */
    fun refreshPin(): String {
        val newPin = generateRandomPin()
        _activePin.value = newPin
        try { Log.i(TAG, "Generated fresh Zero-Trust pairing PIN") } catch (e: Exception) {}
        return newPin
    }

    fun setAuthEnforced(enforced: Boolean) {
        _isAuthEnforced.value = enforced
        try { Log.i(TAG, "Zero-Trust PIN enforcement set to: $enforced") } catch (e: Exception) {}
    }

    /**
     * Validates a PIN presented by a client.
     */
    fun validatePin(providedPin: String, peerAddress: String): Boolean {
        // If auth is not enforced, allow immediately
        if (!_isAuthEnforced.value) return true

        // Check if peer is already authorized
        if (_authorizedPeers.value.contains(peerAddress)) {
            return true
        }

        // Check lockout
        val now = System.currentTimeMillis()
        val lockoutUntil = lockoutTimestamps[peerAddress] ?: 0L
        if (now < lockoutUntil) {
            try { Log.w(TAG, "Rejecting connection from locked out peer: $peerAddress") } catch (e: Exception) {}
            return false
        }

        val cleanPin = providedPin.trim()
        val currentPin = _activePin.value.trim()

        if (cleanPin == currentPin && cleanPin.isNotEmpty()) {
            failedAttempts.remove(peerAddress)
            lockoutTimestamps.remove(peerAddress)
            authorizePeer(peerAddress)
            try { Log.i(TAG, "Peer $peerAddress successfully authenticated with PIN") } catch (e: Exception) {}
            return true
        } else {
            val count = (failedAttempts[peerAddress] ?: 0) + 1
            failedAttempts[peerAddress] = count
            if (count >= MAX_FAILED_ATTEMPTS) {
                lockoutTimestamps[peerAddress] = now + LOCKOUT_DURATION_MS
                try { Log.w(TAG, "Peer $peerAddress exceeded $MAX_FAILED_ATTEMPTS failed attempts. Locked out for 60s.") } catch (e: Exception) {}
            }
            try { Log.w(TAG, "Failed auth attempt from $peerAddress (Attempt $count)") } catch (e: Exception) {}
            return false
        }
    }

    fun authorizePeer(peerAddress: String) {
        val current = _authorizedPeers.value.toMutableSet()
        current.add(peerAddress)
        _authorizedPeers.value = current
    }

    fun revokePeer(peerAddress: String) {
        val current = _authorizedPeers.value.toMutableSet()
        current.remove(peerAddress)
        _authorizedPeers.value = current
    }

    fun clearAllAuthorizations() {
        _authorizedPeers.value = emptySet()
        failedAttempts.clear()
        lockoutTimestamps.clear()
    }

    private fun generateRandomPin(): String {
        val number = secureRandom.nextInt(1_000_000)
        return String.format("%06d", number)
    }
}
