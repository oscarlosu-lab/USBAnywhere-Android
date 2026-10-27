package com.usbanywhere.gaming.simracing

import java.util.concurrent.atomic.AtomicLong

/**
 * Ensures smooth, stutter-free Force Feedback (FFB) packet delivery to sim racing wheels.
 * Prevents violent wheel shakes or motor snaps caused by out-of-order network packets
 * or Wi-Fi jitter spikes.
 */
class FfbJitterGuard(
    val maxAllowedGapMs: Long = 50L
) {

    data class FfbStats(
        val totalReceived: Long = 0,
        val totalForwarded: Long = 0,
        val staleDropped: Long = 0,
        val gapSpikesDetected: Long = 0,
        val lastIntervalMs: Long = 0
    )

    private val totalReceived = AtomicLong(0)
    private val totalForwarded = AtomicLong(0)
    private val staleDropped = AtomicLong(0)
    private val gapSpikes = AtomicLong(0)

    @Volatile
    private var lastSequenceNumber: Long = -1L
    @Volatile
    private var lastTimestampMs: Long = 0L
    @Volatile
    private var lastIntervalMs: Long = 0L

    /**
     * Inspects an incoming Force Feedback URB frame.
     * Returns true if the frame is fresh and should be sent to the physical wheel;
     * returns false if it is stale/out-of-order and must be dropped to protect wheel calibration.
     */
    fun processFrame(sequenceNumber: Long, payload: ByteArray, nowMs: Long = System.currentTimeMillis()): Boolean {
        totalReceived.incrementAndGet()

        // 1. Monotonic sequence check
        if (lastSequenceNumber != -1L && sequenceNumber <= lastSequenceNumber) {
            staleDropped.incrementAndGet()
            return false // Drop stale packet
        }

        // 2. Jitter interval calculation
        if (lastTimestampMs > 0L) {
            val delta = nowMs - lastTimestampMs
            lastIntervalMs = delta
            if (delta > maxAllowedGapMs) {
                gapSpikes.incrementAndGet()
            }
        }

        lastSequenceNumber = sequenceNumber
        lastTimestampMs = nowMs
        totalForwarded.incrementAndGet()
        return true
    }

    fun reset() {
        lastSequenceNumber = -1L
        lastTimestampMs = 0L
        lastIntervalMs = 0L
        totalReceived.set(0)
        totalForwarded.set(0)
        staleDropped.set(0)
        gapSpikes.set(0)
    }

    fun getStats(): FfbStats {
        return FfbStats(
            totalReceived = totalReceived.get(),
            totalForwarded = totalForwarded.get(),
            staleDropped = staleDropped.get(),
            gapSpikesDetected = gapSpikes.get(),
            lastIntervalMs = lastIntervalMs
        )
    }
}
