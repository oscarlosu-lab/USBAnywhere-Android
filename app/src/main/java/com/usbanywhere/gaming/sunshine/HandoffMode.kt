package com.usbanywhere.gaming.sunshine

/**
 * Represents the current operational handoff mode of connected gamepads.
 *
 * - LOCAL_ANDROID_TV: Gamepad input drives the local Android TV / device UI directly.
 * - STREAMING_PC: Gamepad is forwarded over USB/IP to the remote host (Sunshine / Moonlight / PC),
 *                 protecting the local UI from unintended accidental input while streaming.
 */
enum class HandoffMode(
    val title: String,
    val description: String,
    val badgeText: String
) {
    LOCAL_ANDROID_TV(
        title = "Local Android TV",
        description = "Gamepad controls the local device and app navigation.",
        badgeText = "LOCAL TV"
    ),
    STREAMING_PC(
        title = "Streaming PC (Sunshine)",
        description = "Gamepad is forwarded to host PC via USB/IP. Local input suppressed.",
        badgeText = "STREAMING PC"
    );

    fun toggled(): HandoffMode = when (this) {
        LOCAL_ANDROID_TV -> STREAMING_PC
        STREAMING_PC -> LOCAL_ANDROID_TV
    }
}
