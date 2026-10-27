package com.usbanywhere.audio

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates USB Audio Class 1/2 streaming for external gaming headsets and studio microphones.
 * Provides configurable jitter buffer smoothing (2ms, 4ms, 8ms) for low-latency voice chat.
 */
object UsbAudioBridge {

    private const val TAG = "UsbAudioBridge"

    enum class AudioBufferLatency(val ms: Int, val label: String) {
        ULTRA_LOW_2MS(2, "Ultra Low (2ms - Wired LAN)"),
        BALANCED_4MS(4, "Balanced (4ms - Wi-Fi 6)"),
        SAFE_8MS(8, "Safe Mesh (8ms - WAN / Tailscale)")
    }

    data class AudioStats(
        val isStreaming: Boolean = false,
        val sampleRate: Int = UsbAudioProfile.DEFAULT_SAMPLE_RATE_HZ,
        val bitDepth: Int = UsbAudioProfile.DEFAULT_BIT_DEPTH,
        val channels: Int = UsbAudioProfile.CHANNELS_STEREO,
        val totalBytesTransferred: Long = 0L,
        val packetsRecovered: Long = 0L,
        val activeHeadsetName: String? = null
    )

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _latencyProfile = MutableStateFlow(AudioBufferLatency.BALANCED_4MS)
    val latencyProfile: StateFlow<AudioBufferLatency> = _latencyProfile.asStateFlow()

    private val _stats = MutableStateFlow(AudioStats())
    val stats: StateFlow<AudioStats> = _stats.asStateFlow()

    private val bytesCounter = AtomicLong(0)
    private val recoveredCounter = AtomicLong(0)

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        try { Log.i(TAG, "USB Audio Bridge enabled set to: $enabled") } catch (ign: Exception) {}
    }

    fun setLatencyProfile(profile: AudioBufferLatency) {
        _latencyProfile.value = profile
        try { Log.i(TAG, "Audio buffer latency profile set to: ${profile.label}") } catch (ign: Exception) {}
    }

    fun recordAudioTransfer(bytes: Int, recoveredJitter: Boolean = false) {
        val total = bytesCounter.addAndGet(bytes.toLong())
        val rec = if (recoveredJitter) recoveredCounter.incrementAndGet() else recoveredCounter.get()
        _stats.value = _stats.value.copy(
            isStreaming = true,
            totalBytesTransferred = total,
            packetsRecovered = rec
        )
    }

    fun resetStats() {
        bytesCounter.set(0)
        recoveredCounter.set(0)
        _stats.value = AudioStats()
    }
}
