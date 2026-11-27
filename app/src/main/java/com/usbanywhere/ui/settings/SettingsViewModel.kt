package com.usbanywhere.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.usbanywhere.data.AppPreferences
import com.usbanywhere.update.AndroidUpdateChecker
import com.usbanywhere.update.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val currentVersion: String) : UpdateUiState
    data class Available(val info: UpdateCheckResult) : UpdateUiState
    data class Downloading(val progress: Int) : UpdateUiState
    data class ReadyToInstall(val version: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _isEsports8000HzEnabled = MutableStateFlow(AppPreferences.isEsports8000Hz(context))
    val isEsports8000HzEnabled: StateFlow<Boolean> = _isEsports8000HzEnabled.asStateFlow()

    private val _isKeepAwakeEnabled = MutableStateFlow(AppPreferences.isKeepAwake(context))
    val isKeepAwakeEnabled: StateFlow<Boolean> = _isKeepAwakeEnabled.asStateFlow()

    private val _isAutostartOnUsbEnabled = MutableStateFlow(AppPreferences.isAutostartOnUsb(context))
    val isAutostartOnUsbEnabled: StateFlow<Boolean> = _isAutostartOnUsbEnabled.asStateFlow()

    private val _isAutostartOnBootEnabled = MutableStateFlow(AppPreferences.isAutostartOnBoot(context))
    val isAutostartOnBootEnabled: StateFlow<Boolean> = _isAutostartOnBootEnabled.asStateFlow()

    private val _isKioskModeEnabled = MutableStateFlow(AppPreferences.isKioskMode(context))
    val isKioskModeEnabled: StateFlow<Boolean> = _isKioskModeEnabled.asStateFlow()

    private val _serverPort = MutableStateFlow(AppPreferences.getServerPort(context))
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _sunshinePort = MutableStateFlow(AppPreferences.getSunshinePort(context))
    val sunshinePort: StateFlow<Int> = _sunshinePort.asStateFlow()

    private val _isNotificationsEnabled = MutableStateFlow(AppPreferences.isNotificationsEnabled(context))
    val isNotificationsEnabled: StateFlow<Boolean> = _isNotificationsEnabled.asStateFlow()

    private val _isHapticFeedbackEnabled = MutableStateFlow(AppPreferences.isHapticFeedbackEnabled(context))
    val isHapticFeedbackEnabled: StateFlow<Boolean> = _isHapticFeedbackEnabled.asStateFlow()

    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable.asStateFlow()

    private val _bindAddress = MutableStateFlow(AppPreferences.getBindAddress(context))
    val bindAddress: StateFlow<String> = _bindAddress.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val logList = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    init {
        checkRootAccess()
        addLog("USBAnywhere initialized.")
        addLog("Non-Intrusive Engine: Boot=${_isAutostartOnBootEnabled.value}, USB=${_isAutostartOnUsbEnabled.value}")
        addLog("Ports: USB/IP=${_serverPort.value}, Sunshine=${_sunshinePort.value}")
        addLog("Low-Latency WSK / 8000Hz Engine: ${if (_isEsports8000HzEnabled.value) "Active" else "Standard"}")
        addLog("Root VHCI Mode: ${if (_isRootAvailable.value) "Available (Kernel)" else "Userspace (No-Root)"}")
    }

    private fun checkRootAccess() {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su"
        )
        val hasSu = paths.any { File(it).exists() }
        _isRootAvailable.value = hasSu
    }

    fun toggleEsports8000Hz() {
        val next = !_isEsports8000HzEnabled.value
        _isEsports8000HzEnabled.value = next
        AppPreferences.setEsports8000Hz(context, next)
        addLog("Esports 8000Hz mode: ${if (next) "Enabled (TCP_NODELAY + Urgent Priority)" else "Disabled"}")
    }

    fun toggleKeepAwake() {
        val next = !_isKeepAwakeEnabled.value
        _isKeepAwakeEnabled.value = next
        AppPreferences.setKeepAwake(context, next)
        addLog("Keep Screen Awake / WakeLock: ${if (next) "Enabled" else "Disabled"}")
    }

    fun toggleAutostartOnUsb() {
        val next = !_isAutostartOnUsbEnabled.value
        _isAutostartOnUsbEnabled.value = next
        AppPreferences.setAutostartOnUsb(context, next)
        addLog("Non-Intrusive Autostart on USB attach: ${if (next) "Enabled (Opt-in)" else "Disabled (Zero Intrusion)"}")
    }

    fun toggleAutostartOnBoot() {
        val next = !_isAutostartOnBootEnabled.value
        _isAutostartOnBootEnabled.value = next
        AppPreferences.setAutostartOnBoot(context, next)
        addLog("Non-Intrusive Autostart on Boot: ${if (next) "Enabled (Opt-in)" else "Disabled (Zero Intrusion)"}")
    }

    fun toggleKioskMode() {
        val next = !_isKioskModeEnabled.value
        _isKioskModeEnabled.value = next
        AppPreferences.setKioskMode(context, next)
        addLog("Kiosk / Headless Mode: ${if (next) "Enabled (Dedicated Appliance)" else "Disabled"}")
    }

    fun setServerPort(port: Int) {
        val validPort = if (port in 1024..65535) port else AppPreferences.DEFAULT_SERVER_PORT
        _serverPort.value = validPort
        AppPreferences.setServerPort(context, validPort)
        addLog("USB/IP Server Port set to: $validPort (Requires server restart to rebind)")
    }

    fun setSunshinePort(port: Int) {
        val validPort = if (port in 1024..65535) port else AppPreferences.DEFAULT_SUNSHINE_PORT
        _sunshinePort.value = validPort
        AppPreferences.setSunshinePort(context, validPort)
        addLog("Sunshine Webhook Port set to: $validPort (Requires app restart to rebind)")
    }

    fun toggleNotifications() {
        val next = !_isNotificationsEnabled.value
        _isNotificationsEnabled.value = next
        AppPreferences.setNotificationsEnabled(context, next)
        addLog("Mode Transition Toasts & Alerts: ${if (next) "Enabled" else "Disabled (Silent Mode)"}")
    }

    fun toggleHapticFeedback() {
        val next = !_isHapticFeedbackEnabled.value
        _isHapticFeedbackEnabled.value = next
        AppPreferences.setHapticFeedbackEnabled(context, next)
        addLog("Haptic Feedback & Rumble: ${if (next) "Enabled" else "Disabled"}")
    }

    fun setBindAddress(ip: String): Boolean {
        if (!AppPreferences.isValidBindAddress(ip)) return false
        AppPreferences.setBindAddress(context, ip)
        val saved = AppPreferences.getBindAddress(context)
        _bindAddress.value = saved
        addLog("Bind interface IP: ${if (saved.isEmpty()) "0.0.0.0 (All)" else saved}")
        return true
    }

    fun checkForUpdates() {
        val currentVersion = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "3.4.1"
        } catch (_: Exception) {
            "3.4.1"
        }
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            val res = AndroidUpdateChecker.checkForUpdate(currentVersion)
            _updateState.value = when {
                res.error != null -> UpdateUiState.Error(res.error)
                res.updateAvailable -> UpdateUiState.Available(res)
                else -> UpdateUiState.UpToDate(currentVersion)
            }
        }
    }

    fun downloadAndInstallUpdate(info: UpdateCheckResult) {
        _updateState.value = UpdateUiState.Downloading(0)
        viewModelScope.launch(Dispatchers.IO) {
            val dl = AndroidUpdateChecker.downloadAndVerifyApk(context, info) { pct ->
                _updateState.value = UpdateUiState.Downloading(pct)
            }
            dl.fold(
                onSuccess = { apk ->
                    _updateState.value = UpdateUiState.ReadyToInstall(info.latestVersion)
                    AndroidUpdateChecker.launchApkInstaller(context, apk)
                },
                onFailure = { err ->
                    _updateState.value = UpdateUiState.Error(err.message ?: "Verification failed")
                }
            )
        }
    }

    fun addLog(message: String) {
        val entry = "[${timeFormat.format(Date())}] $message"
        synchronized(logList) {
            logList.add(0, entry)
            if (logList.size > 150) logList.removeAt(logList.size - 1)
            _logs.value = logList.toList()
        }
    }

    fun clearLogs() {
        synchronized(logList) {
            logList.clear()
            _logs.value = emptyList()
        }
    }
}
