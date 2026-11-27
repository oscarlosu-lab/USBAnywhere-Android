package com.usbanywhere.gaming.sunshine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StreamProvider(val id: String) {
    SUNSHINE("sunshine"),
    APOLLO("apollo"),
    VIBEPOLLO("vibepollo"),
    VIBESHINE("vibeshine"),
    PARSEC("parsec"),
    STEAM_REMOTE_PLAY("steam_remote_play"),
    GENERIC("generic");

    companion object {
        fun fromQuery(raw: String?): StreamProvider {
            val clean = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.id == clean } ?: SUNSHINE
        }
    }
}

enum class DeviceHandoffPolicy {
    ALWAYS_LOCAL,
    ONLY_DURING_STREAM,
    ALWAYS_FORWARD
}

/**
 * Coordinates Sunshine/Moonlight/Apollo/Parsec/Steam streaming handoff state, local HTTP webhook server,
 * and gamepad quick-toggle button combo detection.
 */
object SunshineHandoffManager {

    private const val TAG = "SunshineHandoffManager"

    private val _currentMode = MutableStateFlow(HandoffMode.LOCAL_ANDROID_TV)
    val currentMode: StateFlow<HandoffMode> = _currentMode.asStateFlow()

    private val _activeProvider = MutableStateFlow<StreamProvider?>(null)
    val activeProvider: StateFlow<StreamProvider?> = _activeProvider.asStateFlow()

    private val _comboProgress = MutableStateFlow(0f)
    val comboProgress: StateFlow<Float> = _comboProgress.asStateFlow()

    private var appContext: Context? = null
    private var handoffServer: SunshineHandoffServer? = null

    val quickToggleDetector = QuickToggleDetector {
        val newMode = toggleMode()
        notifyModeChange(newMode)
    }

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        val configuredPort = com.usbanywhere.data.AppPreferences.getSunshinePort(context)
        handoffServer = SunshineHandoffServer(
            port = configuredPort,
            onModeChanged = { mode ->
                notifyModeChange(mode)
            },
            authTokenProvider = {
                appContext?.let { com.usbanywhere.data.AppPreferences.getSunshineToken(it) }.orEmpty()
            }
        ).also { it.start() }

        Log.i(TAG, "SunshineHandoffManager initialized with embedded HTTP server on port $configuredPort.")
    }

    fun setMode(mode: HandoffMode) {
        if (_currentMode.value != mode) {
            _currentMode.value = mode
            Log.i(TAG, "Handoff Mode transitioned to: ${mode.name}")
        }
        if (mode == HandoffMode.LOCAL_ANDROID_TV) {
            _activeProvider.value = null
        }
    }

    fun setActiveProvider(provider: StreamProvider?) {
        _activeProvider.value = provider
    }

    fun shouldForwardDevice(policy: DeviceHandoffPolicy, mode: HandoffMode = _currentMode.value): Boolean = when (policy) {
        DeviceHandoffPolicy.ALWAYS_LOCAL -> false
        DeviceHandoffPolicy.ALWAYS_FORWARD -> true
        DeviceHandoffPolicy.ONLY_DURING_STREAM -> mode == HandoffMode.STREAMING_PC
    }

    fun toggleMode(): HandoffMode {
        val next = _currentMode.value.toggled()
        _currentMode.value = next
        Log.i(TAG, "Handoff Mode toggled to: ${next.name}")
        return next
    }

    /**
     * Intercepts incoming gamepad key events to test for quick-toggle combo.
     */
    fun processKeyEvent(event: KeyEvent): Boolean {
        val consumed = quickToggleDetector.processKeyEvent(event)
        _comboProgress.value = quickToggleDetector.getComboProgress()
        return consumed
    }

    private fun notifyModeChange(mode: HandoffMode) {
        val ctx = appContext ?: return
        if (!com.usbanywhere.data.AppPreferences.isNotificationsEnabled(ctx)) {
            Log.d(TAG, "HUD Toasts disabled by user preference. Suppressing toast.")
            return
        }
        Handler(Looper.getMainLooper()).post {
            val msg = when (mode) {
                HandoffMode.LOCAL_ANDROID_TV -> "🎮 Local Android TV Control Restored"
                HandoffMode.STREAMING_PC -> "🚀 Gamepad Forwarded to Sunshine PC Streaming"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun shutdown() {
        handoffServer?.stop()
        handoffServer = null
        appContext = null
        quickToggleDetector.reset()
        _comboProgress.value = 0f
    }
}
