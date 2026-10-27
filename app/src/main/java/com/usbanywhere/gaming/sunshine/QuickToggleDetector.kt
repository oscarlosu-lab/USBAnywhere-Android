package com.usbanywhere.gaming.sunshine

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

/**
 * Detects prolonged gamepad button combinations (e.g. Guide + Select held for 2.5 seconds)
 * to instantly toggle between Local Android TV navigation and PC USB/IP Streaming
 * without touching a keyboard or mouse.
 */
class QuickToggleDetector(
    private val holdDurationMs: Long = DEFAULT_HOLD_DURATION_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val onToggleTriggered: () -> Unit
) {

    companion object {
        const val DEFAULT_HOLD_DURATION_MS = 2500L
        private const val TAG = "QuickToggleDetector"
    }

    private var isGuidePressed = false
    private var isSelectPressed = false
    private var isStartPressed = false
    private var isL1Pressed = false
    private var isR1Pressed = false

    private var comboStartTimeMs: Long = 0L
    private var isComboActive: Boolean = false
    private var hasTriggeredForCurrentHold: Boolean = false

    /**
     * Inspects a KeyEvent. Returns true if the key event was consumed by the combo detector.
     */
    fun processKeyEvent(event: KeyEvent): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        return processKey(event.keyCode, isDown)
    }

    /**
     * Inspects a key code and press state directly.
     */
    fun processKey(keyCode: Int, isDown: Boolean): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_MODE -> isGuidePressed = isDown
            KeyEvent.KEYCODE_BUTTON_SELECT -> isSelectPressed = isDown
            KeyEvent.KEYCODE_BUTTON_START -> isStartPressed = isDown
            KeyEvent.KEYCODE_BUTTON_L1 -> isL1Pressed = isDown
            KeyEvent.KEYCODE_BUTTON_R1 -> isR1Pressed = isDown
            else -> return false
        }

        val matchesGuideSelect = isGuidePressed && isSelectPressed
        val matchesBackStart = isSelectPressed && isStartPressed
        val matchesBumperSelect = isL1Pressed && isR1Pressed && isSelectPressed

        val isComboConditionMet = matchesGuideSelect || matchesBackStart || matchesBumperSelect

        if (isComboConditionMet) {
            val now = timeProvider()
            if (!isComboActive) {
                isComboActive = true
                comboStartTimeMs = now
                hasTriggeredForCurrentHold = false
                try {
                    Log.d(TAG, "Quick-toggle combo initiated. Hold for ${holdDurationMs}ms to toggle...")
                } catch (ignored: Exception) {}
            } else if (!hasTriggeredForCurrentHold) {
                val elapsed = now - comboStartTimeMs
                if (elapsed >= holdDurationMs) {
                    hasTriggeredForCurrentHold = true
                    try {
                        Log.i(TAG, "Quick-toggle combo TRIGGERED ($elapsed ms held)!")
                    } catch (ignored: Exception) {}
                    onToggleTriggered()
                    return true
                }
            }
        } else {
            isComboActive = false
            hasTriggeredForCurrentHold = false
            comboStartTimeMs = 0L
        }

        return false
    }

    /**
     * Returns current progress of the hold combo from 0.0f to 1.0f.
     */
    fun getComboProgress(): Float {
        if (!isComboActive || hasTriggeredForCurrentHold) return 0f
        val elapsed = timeProvider() - comboStartTimeMs
        return (elapsed.toFloat() / holdDurationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun reset() {
        isGuidePressed = false
        isSelectPressed = false
        isStartPressed = false
        isL1Pressed = false
        isR1Pressed = false
        isComboActive = false
        hasTriggeredForCurrentHold = false
        comboStartTimeMs = 0L
    }
}
