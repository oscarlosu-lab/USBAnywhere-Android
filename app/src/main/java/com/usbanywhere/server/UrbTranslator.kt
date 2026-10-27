package com.usbanywhere.server

import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import com.usbanywhere.protocol.UsbIpCmdSubmit
import com.usbanywhere.protocol.UsbIpConstants

class UrbTranslator(
    private val hostManager: UsbHostManager,
    private val defaultTimeoutMs: Int = 2000
) {
    companion object {
        private const val TAG = "UrbTranslator"
    }

    data class UrbExecutionResult(
        val status: Int,
        val actualLength: Int,
        val returnData: ByteArray? = null
    )

    fun executeUrb(
        busId: String,
        connection: UsbDeviceConnection,
        cmd: UsbIpCmdSubmit,
        outPayload: ByteArray?
    ): UrbExecutionResult {
        return if (cmd.endpoint == 0) {
            executeControlTransfer(connection, cmd, outPayload)
        } else {
            executeDataTransfer(busId, connection, cmd, outPayload)
        }
    }

    private fun executeControlTransfer(
        connection: UsbDeviceConnection,
        cmd: UsbIpCmdSubmit,
        outPayload: ByteArray?
    ): UrbExecutionResult {
        val setup = cmd.setup
        val bmRequestType = setup[0].toInt() and 0xFF
        val bRequest = setup[1].toInt() and 0xFF
        val wValue = (setup[2].toInt() and 0xFF) or ((setup[3].toInt() and 0xFF) shl 8)
        val wIndex = (setup[4].toInt() and 0xFF) or ((setup[5].toInt() and 0xFF) shl 8)
        val wLength = (setup[6].toInt() and 0xFF) or ((setup[7].toInt() and 0xFF) shl 8)

        val targetLength = maxOf(wLength, cmd.transferBufferLength)

        return if (cmd.direction == UsbIpConstants.USBIP_DIR_OUT) {
            val payload = outPayload ?: ByteArray(0)
            val transferred = connection.controlTransfer(
                bmRequestType,
                bRequest,
                wValue,
                wIndex,
                payload,
                payload.size,
                defaultTimeoutMs
            )
            if (transferred >= 0) {
                UrbExecutionResult(UsbIpConstants.USBIP_STATUS_OK, transferred, null)
            } else {
                Log.w(TAG, "Control OUT transfer failed: $transferred")
                UrbExecutionResult(UsbIpConstants.USBIP_STATUS_ERROR, 0, null)
            }
        } else {
            // Direction IN (Device -> Host)
            val buffer = ByteArray(targetLength)
            val transferred = connection.controlTransfer(
                bmRequestType,
                bRequest,
                wValue,
                wIndex,
                buffer,
                buffer.size,
                defaultTimeoutMs
            )
            if (transferred >= 0) {
                UrbExecutionResult(UsbIpConstants.USBIP_STATUS_OK, transferred, buffer)
            } else {
                Log.w(TAG, "Control IN transfer failed: $transferred")
                UrbExecutionResult(UsbIpConstants.USBIP_STATUS_ERROR, 0, null)
            }
        }
    }

    private fun executeDataTransfer(
        busId: String,
        connection: UsbDeviceConnection,
        cmd: UsbIpCmdSubmit,
        outPayload: ByteArray?
    ): UrbExecutionResult {
        val endpoint = hostManager.findEndpoint(busId, cmd.endpoint, cmd.direction)
        if (endpoint == null) {
            Log.e(TAG, "Endpoint ${cmd.endpoint} not found for busId $busId (direction=${cmd.direction})")
            return UrbExecutionResult(UsbIpConstants.USBIP_STATUS_ERROR, 0, null)
        }

        return if (cmd.direction == UsbIpConstants.USBIP_DIR_OUT) {
            val payload = outPayload ?: ByteArray(0)
            val transferred = connection.bulkTransfer(
                endpoint,
                payload,
                payload.size,
                defaultTimeoutMs
            )
            if (transferred >= 0) {
                UrbExecutionResult(UsbIpConstants.USBIP_STATUS_OK, transferred, null)
            } else {
                UrbExecutionResult(UsbIpConstants.USBIP_STATUS_ERROR, 0, null)
            }
        } else {
            // Direction IN (Device -> Host)
            val buffer = ByteArray(cmd.transferBufferLength)
            val transferred = connection.bulkTransfer(
                endpoint,
                buffer,
                buffer.size,
                defaultTimeoutMs
            )
            if (transferred >= 0) {
                UrbExecutionResult(UsbIpConstants.USBIP_STATUS_OK, transferred, buffer)
            } else {
                UrbExecutionResult(UsbIpConstants.USBIP_STATUS_ERROR, 0, null)
            }
        }
    }
}
