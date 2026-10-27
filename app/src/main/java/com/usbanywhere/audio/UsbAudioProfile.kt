package com.usbanywhere.audio

/**
 * Constants and descriptor identifiers for USB Audio Class 1.0 and 2.0 (UAC1/UAC2).
 */
object UsbAudioProfile {

    const val CLASS_AUDIO = 0x01

    const val SUBCLASS_UNDEFINED = 0x00
    const val SUBCLASS_AUDIOCONTROL = 0x01
    const val SUBCLASS_AUDIOSTREAMING = 0x02
    const val SUBCLASS_MIDISTREAMING = 0x03

    // Common standard formats
    const val DEFAULT_SAMPLE_RATE_HZ = 48000
    const val DEFAULT_BIT_DEPTH = 16
    const val CHANNELS_STEREO = 2
    const val CHANNELS_MONO = 1

    /**
     * Checks if a USB interface belongs to the USB Audio Class.
     */
    fun isAudioInterface(interfaceClass: Int): Boolean {
        return interfaceClass == CLASS_AUDIO
    }

    /**
     * Checks if an interface is an Audio Streaming endpoint (Playback or Mic recording).
     */
    fun isAudioStreaming(interfaceClass: Int, interfaceSubclass: Int): Boolean {
        return interfaceClass == CLASS_AUDIO && interfaceSubclass == SUBCLASS_AUDIOSTREAMING
    }
}
