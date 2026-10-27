package com.usbanywhere.gamepad

data class GamepadState(
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val dpadUp: Boolean = false,
    val dpadDown: Boolean = false,
    val dpadLeft: Boolean = false,
    val dpadRight: Boolean = false,
    val btnA: Boolean = false,
    val btnB: Boolean = false,
    val btnX: Boolean = false,
    val btnY: Boolean = false,
    val btnL1: Boolean = false,
    val btnR1: Boolean = false,
    val btnL3: Boolean = false,
    val btnR3: Boolean = false,
    val btnSelect: Boolean = false,
    val btnStart: Boolean = false,
    val btnGuide: Boolean = false,
    val deviceName: String = "No Controller Detected",
    val isConnected: Boolean = false,
    val timestampNs: Long = 0L
)

data class PollingRateStats(
    val currentHz: Double = 0.0,
    val avgHz: Double = 0.0,
    val minHz: Double = 0.0,
    val maxHz: Double = 0.0,
    val jitterMs: Double = 0.0,
    val totalReports: Long = 0L
)
