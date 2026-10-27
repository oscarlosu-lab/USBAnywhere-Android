package com.usbanywhere.protocol

import java.io.DataInputStream
import java.io.DataOutputStream

data class UsbIpCmdSubmit(
    val command: Int,
    val seqNum: Int,
    val devId: Int,
    val direction: Int, // 0 = OUT, 1 = IN
    val endpoint: Int,
    val transferFlags: Int,
    val transferBufferLength: Int,
    val startFrame: Int,
    val numberOfPackets: Int,
    val interval: Int,
    val setup: ByteArray // 8 bytes setup packet for control transfers
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UsbIpCmdSubmit
        return seqNum == other.seqNum && devId == other.devId && command == other.command
    }

    override fun hashCode(): Int {
        var result = command
        result = 31 * result + seqNum
        result = 31 * result + devId
        return result
    }
}

object UsbIpUrb {

    /**
     * Reads a 48-byte USBIP_CMD_SUBMIT header.
     */
    fun readCmdSubmit(input: DataInputStream): UsbIpCmdSubmit {
        val command = input.readInt()
        val seqNum = input.readInt()
        val devId = input.readInt()
        val direction = input.readInt()
        val endpoint = input.readInt()
        val transferFlags = input.readInt()
        val transferBufferLength = input.readInt()
        val startFrame = input.readInt()
        val numberOfPackets = input.readInt()
        val interval = input.readInt()
        val setup = ByteArray(8)
        input.readFully(setup)

        return UsbIpCmdSubmit(
            command = command,
            seqNum = seqNum,
            devId = devId,
            direction = direction,
            endpoint = endpoint,
            transferFlags = transferFlags,
            transferBufferLength = transferBufferLength,
            startFrame = startFrame,
            numberOfPackets = numberOfPackets,
            interval = interval,
            setup = setup
        )
    }

    /**
     * Writes a 48-byte USBIP_RET_SUBMIT header followed by payload (if direction == IN).
     */
    fun writeRetSubmit(
        output: DataOutputStream,
        seqNum: Int,
        devId: Int,
        direction: Int,
        endpoint: Int,
        status: Int,
        actualLength: Int,
        startFrame: Int,
        numberOfPackets: Int,
        errorCount: Int,
        setup: ByteArray?,
        data: ByteArray?
    ) {
        output.writeInt(UsbIpConstants.USBIP_RET_SUBMIT)
        output.writeInt(seqNum)
        output.writeInt(devId)
        output.writeInt(direction)
        output.writeInt(endpoint)
        output.writeInt(status)
        output.writeInt(actualLength)
        output.writeInt(startFrame)
        output.writeInt(numberOfPackets)
        output.writeInt(errorCount)

        if (setup != null && setup.size == 8) {
            output.write(setup)
        } else {
            output.write(ByteArray(8)) // 8 bytes padding
        }

        // If Device -> Host transfer (IN) and length > 0, write received payload
        if (direction == UsbIpConstants.USBIP_DIR_IN && actualLength > 0 && data != null) {
            output.write(data, 0, actualLength)
        }
        output.flush()
    }

    /**
     * Writes a 48-byte USBIP_RET_UNLINK header.
     */
    fun writeRetUnlink(
        output: DataOutputStream,
        seqNum: Int,
        devId: Int,
        direction: Int,
        endpoint: Int,
        status: Int
    ) {
        output.writeInt(UsbIpConstants.USBIP_RET_UNLINK)
        output.writeInt(seqNum)
        output.writeInt(devId)
        output.writeInt(direction)
        output.writeInt(endpoint)
        output.writeInt(status)
        output.write(ByteArray(24)) // 24 bytes padding
        output.flush()
    }
}
