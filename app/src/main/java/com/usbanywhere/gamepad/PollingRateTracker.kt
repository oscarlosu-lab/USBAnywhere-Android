package com.usbanywhere.gamepad

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PollingRateTracker(private val windowSize: Int = 64) {

    private var lastTimestampNs: Long = 0L
    private val deltaBuffer = LongArray(windowSize)
    private var bufferIndex = 0
    private var bufferCount = 0

    private val reportCount = AtomicLong(0L)
    private var peakHz = 0.0
    private var lowestHz = Double.MAX_VALUE

    @Synchronized
    fun recordEvent(timestampNs: Long = System.nanoTime()): PollingRateStats {
        val count = reportCount.incrementAndGet()

        if (lastTimestampNs == 0L) {
            lastTimestampNs = timestampNs
            return PollingRateStats(totalReports = count)
        }

        val deltaNs = timestampNs - lastTimestampNs
        lastTimestampNs = timestampNs

        // If gap is too large (> 500ms), user probably stopped moving; reset interval
        if (deltaNs <= 0 || deltaNs > 500_000_000L) {
            return getStats()
        }

        deltaBuffer[bufferIndex] = deltaNs
        bufferIndex = (bufferIndex + 1) % windowSize
        if (bufferCount < windowSize) {
            bufferCount++
        }

        val instantHz = 1_000_000_000.0 / deltaNs
        if (instantHz in 10.0..10_000.0) {
            peakHz = max(peakHz, instantHz)
            lowestHz = min(lowestHz, instantHz)
        }

        return getStats()
    }

    @Synchronized
    fun getStats(): PollingRateStats {
        if (bufferCount == 0) {
            return PollingRateStats(totalReports = reportCount.get())
        }

        var sumDelta = 0.0
        for (i in 0 until bufferCount) {
            sumDelta += deltaBuffer[i]
        }
        val avgDeltaNs = sumDelta / bufferCount
        val avgHz = if (avgDeltaNs > 0) 1_000_000_000.0 / avgDeltaNs else 0.0

        // Calculate jitter (standard deviation in ms)
        var varianceSum = 0.0
        for (i in 0 until bufferCount) {
            val diff = (deltaBuffer[i] - avgDeltaNs)
            varianceSum += diff * diff
        }
        val stdDevNs = sqrt(varianceSum / bufferCount)
        val jitterMs = stdDevNs / 1_000_000.0

        val lastDelta = if (bufferCount > 0) {
            val prevIdx = if (bufferIndex == 0) windowSize - 1 else bufferIndex - 1
            deltaBuffer[prevIdx]
        } else 0L

        val currentHz = if (lastDelta > 0) 1_000_000_000.0 / lastDelta else avgHz
        val displayMinHz = if (lowestHz == Double.MAX_VALUE) 0.0 else lowestHz

        return PollingRateStats(
            currentHz = currentHz,
            avgHz = avgHz,
            minHz = displayMinHz,
            maxHz = peakHz,
            jitterMs = jitterMs,
            totalReports = reportCount.get()
        )
    }

    @Synchronized
    fun reset() {
        lastTimestampNs = 0L
        bufferIndex = 0
        bufferCount = 0
        peakHz = 0.0
        lowestHz = Double.MAX_VALUE
        reportCount.set(0L)
    }
}
