package com.usbanywhere.gamepad

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.usbanywhere.gaming.dualsense.DualSenseProfile
import com.usbanywhere.gaming.multiplayer.MultiplayerHubManager
import com.usbanywhere.gaming.simracing.SimRacingProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

object GamepadInputManager {

    private val _gamepadState = MutableStateFlow(GamepadState())
    val gamepadState: StateFlow<GamepadState> = _gamepadState.asStateFlow()

    private val tracker = PollingRateTracker(windowSize = 64)
    private val _pollingStats = MutableStateFlow(PollingRateStats())
    val pollingStats: StateFlow<PollingRateStats> = _pollingStats.asStateFlow()

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _activeProfile = MutableStateFlow("Standard Esports HID Profile")
    val activeProfile: StateFlow<String> = _activeProfile.asStateFlow()

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun handleMotionEvent(event: MotionEvent): Boolean {
        val isGamepad = (event.source and InputDevice.SOURCE_JOYSTICK != 0) ||
                        (event.source and InputDevice.SOURCE_GAMEPAD != 0)
        if (!isGamepad) return false

        val now = System.nanoTime()
        val stats = tracker.recordEvent(now)
        _pollingStats.value = stats

        val dev = event.device
        val devName = dev?.name ?: "Gamepad"
        val vid = dev?.vendorId ?: 0
        val pid = dev?.productId ?: 0
        val devKey = dev?.descriptor?.ifEmpty { null } ?: "$vid:$pid:$devName"

        MultiplayerHubManager.registerDevice(devKey, devName)
        updateActiveProfile(vid, pid, devName)

        val lx = event.getAxisValue(MotionEvent.AXIS_X)
        val ly = event.getAxisValue(MotionEvent.AXIS_Y)
        val rx = event.getAxisValue(MotionEvent.AXIS_Z)
        val ry = event.getAxisValue(MotionEvent.AXIS_RZ)

        var lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
        if (lt == 0f) lt = event.getAxisValue(MotionEvent.AXIS_BRAKE)

        var rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
        if (rt == 0f) rt = event.getAxisValue(MotionEvent.AXIS_GAS)

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val current = _gamepadState.value
        _gamepadState.value = current.copy(
            leftStickX = lx,
            leftStickY = ly,
            rightStickX = rx,
            rightStickY = ry,
            leftTrigger = lt.coerceIn(0f, 1f),
            rightTrigger = rt.coerceIn(0f, 1f),
            dpadLeft = current.dpadLeft || hatX < -0.5f,
            dpadRight = current.dpadRight || hatX > 0.5f,
            dpadUp = current.dpadUp || hatY < -0.5f,
            dpadDown = current.dpadDown || hatY > 0.5f,
            deviceName = devName,
            isConnected = true,
            timestampNs = now
        )
        return true
    }

    private fun updateActiveProfile(vid: Int, pid: Int, name: String) {
        _activeProfile.value = when {
            DualSenseProfile.isDualSense(vid, pid) || name.contains("DualSense", ignoreCase = true) ->
                "Sony DualSense (Haptics Jitter Buffer Active)"
            DualSenseProfile.isSonyController(vid, pid) ->
                "Sony DualShock 4 (Enhanced Audio Active)"
            SimRacingProfile.isRacingWheel(vid, pid) || name.contains("Wheel", ignoreCase = true) ->
                "Sim Racing Wheel (FFB Jitter-Guard Active)"
            else -> "Standard Esports HID Profile"
        }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        val isGamepadKey = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
            else -> false
        }
        if (!isGamepadKey) return false

        val isPressed = event.action == KeyEvent.ACTION_DOWN
        val now = System.nanoTime()
        val stats = tracker.recordEvent(now)
        _pollingStats.value = stats

        val dev = event.device
        val devName = dev?.name ?: "Gamepad"
        val vid = dev?.vendorId ?: 0
        val pid = dev?.productId ?: 0
        val devKey = dev?.descriptor?.ifEmpty { null } ?: "$vid:$pid:$devName"

        MultiplayerHubManager.registerDevice(devKey, devName)
        updateActiveProfile(vid, pid, devName)

        val cur = _gamepadState.value

