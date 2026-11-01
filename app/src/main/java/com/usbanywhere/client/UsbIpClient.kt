package com.usbanywhere.client

import android.util.Log
import com.usbanywhere.data.TunnelState
import com.usbanywhere.data.UsbExportedDevice
import com.usbanywhere.protocol.UsbIpConstants
import com.usbanywhere.protocol.UsbIpPackets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

class UsbIpClient {

    companion object {
        private const val TAG = "UsbIpClient"
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 5000
        private const val DYNAMIC_CONTROL_PORT = 3241
    }

    var lastError: String? = null
        private set

    private var activeSocket: Socket? = null
    private var attachedBusId: String? = null
    private var attachedHost: String? = null
    private var attachedPort: Int = UsbIpConstants.DEFAULT_PORT

    private val isExplicitDisconnect = AtomicBoolean(false)
    private var socketMonitorJob: Job? = null
    private val clientScope = CoroutineScope(Dispatchers.IO)

    private val _tunnelState = MutableStateFlow(TunnelState.DISCONNECTED)
    val tunnelState: StateFlow<TunnelState> = _tunnelState.asStateFlow()

    /**
     * Connects to remote USB/IP server and retrieves the list of exported devices.
     */
    suspend fun listRemoteDevices(
        host: String,
        port: Int = UsbIpConstants.DEFAULT_PORT
    ): List<UsbExportedDevice> =
        withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS

                val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

                // Send OP_REQ_DEVLIST
                output.writeShort(UsbIpConstants.USBIP_VERSION)
                output.writeShort(UsbIpConstants.OP_REQ_DEVLIST.toInt())
                output.writeInt(UsbIpConstants.USBIP_STATUS_OK)
                output.flush()

                // Read OP_REP_DEVLIST
                val version = input.readShort()
                val opcode = input.readShort()
                val status = input.readInt()

