package com.usbanywhere.client.bridge

import android.util.Log
import com.usbanywhere.gamepad.GamepadInputManager

data class GamepadInputState(
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,
    val buttonA: Boolean = false,
    val buttonB: Boolean = false,
    val buttonX: Boolean = false,
    val buttonY: Boolean = false,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val dpadUp: Boolean = false,
    val dpadDown: Boolean = false,
    val dpadLeft: Boolean = false,
    val dpadRight: Boolean = false
)

class VirtualInputBridge {

    companion object {
        private const val TAG = "VirtualInputBridge"
    }

    /**
     * Decodes standard HID Gamepad / DualSense / Xbox report buffer and feeds GamepadInputManager.
     */
    fun decodeHidReport(report: ByteArray): GamepadInputState {
        if (report.size < 6) return GamepadInputState()

        try {
            // Forward raw report to real-time Gamepad Tester & Polling Tracker
            GamepadInputManager.handleRemoteReport(report)

            val lx = ((report[0].toInt() and 0xFF) - 128) / 128f
            val ly = ((report[1].toInt() and 0xFF) - 128) / 128f
            val rx = ((report[2].toInt() and 0xFF) - 128) / 128f
            val ry = ((report[3].toInt() and 0xFF) - 128) / 128f

            val buttons = report[4].toInt() and 0xFF
            val btnA = (buttons and 0x01) != 0
            val btnB = (buttons and 0x02) != 0
            val btnX = (buttons and 0x04) != 0
            val btnY = (buttons and 0x08) != 0

            return GamepadInputState(
                leftStickX = lx,
                leftStickY = ly,
                rightStickX = rx,
                rightStickY = ry,
                buttonA = btnA,
                buttonB = btnB,
                buttonX = btnX,
                buttonY = btnY
            )
        } catch (e: Exception) {
            Log.d(TAG, "Error decoding HID report: ${e.message}")
            return GamepadInputState()
        }
    }
}