        _gamepadState.value = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> cur.copy(btnA = isPressed)
            KeyEvent.KEYCODE_BUTTON_B -> cur.copy(btnB = isPressed)
            KeyEvent.KEYCODE_BUTTON_X -> cur.copy(btnX = isPressed)
            KeyEvent.KEYCODE_BUTTON_Y -> cur.copy(btnY = isPressed)
            KeyEvent.KEYCODE_BUTTON_L1 -> cur.copy(btnL1 = isPressed)
            KeyEvent.KEYCODE_BUTTON_R1 -> cur.copy(btnR1 = isPressed)
            KeyEvent.KEYCODE_BUTTON_THUMBL -> cur.copy(btnL3 = isPressed)
            KeyEvent.KEYCODE_BUTTON_THUMBR -> cur.copy(btnR3 = isPressed)
            KeyEvent.KEYCODE_BUTTON_START -> cur.copy(btnStart = isPressed)
            KeyEvent.KEYCODE_BUTTON_SELECT -> cur.copy(btnSelect = isPressed)
            KeyEvent.KEYCODE_BUTTON_MODE -> cur.copy(btnGuide = isPressed)
            KeyEvent.KEYCODE_DPAD_UP -> cur.copy(dpadUp = isPressed)
            KeyEvent.KEYCODE_DPAD_DOWN -> cur.copy(dpadDown = isPressed)
            KeyEvent.KEYCODE_DPAD_LEFT -> cur.copy(dpadLeft = isPressed)
            KeyEvent.KEYCODE_DPAD_RIGHT -> cur.copy(dpadRight = isPressed)
            else -> cur
        }.copy(
            deviceName = devName,
            isConnected = true,
            timestampNs = now
        )

        return true
    }

    fun handleRemoteReport(report: ByteArray) {
        if (report.size < 6) return
        val now = System.nanoTime()
        val stats = tracker.recordEvent(now)
        _pollingStats.value = stats

        try {
            val lx = ((report[0].toInt() and 0xFF) - 128) / 128f
            val ly = ((report[1].toInt() and 0xFF) - 128) / 128f
            val rx = ((report[2].toInt() and 0xFF) - 128) / 128f
            val ry = ((report[3].toInt() and 0xFF) - 128) / 128f

            val buttons = report[4].toInt() and 0xFF
            val btnA = (buttons and 0x01) != 0
            val btnB = (buttons and 0x02) != 0
            val btnX = (buttons and 0x04) != 0
            val btnY = (buttons and 0x08) != 0
            val btnL1 = (buttons and 0x10) != 0
            val btnR1 = (buttons and 0x20) != 0

            _gamepadState.value = GamepadState(
                leftStickX = lx,
                leftStickY = ly,
                rightStickX = rx,
                rightStickY = ry,
                btnA = btnA,
                btnB = btnB,
                btnX = btnX,
                btnY = btnY,
                btnL1 = btnL1,
                btnR1 = btnR1,
                deviceName = "Remote USB/IP Gamepad",
                isConnected = true,
                timestampNs = now
            )
        } catch (_: Exception) {}
    }

    fun toggleSimulation() {
        if (_isSimulating.value) {
            stopSimulation()
        } else {
            startSimulation(targetHz = 1000)
        }
    }

    fun startSimulation(targetHz: Int = 1000) {
        stopSimulation()
        _isSimulating.value = true
        tracker.reset()

        val intervalNs = 1_000_000_000L / targetHz

        simulationJob = scope.launch {
            var angle = 0.0
            var tick = 0L

            while (isActive && _isSimulating.value) {
                val startLoop = System.nanoTime()
                angle += 0.05
                tick++

                val lx = cos(angle).toFloat()
                val ly = sin(angle).toFloat()
                val rx = cos(angle * 1.5).toFloat()
                val ry = sin(angle * 1.5).toFloat()

                val triggerVal = ((sin(angle * 0.5) + 1.0) / 2.0).toFloat()

                val now = System.nanoTime()
                val stats = tracker.recordEvent(now)
                _pollingStats.value = stats

                _gamepadState.value = GamepadState(
                    leftStickX = lx,
                    leftStickY = ly,
                    rightStickX = rx,
                    rightStickY = ry,
                    leftTrigger = triggerVal,
                    rightTrigger = 1f - triggerVal,
                    btnA = (tick % 40) < 15,
                    btnB = (tick % 60) < 20,
                    btnX = (tick % 80) < 25,
                    btnY = (tick % 100) < 30,
                    dpadUp = (tick % 120) < 20,
                    dpadDown = (tick % 120) in 30..50,
                    dpadLeft = (tick % 120) in 60..80,
                    dpadRight = (tick % 120) in 90..110,
                    deviceName = "Synthetic Esports Generator ($targetHz Hz)",
                    isConnected = true,
                    timestampNs = now
                )

                val elapsed = System.nanoTime() - startLoop
                val sleepNs = intervalNs - elapsed
                if (sleepNs > 1_000_000L) {
                    delay(sleepNs / 1_000_000L)
                } else if (sleepNs > 0) {
                    // Busy-wait for sub-millisecond precision
                    val target = System.nanoTime() + sleepNs
                    while (System.nanoTime() < target) {
                        // spin
                    }
                }
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _isSimulating.value = false
        _gamepadState.value = _gamepadState.value.copy(
            deviceName = "No Controller Detected",
            isConnected = false
        )
    }

    fun resetStats() {
        tracker.reset()
        _pollingStats.value = PollingRateStats()
    }
}
