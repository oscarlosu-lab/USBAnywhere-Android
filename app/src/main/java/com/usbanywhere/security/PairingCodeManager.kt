package com.usbanywhere.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/**
 * Ephemeral 6-digit pairing code manager with 5-minute TTL and 5-attempt lockout.
 */
object PairingCodeManager {

    private const val TTL_MS = 5 * 60 * 1000L
    private const val MAX_ATTEMPTS = 5
    private val random = SecureRandom()
    private val lock = Any()

    private var activeCode: String = ""
    private var expiresAtMs: Long = 0L
    private var failedAttempts: Int = 0
    private var lockedUntilMs: Long = 0L

    data class VerifyResult(
        val success: Boolean,
        val controlToken: String = "",
        val message: String = ""
    )

    fun createCode(nowMs: Long = System.currentTimeMillis()): String = synchronized(lock) {
        activeCode = String.format(Locale.US, "%06d", random.nextInt(1_000_000))
        expiresAtMs = nowMs + TTL_MS
        failedAttempts = 0
        lockedUntilMs = 0L
        activeCode
    }

    fun verifyCode(candidate: String, nowMs: Long = System.currentTimeMillis()): VerifyResult = synchronized(lock) {
        val clean = candidate.filter { it.isDigit() }
        if (nowMs < lockedUntilMs) {
            return@synchronized VerifyResult(false, message = "Too many failed pairing attempts. Locked.")
        }
        if (activeCode.isEmpty() || nowMs > expiresAtMs) {
            return@synchronized VerifyResult(false, message = "No active pairing code or code expired.")
        }
        val match = clean.length == 6 && MessageDigest.isEqual(
            clean.toByteArray(StandardCharsets.UTF_8),
            activeCode.toByteArray(StandardCharsets.UTF_8)
        )
        if (!match) {
            failedAttempts++
            if (failedAttempts >= MAX_ATTEMPTS) {
                lockedUntilMs = nowMs + TTL_MS
                activeCode = ""
            }
            return@synchronized VerifyResult(false, message = "Invalid pairing code.")
        }
        activeCode = ""
        failedAttempts = 0
        val tokenBytes = ByteArray(16)
        random.nextBytes(tokenBytes)
        val hexToken = tokenBytes.joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xFF) }
        VerifyResult(true, controlToken = hexToken, message = "Paired")
    }

    internal fun resetForTests() = synchronized(lock) {
        activeCode = ""
        expiresAtMs = 0L
        failedAttempts = 0
        lockedUntilMs = 0L
    }
}
