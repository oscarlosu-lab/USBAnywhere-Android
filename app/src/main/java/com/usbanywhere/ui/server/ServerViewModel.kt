package com.usbanywhere.ui.server

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.usbanywhere.data.ServerRuntimeStats
import com.usbanywhere.data.UsbExportedDevice
import com.usbanywhere.server.UsbHostManager
import com.usbanywhere.server.UsbIpServerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServerViewModel(application: Application) : AndroidViewModel(application) {

    val serverStats: StateFlow<ServerRuntimeStats> = UsbIpServerService.serverStats

    private val hostManager: UsbHostManager
        get() = UsbIpServerService.hostManagerInstance ?: UsbHostManager(getApplication())

    val connectedDevices: StateFlow<List<UsbExportedDevice>>
        get() = hostManager.connectedDevices

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refreshDevices()
    }

    fun toggleServer() {
        val currentStats = serverStats.value
        if (currentStats.isRunning) {
            UsbIpServerService.stopService(getApplication())
        } else {
            UsbIpServerService.startService(getApplication())
        }
    }

    fun refreshDevices() {
        viewModelScope.launch {
            _isRefreshing.value = true
            hostManager.refreshDevices()
            _isRefreshing.value = false
        }
    }

    fun requestPermission(busId: String) {
        hostManager.requestPermission(busId)
    }

    fun hasPermission(busId: String): Boolean {
        return hostManager.hasPermission(busId)
    }

    fun toggleShare(busId: String) {
        val list = connectedDevices.value
        val dev = list.find { it.busId == busId }
        dev?.let {
            it.isShared = !it.isShared
            hostManager.refreshDevices()
        }
    }
}
