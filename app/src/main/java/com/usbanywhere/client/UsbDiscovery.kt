package com.usbanywhere.client

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.usbanywhere.client.nsd.NsdDiscoveryHelper
import com.usbanywhere.data.RemoteServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

class UsbDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "UsbDiscovery"
        private const val DISCOVERY_PORT = 3241
        private const val USBIP_PORT = 3240
        private const val PROBE_MESSAGE = "USBANYWHERE_DISCOVERY_PROBE"
        private const val TCP_CONNECT_TIMEOUT_MS = 450
    }

    data class SubnetTarget(
        val subnetBase: String,
        val myIp: String,
        val broadcastAddress: InetAddress?
    )

    private val _discoveredServers = MutableStateFlow<List<RemoteServer>>(emptyList())
    val discoveredServers: StateFlow<List<RemoteServer>> = _discoveredServers.asStateFlow()

    private val serverMap = ConcurrentHashMap<String, RemoteServer>()

    private val nsdHelper = NsdDiscoveryHelper(context) { server ->
        val existing = serverMap[server.ipAddress]
        val merged = if (existing != null) {
            existing.copy(
                hostName = if (existing.hostName.startsWith("USBAnywhere Host (") || existing.hostName.startsWith("USBAnywhere Server (")) {
                    server.hostName
                } else {
                    existing.hostName
                },
                port = server.port
            )
        } else {
            server
        }
        serverMap[server.ipAddress] = merged
        _discoveredServers.value = serverMap.values.toList()
    }

    private fun getLocalSubnetTargets(): List<SubnetTarget> {
        val targets = mutableListOf<SubnetTarget>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                for (ia in iface.interfaceAddresses) {
                    val addr = ia.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostIp = addr.hostAddress ?: continue
                        val parts = hostIp.split(".")
                        if (parts.size == 4) {
                            val b1 = parts[0].toIntOrNull() ?: continue
                            // Private IPv4 LAN subnets: 10.x.x.x, 172.16-31.x.x, 192.168.x.x
                            val isPrivate = (b1 == 10) ||
                                    (b1 == 172 && (parts[1].toIntOrNull() ?: 0) in 16..31) ||
                                    (b1 == 192 && parts[1] == "168")
                            if (isPrivate) {
                                val subnetBase = "${parts[0]}.${parts[1]}.${parts[2]}"
                                targets.add(SubnetTarget(subnetBase, hostIp, ia.broadcast))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error enumerating network interfaces: ${e.message}")
        }
        return targets
    }

    private suspend fun scanSubnetTcp(subnetBase: String, myIp: String, timeoutMs: Int = TCP_CONNECT_TIMEOUT_MS) = coroutineScope {
        val jobs = (1..254).map { hostNum ->
            val targetIp = "$subnetBase.$hostNum"
            if (targetIp == myIp) return@map null
            async(Dispatchers.IO) {
                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(targetIp, USBIP_PORT), timeoutMs)
                        targetIp
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }.filterNotNull()

        val activeIps = jobs.awaitAll().filterNotNull()
        for (ip in activeIps) {
            val existing = serverMap[ip]
            val resolvedName = try {
                val h = InetAddress.getByName(ip).hostName
                if (!h.isNullOrBlank() && h != ip) h else "USBAnywhere Server ($ip)"
            } catch (_: Exception) {
                "USBAnywhere Server ($ip)"
            }

            val server = RemoteServer(
                ipAddress = ip,
                port = USBIP_PORT,
                hostName = existing?.hostName ?: resolvedName,
                latencyMs = 5,
                isConnected = existing?.isConnected ?: false,
                devices = existing?.devices ?: emptyList()
            )
            serverMap[ip] = server
        }
        _discoveredServers.value = serverMap.values.toList()
    }

    suspend fun scanNetwork(timeoutMs: Int = 3000) = withContext(Dispatchers.IO) {
        // Tier 1: Start mDNS / Bonjour Zeroconf discovery in parallel
        nsdHelper.startDiscovery()

        val targets = getLocalSubnetTargets()
        var multicastLock: WifiManager.MulticastLock? = null

        // Tier 2: Launch concurrent subnet TCP 3240 prober (matches Windows Client NetworkScanner)
        val tcpScanJob = launch {
            for (target in targets) {
                scanSubnetTcp(target.subnetBase, target.myIp)
            }
        }

        // Tier 3: UDP Broadcast discovery (directed subnet broadcast + 255.255.255.255)
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("USBAnywhere::Discovery")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()

            val socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = timeoutMs

            val probeBytes = PROBE_MESSAGE.toByteArray()
            val globalBcast = InetAddress.getByName("255.255.255.255")
            val broadcastAddrs = (targets.mapNotNull { it.broadcastAddress } + globalBcast).distinct()

            for (bcast in broadcastAddrs) {
                try {
                    val packet = DatagramPacket(probeBytes, probeBytes.size, bcast, DISCOVERY_PORT)
                    socket.send(packet)
                } catch (_: Exception) {}
            }

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
                        port = USBIP_PORT,
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
        } catch (e: Exception) {
            Log.w(TAG, "Discovery scan exception: ${e.message}")
        } finally {
            try {
                if (multicastLock?.isHeld == true) multicastLock.release()
            } catch (_: Exception) {}
        }

        tcpScanJob.join()

        // Brief delay to allow in-flight mDNS resolutions to finish
        delay(300)
        nsdHelper.stopDiscovery()
    }

    fun addManualServer(ip: String, port: Int = USBIP_PORT, name: String = "Manual Host") {
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
