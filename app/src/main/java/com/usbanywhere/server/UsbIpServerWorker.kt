package com.usbanywhere.server

import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import com.usbanywhere.protocol.UsbIpConstants
import com.usbanywhere.protocol.UsbIpPackets
import com.usbanywhere.protocol.UsbIpUrb
import com.usbanywhere.security.ZeroTrustAuthManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.Socket
import java.net.SocketException

class UsbIpServerWorker(
    private val socket: Socket,
    private val hostManager: UsbHostManager,
    private val onPacketTransferred: (packets: Long, bytes: Long) -> Unit,
    private val onClientDisconnected: (worker: UsbIpServerWorker) -> Unit
) : Runnable {

    companion object {
        private const val TAG = "UsbIpServerWorker"
    }

    @Volatile
    private var isRunning = true
    private var attachedBusId: String? = null
    private var isAuthenticated = false

    override fun run() {
        try {
            // Low-latency esports tuning: disable Nagle's algorithm & expand socket buffers
            socket.tcpNoDelay = true
            socket.receiveBufferSize = 65536
            socket.sendBufferSize = 65536

            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val clientIp = socket.inetAddress?.hostAddress ?: "unknown"

            while (isRunning && !socket.isClosed) {
                val headerCode = try {
                    input.readShort()
                } catch (_: EOFException) {
                    break
                } catch (_: SocketException) {
                    break
                }

                if (headerCode.toInt() == UsbIpConstants.USBIP_VERSION) {
                    // Handshake Management Phase
                    val opCode = input.readShort()
                    val status = input.readInt()

                    // Check Zero-Trust Authentication
                    when (opCode) {
                        UsbIpConstants.OP_REQ_AUTH -> {
                            val pin = UsbIpPackets.readReqAuth(input)
                            val isValid = ZeroTrustAuthManager.validatePin(pin, clientIp)
                            if (isValid) {
                                isAuthenticated = true
                                UsbIpPackets.writeRepAuth(output, UsbIpConstants.AUTH_STATUS_SUCCESS, "Authenticated")
                                Log.i(TAG, "Client $clientIp authenticated successfully via Zero-Trust PIN")
                            } else {
                                UsbIpPackets.writeRepAuth(output, UsbIpConstants.AUTH_STATUS_BAD_PIN, "Invalid PIN")
                                Log.w(TAG, "Client $clientIp failed Zero-Trust authentication")
                                break // Disconnect unauthorized client
                            }
                            continue
                        }
                    }

                    // For all other management opcodes, enforce authentication if enabled
                    val isAuthorized = !ZeroTrustAuthManager.isAuthEnforced.value ||
                            isAuthenticated ||
                            ZeroTrustAuthManager.authorizedPeers.value.contains(clientIp)

                    if (!isAuthorized) {
                        Log.w(TAG, "Rejecting unauthenticated client $clientIp for opcode 0x${Integer.toHexString(opCode.toInt())}")
                        UsbIpPackets.writeRepAuth(output, UsbIpConstants.AUTH_STATUS_UNAUTHORIZED, "Authentication Required")
                        break
                    }

                    when (opCode) {
                        UsbIpConstants.OP_REQ_DEVLIST -> {
                            Log.i(TAG, "Client $clientIp requested device list")
                            val sharedDevs = hostManager.connectedDevices.value.filter { it.isShared }
                            UsbIpPackets.writeRepDevlist(output, sharedDevs)
                        }
                        UsbIpConstants.OP_REQ_IMPORT -> {
                            val busId = UsbIpPackets.readFixedString(input, UsbIpConstants.SYSFS_BUSID_SIZE)
                            Log.i(TAG, "Client ${socket.inetAddress} requested attach of device $busId")

                            val conn = hostManager.openDevice(busId)
                            val dev = hostManager.connectedDevices.value.find { it.busId == busId }

                            if (conn != null && dev != null) {
                                attachedBusId = busId
                                dev.isAttached = true
                                UsbIpPackets.writeRepImport(output, UsbIpConstants.USBIP_STATUS_OK, dev)
                                Log.i(TAG, "Device $busId attached successfully, entering streaming loop")
                                streamUrbs(input, output, busId, conn)
                                break // Streaming finished or disconnected
                            } else {
                                Log.w(TAG, "Failed to attach device $busId (no permission or device missing)")
                                UsbIpPackets.writeRepImport(output, UsbIpConstants.USBIP_STATUS_ERROR, null)
                            }
                        }
                        else -> {
                            Log.w(TAG, "Unknown management opcode: 0x${Integer.toHexString(opCode.toInt())}")
                        }
                    }
                } else {
                    Log.w(TAG, "Invalid protocol version: 0x${Integer.toHexString(headerCode.toInt())}")
                    break
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Session ended for ${socket.inetAddress}: ${e.message}")
        } finally {
            close()
        }
    }

    private fun streamUrbs(
        input: DataInputStream,
        output: DataOutputStream,
        busId: String,
        connection: UsbDeviceConnection
    ) {
        val translator = UrbTranslator(hostManager)

        try {
            while (isRunning && !socket.isClosed) {
                val cmdCode = try {
                    input.readInt()
                } catch (_: EOFException) {
                    break
                } catch (_: SocketException) {
                    break
                }

                when (cmdCode) {
                    UsbIpConstants.USBIP_CMD_SUBMIT -> {
                        val seqNum = input.readInt()
                        val devId = input.readInt()
                        val direction = input.readInt()
                        val endpoint = input.readInt()
                        val transferFlags = input.readInt()
                        val bufferLength = input.readInt()
                        val startFrame = input.readInt()
                        val numPackets = input.readInt()
                        val interval = input.readInt()
                        val setup = ByteArray(8)
                        input.readFully(setup)

                        val cmd = com.usbanywhere.protocol.UsbIpCmdSubmit(
                            command = cmdCode,
                            seqNum = seqNum,
                            devId = devId,
                            direction = direction,
                            endpoint = endpoint,
                            transferFlags = transferFlags,
                            transferBufferLength = bufferLength,
                            startFrame = startFrame,
                            numberOfPackets = numPackets,
                            interval = interval,
                            setup = setup
                        )

                        var outPayload: ByteArray? = null
                        if (direction == UsbIpConstants.USBIP_DIR_OUT && bufferLength > 0) {
                            outPayload = ByteArray(bufferLength)
                            input.readFully(outPayload)
                        }

                        val result = translator.executeUrb(busId, connection, cmd, outPayload)

                        UsbIpUrb.writeRetSubmit(
                            output = output,
                            seqNum = seqNum,
                            devId = devId,
                            direction = direction,
                            endpoint = endpoint,
                            status = result.status,
                            actualLength = result.actualLength,
                            startFrame = startFrame,
                            numberOfPackets = numPackets,
                            errorCount = 0,
                            setup = setup,
                            data = result.returnData
                        )

                        val bytes = if (direction == UsbIpConstants.USBIP_DIR_OUT) {
                            bufferLength.toLong()
                        } else {
                            result.actualLength.toLong()
                        }
                        onPacketTransferred(1, bytes)
                    }
                    UsbIpConstants.USBIP_CMD_UNLINK -> {
                        val seqNum = input.readInt()
                        val devId = input.readInt()
                        val direction = input.readInt()
                        val endpoint = input.readInt()
                        val unlinkSeqNum = input.readInt()
                        val padding = ByteArray(24)
                        input.readFully(padding)

                        UsbIpUrb.writeRetUnlink(
                            output = output,
                            seqNum = seqNum,
                            devId = devId,
                            direction = direction,
                            endpoint = endpoint,
                            status = UsbIpConstants.USBIP_STATUS_OK
                        )
                    }
                    else -> {
                        Log.w(TAG, "Unknown URB command code: 0x${Integer.toHexString(cmdCode)}")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Streaming loop closed: ${e.message}")
        }
    }

    fun close() {
        isRunning = false
        attachedBusId?.let { busId ->
            hostManager.connectedDevices.value.find { it.busId == busId }?.isAttached = false
            hostManager.closeDevice(busId)
            attachedBusId = null
        }
        try { socket.close() } catch (_: Exception) {}
        onClientDisconnected(this)
    }
}
