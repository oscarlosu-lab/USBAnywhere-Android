package com.usbanywhere.ui.client

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.usbanywhere.client.UsbDiscovery
import com.usbanywhere.client.UsbIpClient
import com.usbanywhere.data.RemoteServer
import com.usbanywhere.data.TunnelState
import com.usbanywhere.data.UsbExportedDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClientViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ClientViewModel"
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private val discovery = UsbDiscovery(application)
    private val client = UsbIpClient()

    val discoveredServers: StateFlow<List<RemoteServer>> = discovery.discoveredServers
    val tunnelState: StateFlow<TunnelState> = client.tunnelState

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedServer = MutableStateFlow<RemoteServer?>(null)
    val selectedServer: StateFlow<RemoteServer?> = _selectedServer.asStateFlow()

    private val _remoteDevices = MutableStateFlow<List<UsbExportedDevice>>(emptyList())
    val remoteDevices: StateFlow<List<UsbExportedDevice>> = _remoteDevices.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _attachedDevice = MutableStateFlow<UsbExportedDevice?>(null)
    val attachedDevice: StateFlow<UsbExportedDevice?> = _attachedDevice.asStateFlow()

    private val _reconnectAttempt = MutableStateFlow(0)
    val reconnectAttempt: StateFlow<Int> = _reconnectAttempt.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var reconnectJob: Job? = null
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            if (client.tunnelState.value == TunnelState.RECONNECTING && _attachedDevice.value != null) {
                Log.i(TAG, "Network restored via Wi-Fi/Roaming! Triggering immediate reconnect.")
                triggerAutoReconnect(immediate = true)
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            if (_attachedDevice.value != null && client.tunnelState.value == TunnelState.ACTIVE) {
                Log.w(TAG, "Network lost during active USB tunnel! Starting roaming recovery.")
                triggerAutoReconnect(immediate = false)
            }
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
        client.disconnect()
    }

    fun scanNetwork() {
        viewModelScope.launch {
            _isScanning.value = true
            _statusMessage.value = "Scanning local subnet (mDNS Zeroconf + UDP 3241)..."
            discovery.scanNetwork(timeoutMs = 2500)
            _isScanning.value = false
            _statusMessage.value = "Scan complete. Found ${discoveredServers.value.size} server(s)."
        }
    }

    fun addManualServer(ip: String, port: Int = 3240) {
        if (ip.isNotBlank()) {
            discovery.addManualServer(ip.trim(), port)
            connectToServer(ip.trim(), port)
        }
    }

    fun connectToServer(ip: String, port: Int = 3240) {
        viewModelScope.launch {
            _isConnecting.value = true
            _statusMessage.value = "Querying $ip:$port..."
            val devs = client.listRemoteDevices(ip, port)
            val server = RemoteServer(
                ipAddress = ip,
                port = port,
                isConnected = devs.isNotEmpty(),
                devices = devs
            )
            _selectedServer.value = server
            _remoteDevices.value = devs
            _isConnecting.value = false
            _statusMessage.value = if (devs.isNotEmpty()) {
                "Connected to $ip! Found ${devs.size} device(s)."
            } else {
                "No exported devices found on $ip."
            }
        }
    }

    fun attachDevice(busId: String) {
        val server = _selectedServer.value ?: return
        reconnectJob?.cancel()
        _reconnectAttempt.value = 0

        viewModelScope.launch {
            _statusMessage.value = "Attaching remote device $busId..."
            client.attachRemoteDevice(
                host = server.ipAddress,
                port = server.port,
                busId = busId,
                onAttached = { dev ->
                    _attachedDevice.value = dev
                    _statusMessage.value = "Attached ${dev.displayName} (${dev.busId})!"
                },
                onUnexpectedDisconnect = {
                    triggerAutoReconnect(immediate = false)
                }
            )
        }
    }

    fun triggerAutoReconnect(immediate: Boolean = false) {
        val dev = _attachedDevice.value ?: return
        val server = _selectedServer.value ?: return

        client.setTunnelState(TunnelState.RECONNECTING)
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            val delays = listOf(500L, 1000L, 2000L, 3000L, 3000L)

            for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
                _reconnectAttempt.value = attempt
                _statusMessage.value = "Connection lost. Reconnecting to ${dev.displayName} (Attempt $attempt/$MAX_RECONNECT_ATTEMPTS)..."

                if (!immediate || attempt > 1) {
                    val delayMs = delays.getOrElse(attempt - 1) { 3000L }
                    delay(delayMs)
                }

                Log.i(TAG, "Executing auto-reconnect attempt $attempt for busId ${dev.busId}")
                val success = client.attachRemoteDevice(
                    host = server.ipAddress,
                    port = server.port,
                    busId = dev.busId,
                    onAttached = { reconnectedDev ->
                        _attachedDevice.value = reconnectedDev
                        _statusMessage.value = "Successfully reconnected to ${reconnectedDev.displayName}!"
                    },
                    onUnexpectedDisconnect = {
                        triggerAutoReconnect(immediate = false)
                    }
                )

                if (success) {
                    _reconnectAttempt.value = 0
                    Log.i(TAG, "Auto-reconnect succeeded on attempt $attempt!")
                    return@launch
                }
            }

            // Exhausted all retry attempts
            Log.w(TAG, "Exhausted all $MAX_RECONNECT_ATTEMPTS auto-reconnect attempts.")
            _reconnectAttempt.value = 0
            _attachedDevice.value = null
            client.setTunnelState(TunnelState.DISCONNECTED)
            _statusMessage.value = "Unable to reconnect to ${dev.displayName} after $MAX_RECONNECT_ATTEMPTS attempts."
        }
    }

    fun detachCurrentDevice() {
        reconnectJob?.cancel()
        reconnectJob = null
        _reconnectAttempt.value = 0
        client.disconnect()
        _attachedDevice.value = null
        _statusMessage.value = "Device detached."
    }
}
