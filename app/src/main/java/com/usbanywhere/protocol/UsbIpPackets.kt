package com.usbanywhere.protocol

import com.usbanywhere.data.UsbExportedDevice
import com.usbanywhere.data.UsbInterfaceInfo
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

object UsbIpPackets {

    /**
     * Reads a null-terminated or fixed-size string from DataInputStream.
     */
    fun readFixedString(input: DataInputStream, length: Int): String {
        val bytes = ByteArray(length)
        input.readFully(bytes)
        val nullIdx = bytes.indexOf(0)
        val effectiveLen = if (nullIdx >= 0) nullIdx else length
        return String(bytes, 0, effectiveLen, StandardCharsets.UTF_8).trim()
    }

    /**
     * Writes a string padded with zeros up to fixed length.
     */
    fun writeFixedString(output: DataOutputStream, str: String, length: Int) {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteArray(length)
        val copyLen = minOf(bytes.size, length)
        System.arraycopy(bytes, 0, buffer, 0, copyLen)
        output.write(buffer)
    }

    /**
     * Serializes OP_REP_DEVLIST containing the list of exported USB devices.
     */
    fun writeRepDevlist(output: DataOutputStream, devices: List<UsbExportedDevice>) {
        output.writeShort(UsbIpConstants.USBIP_VERSION)
        output.writeShort(UsbIpConstants.OP_REP_DEVLIST.toInt())
        output.writeInt(UsbIpConstants.USBIP_STATUS_OK)
        output.writeInt(devices.size)

        for (dev in devices) {
            writeFixedString(output, dev.path, UsbIpConstants.SYSFS_PATH_MAX)
            writeFixedString(output, dev.busId, UsbIpConstants.SYSFS_BUSID_SIZE)
            output.writeInt(dev.busNum)
            output.writeInt(dev.devNum)
            output.writeInt(dev.speed)
            output.writeShort(dev.vendorId)
            output.writeShort(dev.productId)
            output.writeShort(dev.bcdDevice)
            output.writeByte(dev.deviceClass)
            output.writeByte(dev.deviceSubclass)
            output.writeByte(dev.deviceProtocol)
            output.writeByte(dev.configurationValue)
            output.writeByte(dev.numConfigurations)
            output.writeByte(dev.interfaces.size)

            for (iface in dev.interfaces) {
                output.writeByte(iface.interfaceClass)
                output.writeByte(iface.interfaceSubclass)
                output.writeByte(iface.interfaceProtocol)
                output.writeByte(0) // padding byte
            }
        }
        output.flush()
    }

    /**
     * Deserializes OP_REP_DEVLIST response on client side.
     */
    fun readRepDevlist(input: DataInputStream): List<UsbExportedDevice> {
        val numDevices = input.readInt()
        val devices = mutableListOf<UsbExportedDevice>()

        for (i in 0 until numDevices) {
            val path = readFixedString(input, UsbIpConstants.SYSFS_PATH_MAX)
            val busId = readFixedString(input, UsbIpConstants.SYSFS_BUSID_SIZE)
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

            val interfaces = mutableListOf<UsbInterfaceInfo>()
            for (j in 0 until numInterfaces) {
                val ifClass = input.readUnsignedByte()
                val ifSubclass = input.readUnsignedByte()
                val ifProtocol = input.readUnsignedByte()
                input.readByte() // discard padding
                interfaces.add(UsbInterfaceInfo(j, ifClass, ifSubclass, ifProtocol))
            }

            devices.add(
                UsbExportedDevice(
                    busId = busId,
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
                    interfaces = interfaces
                )
            )
        }
        return devices
    }

    /**
     * Writes OP_REP_IMPORT response.
     */
    fun writeRepImport(output: DataOutputStream, status: Int, dev: UsbExportedDevice?) {
        output.writeShort(UsbIpConstants.USBIP_VERSION)
        output.writeShort(UsbIpConstants.OP_REP_IMPORT.toInt())
        output.writeInt(status)

        if (status == UsbIpConstants.USBIP_STATUS_OK && dev != null) {
            writeFixedString(output, dev.path, UsbIpConstants.SYSFS_PATH_MAX)
            writeFixedString(output, dev.busId, UsbIpConstants.SYSFS_BUSID_SIZE)
            output.writeInt(dev.busNum)
            output.writeInt(dev.devNum)
            output.writeInt(dev.speed)
            output.writeShort(dev.vendorId)
            output.writeShort(dev.productId)
            output.writeShort(dev.bcdDevice)
            output.writeByte(dev.deviceClass)
            output.writeByte(dev.deviceSubclass)
            output.writeByte(dev.deviceProtocol)
            output.writeByte(dev.configurationValue)
            output.writeByte(dev.numConfigurations)
            output.writeByte(dev.interfaces.size)
        }
        output.flush()
    }
}