                val exported = if (version.toInt() == UsbIpConstants.USBIP_VERSION &&
                    opcode == UsbIpConstants.OP_REP_DEVLIST &&
                    status == UsbIpConstants.USBIP_STATUS_OK
                ) {
                    val devices = UsbIpPackets.readRepDevlist(input)
                    Log.i(TAG, "Retrieved ${devices.size} devices from $host:$port")
                    devices.toMutableList()
                } else {
                    Log.w(TAG, "Invalid response from $host: ver=$version, op=$opcode, status=$status")
                    mutableListOf()
                }
                val dynamicDevs = queryDynamicInventory(host)
                for (dyn in dynamicDevs) {
                    if (exported.none { it.busId == dyn.busId }) {
                        exported.add(dyn)
                    }
                }
                exported
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list devices from $host:$port: ${e.message}")
                queryDynamicInventory(host)
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }

    private fun queryDynamicInventory(host: String): List<UsbExportedDevice> {
        val result = mutableListOf<UsbExportedDevice>()
        val sock = Socket()
        try {
            sock.tcpNoDelay = true
            sock.connect(InetSocketAddress(host, DYNAMIC_CONTROL_PORT), 1200)
            sock.soTimeout = 1500
            val out = sock.getOutputStream()
            out.write("LIST\n".toByteArray(Charsets.UTF_8))
            out.flush()
            val jsonStr = sock.getInputStream().bufferedReader(Charsets.UTF_8).readText().trim()
            if (jsonStr.isNotEmpty()) {
                val root = org.json.JSONObject(jsonStr)
                val arr = root.optJSONArray("devices")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val busId = obj.optString("busid", "")
                        if (busId.isBlank()) continue
                        val vid = obj.optString("vid", "0000").toIntOrNull(16) ?: 0
                        val pid = obj.optString("pid", "0000").toIntOrNull(16) ?: 0
                        val desc = obj.optString("description", "USB Device")
                        val shared = obj.optBoolean("shared", false)
                        val busNum = busId.substringBefore("-", "1").toIntOrNull() ?: 1
                        result.add(
                            UsbExportedDevice(
                                busId = busId,
                                path = "/sys/bus/usb/devices/$busId",
                                busNum = busNum,
                                devNum = i + 1,
                                speed = 3,
                                vendorId = vid,
                                productId = pid,
                                deviceClass = 0,
                                deviceSubclass = 0,
                                deviceProtocol = 0,
                                interfaces = emptyList(),
                                productName = desc,
                                isShared = shared
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Dynamic inventory query on $host:$DYNAMIC_CONTROL_PORT skipped: ${e.message}")
        } finally {
            try { sock.close() } catch (_: Exception) {}
        }
        return result
    }

    private fun tryDynamicBind(host: String, busId: String): Boolean {
        val sock = Socket()
        try {
            sock.tcpNoDelay = true
            sock.connect(InetSocketAddress(host, DYNAMIC_CONTROL_PORT), 1500)
            sock.soTimeout = 2500
            val out = sock.getOutputStream()
            out.write("BIND $busId\n".toByteArray(Charsets.UTF_8))
            out.flush()
            val resp = sock.getInputStream().bufferedReader(Charsets.UTF_8).readLine()?.trim() ?: ""
            if (resp.contains("\"DENIED\"", ignoreCase = true)) {
                lastError = try {
                    org.json.JSONObject(resp).optString("message", "Denied by host policy")
                } catch (_: Exception) {
                    "Denied by host policy"
                }
                return false
            }
            if (resp.contains("\"OK\"", ignoreCase = true)) {
                Thread.sleep(150)
            }
        } catch (_: Exception) {
            // Legacy server without TCP 3241
        } finally {
            try { sock.close() } catch (_: Exception) {}
        }
        return true
    }

    /**
     * Attaches a remote USB device using OP_REQ_IMPORT.
     */
    suspend fun attachRemoteDevice(
        host: String,
        port: Int = UsbIpConstants.DEFAULT_PORT,
        busId: String,
        onAttached: (UsbExportedDevice) -> Unit,
        onUnexpectedDisconnect: () -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        lastError = null
        isExplicitDisconnect.set(false)
        _tunnelState.value = TunnelState.CONNECTING

        if (!tryDynamicBind(host, busId)) {
            _tunnelState.value = TunnelState.DISCONNECTED
            return@withContext false
        }

        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.receiveBufferSize = 65536
            socket.sendBufferSize = 65536
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)

            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

            // Send OP_REQ_IMPORT
            output.writeShort(UsbIpConstants.USBIP_VERSION)
            output.writeShort(UsbIpConstants.OP_REQ_IMPORT.toInt())
            output.writeInt(UsbIpConstants.USBIP_STATUS_OK)
            UsbIpPackets.writeFixedString(output, busId, UsbIpConstants.SYSFS_BUSID_SIZE)
            output.flush()

            // Read OP_REP_IMPORT
            val version = input.readShort()
            val opcode = input.readShort()
            val status = input.readInt()

            if (version.toInt() == UsbIpConstants.USBIP_VERSION &&
                opcode == UsbIpConstants.OP_REP_IMPORT &&
                status == UsbIpConstants.USBIP_STATUS_OK
            ) {
                val path = UsbIpPackets.readFixedString(input, UsbIpConstants.SYSFS_PATH_MAX)
                val retBusId = UsbIpPackets.readFixedString(input, UsbIpConstants.SYSFS_BUSID_SIZE)
                val busNum = input.readInt()
                val devNum = input.readInt()
                val speed = input.readInt()
                val vendorId = input.readUnsignedShort()
                val productId = input.readUnsignedShort()
                val bcdDevice = input.readUnsignedShort()
                val deviceClass = input.readUnsignedByte()
                val deviceSubclass = input.readUnsignedByte()
                val deviceProtocol = input.readUnsignedByte()
                val configValue = input.readUnsignedByte()
                val numConfigs = input.readUnsignedByte()
                val numInterfaces = input.readUnsignedByte()

                val dev = UsbExportedDevice(
                    busId = retBusId,
                    path = path,
                    busNum = busNum,
                    devNum = devNum,
                    speed = speed,
                    vendorId = vendorId,
                    productId = productId,
                    bcdDevice = bcdDevice,
                    deviceClass = deviceClass,
                    deviceSubclass = deviceSubclass,
                    deviceProtocol = deviceProtocol,
                    configurationValue = configValue,
                    numConfigurations = numConfigs,
                    interfaces = emptyList(),
                    isAttached = true
                )

                this@UsbIpClient.activeSocket = socket
                this@UsbIpClient.attachedBusId = busId
                this@UsbIpClient.attachedHost = host
                this@UsbIpClient.attachedPort = port
                _tunnelState.value = TunnelState.ACTIVE

                onAttached(dev)
                Log.i(TAG, "Successfully attached remote device $busId ($vendorId:$productId)")

                startSocketMonitor(socket, onUnexpectedDisconnect)
                true
            } else {
                Log.w(TAG, "Attach failed with status: $status")
                _tunnelState.value = TunnelState.DISCONNECTED
                try { socket.close() } catch (_: Exception) {}
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Attach exception for $host:$busId: ${e.message}")
            _tunnelState.value = TunnelState.DISCONNECTED
            try { socket.close() } catch (_: Exception) {}
            false
        }
    }

    private fun startSocketMonitor(
        socket: Socket,
        onUnexpectedDisconnect: () -> Unit
    ) {
        socketMonitorJob?.cancel()
        socketMonitorJob = clientScope.launch {
            try {
                val inputStream = socket.getInputStream()
                val buffer = ByteArray(1)
                while (isActive && !socket.isClosed) {
                    val read = inputStream.read(buffer)
                    if (read < 0) {
                        Log.w(TAG, "Remote host closed connection for $attachedBusId")
                        break
                    }
                }
            } catch (e: SocketException) {
                Log.d(TAG, "SocketException in monitor: ${e.message}")
            } catch (e: Exception) {
                Log.d(TAG, "Monitor exception: ${e.message}")
            } finally {
                if (!isExplicitDisconnect.get() && _tunnelState.value == TunnelState.ACTIVE) {
                    Log.w(TAG, "Triggering unexpected disconnect callback")
                    onUnexpectedDisconnect()
                }
            }
        }
    }

    fun stopSocketMonitor() {
        socketMonitorJob?.cancel()
        socketMonitorJob = null
    }

    fun setTunnelState(state: TunnelState) {
        _tunnelState.value = state
    }

    fun disconnect() {
        isExplicitDisconnect.set(true)
        stopSocketMonitor()
        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        activeSocket = null
        attachedBusId = null
        _tunnelState.value = TunnelState.DISCONNECTED
    }

    fun isConnected(): Boolean = activeSocket?.isConnected == true && activeSocket?.isClosed == false
}
