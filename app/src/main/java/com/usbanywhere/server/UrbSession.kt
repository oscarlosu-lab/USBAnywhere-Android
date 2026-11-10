package com.usbanywhere.server

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbRequest
import android.util.Log
import com.usbanywhere.protocol.UsbIpCmdSubmit
import com.usbanywhere.protocol.UsbIpConstants
import com.usbanywhere.protocol.UsbIpUrb
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeoutException

/**
 * Executes the URBs of one attached device, the way the Linux usbip stub does:
 *  - bulk/interrupt URBs are queued asynchronously with [UsbRequest] and completed by a
 *    reaper thread, so an interrupt IN that waits for input (gamepad idle) never times
 *    out and never blocks other traffic;
 *  - control URBs run in order on their own thread;
 *  - UNLINK really cancels the URB and suppresses its RET_SUBMIT;
 *  - errors are reported as negative errno values;
 *  - SET_CONFIGURATION / SET_INTERFACE are handled through the Android API.
 */
class UrbSession(
    private val busId: String,
    private val connection: UsbDeviceConnection,
    private val hostManager: UsbHostManager,
    private val output: DataOutputStream,
    private val onPacketTransferred: (packets: Long, bytes: Long) -> Unit,
    private val onFatal: () -> Unit
) {
    companion object {
        private const val TAG = "UrbSession"
        private const val CONTROL_TIMEOUT_MS = 5000
        private const val REAP_POLL_MS = 250L

        const val ACTION_PASSTHROUGH = 0
        const val ACTION_SET_CONFIGURATION = 1
        const val ACTION_SET_INTERFACE = 2

        /** Requests the stub must not forward to the device as a raw control transfer. */
        fun classifyControl(setup: ByteArray): Int {
            val bmRequestType = setup[0].toInt() and 0xFF
            val bRequest = setup[1].toInt() and 0xFF
            return when {
                bmRequestType == 0x00 && bRequest == 0x09 -> ACTION_SET_CONFIGURATION
                bmRequestType == 0x01 && bRequest == 0x0B -> ACTION_SET_INTERFACE
                else -> ACTION_PASSTHROUGH
            }
        }

        /** Builds the RET_SUBMIT iso descriptors for an ISO URB we cannot execute. */
        fun failedIsoDescriptors(cmdDescriptors: ByteArray, count: Int): ByteArray {
            val src = ByteBuffer.wrap(cmdDescriptors).order(ByteOrder.BIG_ENDIAN)
            val dst = ByteBuffer.allocate(count * 16).order(ByteOrder.BIG_ENDIAN)
            for (i in 0 until count) {
                var offset = 0
                var length = 0
                if (src.remaining() >= 16) {
                    offset = src.int; length = src.int; src.int; src.int
                }
                dst.putInt(offset).putInt(length).putInt(0).putInt(UsbIpConstants.EXDEV)
            }
            return dst.array()
        }

        private val BOOT_KEYBOARD_REPORT_DESC = intArrayOf(
            0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x05, 0x07,
            0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01,
            0x75, 0x01, 0x95, 0x08, 0x81, 0x02, 0x95, 0x01,
            0x75, 0x08, 0x81, 0x01, 0x95, 0x05, 0x75, 0x01,
            0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91, 0x02,
            0x95, 0x01, 0x75, 0x03, 0x91, 0x01, 0x95, 0x06,
            0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07,
            0x19, 0x00, 0x29, 0x65, 0x81, 0x00, 0xC0
        ).map { it.toByte() }.toByteArray()

        private val BOOT_MOUSE_REPORT_DESC = intArrayOf(
            0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x09, 0x01,
            0xA1, 0x00, 0x05, 0x09, 0x19, 0x01, 0x29, 0x03,
            0x15, 0x00, 0x25, 0x01, 0x95, 0x03, 0x75, 0x01,
            0x81, 0x02, 0x95, 0x01, 0x75, 0x05, 0x81, 0x01,
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x15, 0x81,
            0x25, 0x7F, 0x75, 0x08, 0x95, 0x02, 0x81, 0x06,
            0xC0, 0xC0
        ).map { it.toByte() }.toByteArray()

        private val GAMEPAD_REPORT_DESC = intArrayOf(
            0x05, 0x01, 0x09, 0x05, 0xA1, 0x01, 0x05, 0x09,
            0x19, 0x01, 0x29, 0x10, 0x15, 0x00, 0x25, 0x01,
            0x75, 0x01, 0x95, 0x10, 0x81, 0x02, 0x05, 0x01,
            0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35,
            0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95,
            0x04, 0x81, 0x02, 0xC0
        ).map { it.toByte() }.toByteArray()

        /**
         * Fallback descriptor extraction from Android's cached [UsbDeviceConnection.getRawDescriptors]
         * when a live EP0 GET_DESCRIPTOR controlTransfer fails or stalls on keyboards/mice/gamepads.
         */
        fun extractDescriptorFallback(
            raw: ByteArray?,
            bmRequestType: Int,
            bRequest: Int,
            wValue: Int,
            wIndex: Int,
            wLength: Int
        ): ByteArray? {
            if (bRequest != 0x06 || wLength <= 0 || raw == null || raw.size < 18) return null
            val descType = (wValue ushr 8) and 0xFF
            val descIndex = wValue and 0xFF

            when (descType) {
                0x01 -> { // USB_DT_DEVICE
                    if ((raw[1].toInt() and 0xFF) == 0x01) {
                        val len = minOf(raw[0].toInt() and 0xFF, raw.size, wLength)
                        return raw.copyOfRange(0, len)
                    }
                }
                0x02 -> { // USB_DT_CONFIG
                    var pos = 0
                    var cfgIdx = 0
                    while (pos + 2 <= raw.size) {
                        val bLen = raw[pos].toInt() and 0xFF
                        val bType = raw[pos + 1].toInt() and 0xFF
                        if (bLen < 2 || pos + bLen > raw.size) break
                        if (bType == 0x02 && pos + 4 <= raw.size) {
                            if (cfgIdx == descIndex) {
                                val totalLen = (raw[pos + 2].toInt() and 0xFF) or ((raw[pos + 3].toInt() and 0xFF) shl 8)
                                val end = minOf(raw.size, pos + totalLen, pos + wLength)
                                return raw.copyOfRange(pos, end)
                            }
                            cfgIdx++
                        }
                        pos += bLen
                    }
                }
                0x21, 0x22 -> { // HID_DT_HID (0x21) or HID_DT_REPORT (0x22)
                    var pos = 0
                    var curIface = -1
                    var curProtocol = 0
                    var hidReportLen = -1
                    while (pos + 2 <= raw.size) {
                        val bLen = raw[pos].toInt() and 0xFF
                        val bType = raw[pos + 1].toInt() and 0xFF
                        if (bLen < 2 || pos + bLen > raw.size) break
                        if (bType == 0x04 && bLen >= 9) {
                            curIface = raw[pos + 2].toInt() and 0xFF
                            curProtocol = raw[pos + 7].toInt() and 0xFF
                        } else if (bType == 0x21 && curIface == (wIndex and 0xFF)) {
                            if (descType == 0x21) {
                                val end = minOf(raw.size, pos + bLen, pos + wLength)
                                return raw.copyOfRange(pos, end)
                            }
                            if (bLen >= 9) {
                                hidReportLen = (raw[pos + 7].toInt() and 0xFF) or ((raw[pos + 8].toInt() and 0xFF) shl 8)
                                break
                            }
                        }
                        pos += bLen
                    }
                    if (descType == 0x22) {
                        val template = when (curProtocol) {
                            1 -> BOOT_KEYBOARD_REPORT_DESC
                            2 -> BOOT_MOUSE_REPORT_DESC
                            else -> GAMEPAD_REPORT_DESC
                        }
                        val targetLen = if (hidReportLen > 0) minOf(hidReportLen, wLength) else minOf(template.size, wLength)
                        return if (targetLen <= template.size) {
                            template.copyOf(targetLen)
                        } else {
                            ByteArray(targetLen).also { out ->
                                System.arraycopy(template, 0, out, 0, template.size)
                            }
                        }
                    }
                }
            }
            return null
        }
    }

    private sealed class Pending(val cmd: UsbIpCmdSubmit) {
        class Async(cmd: UsbIpCmdSubmit, val request: UsbRequest, val buffer: ByteBuffer) : Pending(cmd)
        /** Runs on the control thread. */
        class Sync(cmd: UsbIpCmdSubmit) : Pending(cmd) {
            @Volatile var future: Future<*>? = null
        }
    }

    private val pending = ConcurrentHashMap<Int, Pending>()
    private val controlExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "UsbIpControl-$busId")
    }
    @Volatile private var running = true
    private val reaper = Thread({ reapLoop() }, "UsbIpReaper-$busId")

    init {
        reaper.isDaemon = true
        reaper.start()
    }

    // ---------------------------------------------------------------- submit

    fun submit(cmd: UsbIpCmdSubmit, outPayload: ByteArray?, isoDescriptors: ByteArray?) {
        if (!running) return
        if (cmd.endpoint == 0) {
            submitControl(cmd, outPayload)
            return
        }

        val ep = hostManager.findEndpoint(busId, cmd.endpoint, cmd.direction)
        if (ep == null) {
            Log.w(TAG, "seq=${cmd.seqNum}: endpoint ${cmd.endpoint} dir=${cmd.direction} not found on $busId")
            reply(cmd, UsbIpConstants.EPIPE, 0, null)
            return
        }

        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC || isoDescriptors != null) {
            // Android's public API has no isochronous transfers.
            val n = cmd.numberOfPackets.coerceAtLeast(0)
            Log.w(TAG, "seq=${cmd.seqNum}: isochronous transfers are not supported on Android (ep ${cmd.endpoint})")
            reply(cmd, 0, 0, null, errorCount = n,
                isoDescriptors = failedIsoDescriptors(isoDescriptors ?: ByteArray(0), n))
            return
        }

        val len = cmd.transferBufferLength
        val buffer = ByteBuffer.allocateDirect(len)
        if (cmd.direction == UsbIpConstants.USBIP_DIR_OUT && outPayload != null) {
            buffer.put(outPayload, 0, minOf(len, outPayload.size))
            buffer.flip()
        }

        val request = UsbRequest()
        if (!request.initialize(connection, ep)) {
            Log.w(TAG, "seq=${cmd.seqNum}: UsbRequest.initialize failed for ep ${cmd.endpoint}")
            request.close()
            failSubmit(cmd)
            return
        }
        val p = Pending.Async(cmd, request, buffer)
        request.clientData = p
        pending[cmd.seqNum] = p

        val queued = try {
            request.queue(buffer)
        } catch (e: Exception) {
            Log.w(TAG, "seq=${cmd.seqNum}: queue threw ${e.javaClass.simpleName}: ${e.message}")
            false
        }
        if (!queued) {
            if (pending.remove(cmd.seqNum, p)) {
                request.close()
                failSubmit(cmd)
            }
        }
    }

    /** Queueing failed: the device is probably gone. */
    private fun failSubmit(cmd: UsbIpCmdSubmit) {
        if (hostManager.findNativeDevice(busId) == null) {
            Log.w(TAG, "Device $busId disappeared, closing session")
            reply(cmd, UsbIpConstants.ENODEV, 0, null)
            onFatal()
        } else {
            reply(cmd, UsbIpConstants.EPIPE, 0, null)
        }
    }

    private fun submitControl(cmd: UsbIpCmdSubmit, outPayload: ByteArray?) {
        val p = Pending.Sync(cmd)
        pending[cmd.seqNum] = p
        try {
            p.future = controlExecutor.submit {
                if (pending[cmd.seqNum] !== p) return@submit // unlinked before it started
                val result = runControl(cmd, outPayload)
                if (pending.remove(cmd.seqNum, p)) {
                    reply(cmd, result.first, result.second?.size ?: 0, result.second, actualOverride = result.third)
                }
            }
        } catch (e: Exception) {
            pending.remove(cmd.seqNum, p)
        }
    }

    /** Returns (status, IN data, actual length for OUT). */
    private fun runControl(cmd: UsbIpCmdSubmit, outPayload: ByteArray?): Triple<Int, ByteArray?, Int?> {
        val s = cmd.setup
        val bmRequestType = s[0].toInt() and 0xFF
        val bRequest = s[1].toInt() and 0xFF
        val wValue = (s[2].toInt() and 0xFF) or ((s[3].toInt() and 0xFF) shl 8)
        val wIndex = (s[4].toInt() and 0xFF) or ((s[5].toInt() and 0xFF) shl 8)
        val wLength = (s[6].toInt() and 0xFF) or ((s[7].toInt() and 0xFF) shl 8)

        when (classifyControl(s)) {
            ACTION_SET_CONFIGURATION -> {
                // Android has already configured the device and we hold its interfaces;
                // re-selecting the active configuration must just succeed.
                if (!hostManager.isCurrentConfiguration(busId, wValue)) {
                    Log.w(TAG, "SET_CONFIGURATION($wValue) on $busId: only the default configuration is supported, faking success")
                }
                hostManager.resetAltSettings(busId)
                return Triple(UsbIpConstants.USBIP_STATUS_OK, null, 0)
            }
            ACTION_SET_INTERFACE -> {
                val ok = hostManager.setInterface(busId, connection, wIndex, wValue)
                if (!ok) Log.w(TAG, "SET_INTERFACE(if=$wIndex alt=$wValue) failed on $busId")
                return Triple(if (ok) UsbIpConstants.USBIP_STATUS_OK else UsbIpConstants.EPIPE, null, 0)
            }
        }

        val isIn = (bmRequestType and 0x80) != 0
        return if (isIn) {
            val buffer = ByteArray(wLength)
            val n = connection.controlTransfer(bmRequestType, bRequest, wValue, wIndex, buffer, wLength, CONTROL_TIMEOUT_MS)
            if (n >= 0) {
                val actual = minOf(n, cmd.transferBufferLength.coerceAtLeast(0))
                Triple(UsbIpConstants.USBIP_STATUS_OK, buffer.copyOf(actual), null)
            } else {
                val fallback = try {
                    extractDescriptorFallback(connection.rawDescriptors, bmRequestType, bRequest, wValue, wIndex, wLength)
                } catch (_: Exception) {
                    null
                }
                if (fallback != null) {
                    val actual = minOf(fallback.size, cmd.transferBufferLength.coerceAtLeast(0))
                    Log.i(TAG, "Control IN served via rawDescriptors fallback: type=0x%02x req=0x%02x val=0x%04x idx=0x%04x (%d bytes)".format(bmRequestType, bRequest, wValue, wIndex, actual))
                    Triple(UsbIpConstants.USBIP_STATUS_OK, fallback.copyOf(actual), null)
                } else {
                    Log.w(TAG, "Control IN failed: type=0x%02x req=0x%02x val=0x%04x idx=0x%04x len=%d".format(bmRequestType, bRequest, wValue, wIndex, wLength))
                    Triple(UsbIpConstants.EPIPE, null, null)
                }
            }
        } else {
            val payload = outPayload ?: ByteArray(0)
            val len = minOf(wLength, payload.size)
            val n = connection.controlTransfer(bmRequestType, bRequest, wValue, wIndex, payload, len, CONTROL_TIMEOUT_MS)
            if (n >= 0) {
                Triple(UsbIpConstants.USBIP_STATUS_OK, null, n)
            } else if (bmRequestType == 0x21 && (bRequest == 0x0A || bRequest == 0x0B)) {
                // USB HID SET_IDLE (0x0A) and SET_PROTOCOL (0x0B) are frequently stalled by USB keyboards;
                // treat as benign success so Windows hidusb.sys completes enumeration cleanly.
                Log.i(TAG, "HID control OUT (req=0x%02x) stalled on $busId; treating as benign OK".format(bRequest))
                Triple(UsbIpConstants.USBIP_STATUS_OK, null, len)
            } else {
                Log.w(TAG, "Control OUT failed: type=0x%02x req=0x%02x val=0x%04x idx=0x%04x len=%d".format(bmRequestType, bRequest, wValue, wIndex, wLength))
                Triple(UsbIpConstants.EPIPE, null, null)
            }
        }
    }

    // ---------------------------------------------------------------- unlink

    fun unlink(seqNum: Int, unlinkSeqNum: Int) {
        val p = pending.remove(unlinkSeqNum)
        val status = if (p != null) {
            when (p) {
                is Pending.Async -> try { p.request.cancel() } catch (_: Exception) {}
                is Pending.Sync -> p.future?.cancel(false)
            }
            UsbIpConstants.ECONNRESET
        } else {
            0 // already completed: its RET_SUBMIT was (or is being) sent
        }
        try {
            synchronized(output) {
                UsbIpUrb.writeRetUnlink(output, seqNum, 0, 0, 0, status)
            }
        } catch (e: Exception) {
            Log.d(TAG, "RET_UNLINK write failed: ${e.message}")
            onFatal()
        }
    }

    // ---------------------------------------------------------------- completion

    private fun reapLoop() {
        while (running) {
            val req = try {
                connection.requestWait(REAP_POLL_MS)
            } catch (_: TimeoutException) {
                continue
            } catch (e: Exception) {
                if (running) Log.w(TAG, "requestWait failed: ${e.javaClass.simpleName}: ${e.message}")
                if (!running) break
                try { Thread.sleep(50) } catch (_: InterruptedException) { break }
                continue
            } ?: continue
            complete(req)
        }
    }

    private fun complete(req: UsbRequest) {
        val p = req.clientData as? Pending.Async
        if (p == null) {
            try { req.close() } catch (_: Exception) {}
            return
        }
        val cmd = p.cmd
        val transferred = p.buffer.position()
        val owned = pending.remove(cmd.seqNum, p)
        try { req.close() } catch (_: Exception) {}
        if (!owned) return // unlinked: the client already got RET_UNLINK

        if (cmd.direction == UsbIpConstants.USBIP_DIR_IN) {
            val data = ByteArray(transferred)
            p.buffer.flip()
            p.buffer.get(data)
            reply(cmd, UsbIpConstants.USBIP_STATUS_OK, transferred, data)
        } else if (cmd.transferBufferLength > 0 && transferred == 0) {
            // Android leaves the buffer untouched when the transfer fails
            Log.w(TAG, "seq=${cmd.seqNum}: OUT transfer on ep ${cmd.endpoint} failed")
            reply(cmd, UsbIpConstants.EPIPE, 0, null)
        } else {
            reply(cmd, UsbIpConstants.USBIP_STATUS_OK, transferred, null)
        }
    }

    private fun reply(
        cmd: UsbIpCmdSubmit,
        status: Int,
        actualLength: Int,
        data: ByteArray?,
        errorCount: Int = 0,
        isoDescriptors: ByteArray? = null,
        actualOverride: Int? = null
    ) {
        val actual = actualOverride ?: actualLength
        try {
            synchronized(output) {
                UsbIpUrb.writeRetSubmit(
                    output = output,
                    seqNum = cmd.seqNum,
                    devId = 0,
                    direction = cmd.direction,
                    endpoint = 0,
                    status = status,
                    actualLength = actual,
                    startFrame = 0,
                    numberOfPackets = cmd.numberOfPackets,
                    errorCount = errorCount,
                    setup = null,
                    data = data,
                    isoDescriptors = isoDescriptors
                )
            }
            onPacketTransferred(1, if (cmd.direction == UsbIpConstants.USBIP_DIR_IN) actual.toLong() else cmd.transferBufferLength.toLong())
        } catch (e: Exception) {
            Log.d(TAG, "RET_SUBMIT write failed: ${e.message}")
            onFatal()
        }
    }

    // ---------------------------------------------------------------- shutdown

    fun close() {
        if (!running) return
        running = false
        controlExecutor.shutdownNow()
        for (p in pending.values) {
            if (p is Pending.Async) try { p.request.cancel() } catch (_: Exception) {}
        }
        pending.clear()
        try { reaper.join(REAP_POLL_MS * 3) } catch (_: InterruptedException) {}
    }
}
