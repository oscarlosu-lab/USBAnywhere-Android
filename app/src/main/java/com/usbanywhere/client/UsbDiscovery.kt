package com.usbanywhere.client

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.usbanywhere.client.nsd.NsdDiscoveryHelper
import com.usbanywhere.data.RemoteServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

class UsbDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "UsbDiscovery"
        private const val DISCOVERY_PORT = 3241
        private const val PROBE_MESSAGE = "USBANYWHERE_DISCOVERY_PROBE"
    }

    private val _discoveredServers = MutableStateFlow<List<RemoteServer>>(emptyList())
    val discoveredServers: StateFlow<List<RemoteServer>> = _discoveredServers.asStateFlow()

    private val serverMap = ConcurrentHashMap<String, RemoteServer>()

    private val nsdHelper = NsdDiscoveryHelper(context) { server ->
        val existing = serverMap[server.ipAddress]
        val merged = if (existing != null) {
            existing.copy(
                hostName = if (existing.hostName.startsWith("USBAnywhere Host (")) server.hostName else existing.hostName,
                port = server.port
            )
        } else {
            server
        }
        serverMap[server.ipAddress] = merged
        _discoveredServers.value = serverMap.values.toList()
    }

    suspend fun scanNetwork(timeoutMs: Int = 3000) = withContext(Dispatchers.IO) {
        // Start mDNS / Bonjour Zeroconf discovery in parallel
        nsdHelper.startDiscovery()

        var multicastLock: WifiManager.MulticastLock? = null
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("USBAnywhere::Discovery")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()

            val socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = timeoutMs

            val probeBytes = PROBE_MESSAGE.toByteArray()
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(probeBytes, probeBytes.size, broadcastAddr, DISCOVERY_PORT)
            socket.send(packet)

            val receiveBuffer = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    socket.receive(receivePacket)
                    val senderIp = receivePacket.address.hostAddress ?: continue
                    val responseStr = String(receivePacket.data, 0, receivePacket.length).trim()

                    val hostName = if (responseStr.startsWith("USBANYWHERE_SERVER:")) {
                        responseStr.substringAfter("USBANYWHERE_SERVER:").trim()
                    } else {
                        "USBAnywhere Host ($senderIp)"
                    }

                    val existing = serverMap[senderIp]
                    val server = RemoteServer(
                        ipAddress = senderIp,
                        port = 3240,
                        hostName = hostName,
                        latencyMs = System.currentTimeMillis() - startTime,
                        isConnected = existing?.isConnected ?: false,
                        devices = existing?.devices ?: emptyList()
                    )
                    serverMap[senderIp] = server
                    _discoveredServers.value = serverMap.values.toList()
                } catch (_: SocketTimeoutException) {
                    break
                }
            }
            socket.close()

            // Brief delay to allow in-flight mDNS resolutions to finish
            delay(500)
        } catch (e: Exception) {
            Log.w(TAG, "Discovery scan exception: ${e.message}")
        } finally {
            try {
                if (multicastLock?.isHeld == true) multicastLock.release()
            } catch (_: Exception) {}
            // Stop mDNS active discovery pass
            nsdHelper.stopDiscovery()
        }
    }

    fun addManualServer(ip: String, port: Int = 3240, name: String = "Manual Host") {
        val existing = serverMap[ip]
        val server = existing?.copy(port = port) ?: RemoteServer(
            ipAddress = ip,
            port = port,
            hostName = name
        )
        serverMap[ip] = server
        _discoveredServers.value = serverMap.values.toList()
    }

    fun startContinuousDiscovery() {
        nsdHelper.startDiscovery()
    }

    fun stopContinuousDiscovery() {
        nsdHelper.stopDiscovery()
    }
}
