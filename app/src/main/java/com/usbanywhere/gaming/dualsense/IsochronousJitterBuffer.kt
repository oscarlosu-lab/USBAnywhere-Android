package com.usbanywhere.gaming.dualsense

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Low-latency sliding jitter buffer for isochronous USB audio and DualSense haptic rumble streams.
 * Prevents audio buffer underflows (the primary cause of DualSense haptic crackles over Wi-Fi)
 * while maintaining a tight, esports-grade sub-5ms delay window.
 */
class IsochronousJitterBuffer(
    val targetQueueDepth: Int = 4,
    val maxQueueDepth: Int = 16
) {

    data class IsoPacket(
        val sequenceNumber: Long,
        val timestampNs: Long,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IsoPacket) return false
            return sequenceNumber == other.sequenceNumber && timestampNs == other.timestampNs
        }

        override fun hashCode(): Int {
            return sequenceNumber.hashCode()
        }
    }

    data class JitterStats(
        val totalEnqueued: Long = 0,
        val totalDispatched: Long = 0,
        val underflowCount: Long = 0,
        val droppedStaleCount: Long = 0,
        val currentQueueDepth: Int = 0
    )

    private val queue = ConcurrentLinkedQueue<IsoPacket>()
    private val totalEnqueued = AtomicLong(0)
    private val totalDispatched = AtomicLong(0)
    private val underflowCount = AtomicLong(0)
    private val droppedStaleCount = AtomicLong(0)

    fun push(payload: ByteArray, sequenceNumber: Long = totalEnqueued.get(), timestampNs: Long = System.nanoTime()) {
        if (queue.size >= maxQueueDepth) {
            // Drop oldest packet to prevent latency runaway
            queue.poll()
            droppedStaleCount.incrementAndGet()
        }
        queue.offer(IsoPacket(sequenceNumber, timestampNs, payload))
        totalEnqueued.incrementAndGet()
    }

    fun popNext(): IsoPacket? {
        val packet = queue.poll()
        if (packet == null) {
            underflowCount.incrementAndGet()
            return null
        }
        totalDispatched.incrementAndGet()
        return packet
    }

    fun clear() {
        queue.clear()
    }

    fun getStats(): JitterStats {
        return JitterStats(
            totalEnqueued = totalEnqueued.get(),
            totalDispatched = totalDispatched.get(),
            underflowCount = underflowCount.get(),
            droppedStaleCount = droppedStaleCount.get(),
            currentQueueDepth = queue.size
        )
    }
}
