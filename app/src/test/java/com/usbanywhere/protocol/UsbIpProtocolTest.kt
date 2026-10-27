package com.usbanywhere.protocol

import com.usbanywhere.client.bridge.VirtualInputBridge
import com.usbanywhere.data.UsbExportedDevice
import com.usbanywhere.data.UsbInterfaceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class UsbIpProtocolTest {

    @Test
    fun testFixedStringRoundTrip() {
        val testStr = "1-1.3"
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        UsbIpPackets.writeFixedString(dos, testStr, UsbIpConstants.SYSFS_BUSID_SIZE)
        dos.flush()

        val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))
        val recovered = UsbIpPackets.readFixedString(dis, UsbIpConstants.SYSFS_BUSID_SIZE)

        assertEquals(testStr, recovered)
    }

    @Test
    fun testRepDevlistRoundTrip() {
        val interfaces = listOf(
            UsbInterfaceInfo(0, 3, 0, 0, 1),
            UsbInterfaceInfo(1, 3, 1, 2, 2)
        )
        val originalDevice = UsbExportedDevice(
            busId = "1-1",
            path = "/sys/devices/usb1/1-1",
            busNum = 1,
            devNum = 42,
            speed = UsbIpConstants.USB_SPEED_HIGH,
            vendorId = 0x046D,
            productId = 0xC24F,
            bcdDevice = 0x0200,
            deviceClass = 0,
            deviceSubclass = 0,
            deviceProtocol = 0,
            configurationValue = 1,
            numConfigurations = 1,
            interfaces = interfaces,
            productName = "Logitech G Pro Gaming Mouse",
            manufacturerName = "Logitech"
        )

        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        UsbIpPackets.writeRepDevlist(dos, listOf(originalDevice))
        dos.flush()

        val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))
        val version = dis.readShort().toInt()
        val opcode = dis.readShort()
        val status = dis.readInt()

        assertEquals(UsbIpConstants.USBIP_VERSION, version)
        assertEquals(UsbIpConstants.OP_REP_DEVLIST, opcode)
        assertEquals(UsbIpConstants.USBIP_STATUS_OK, status)

        val recoveredList = UsbIpPackets.readRepDevlist(dis)
        assertEquals(1, recoveredList.size)

        val recoveredDev = recoveredList[0]
        assertEquals("1-1", recoveredDev.busId)
        assertEquals(0x046D, recoveredDev.vendorId)
        assertEquals(0xC24F, recoveredDev.productId)
        assertEquals(UsbIpConstants.USB_SPEED_HIGH, recoveredDev.speed)
        assertEquals(2, recoveredDev.interfaces.size)
        assertEquals(3, recoveredDev.interfaces[0].interfaceClass)
    }

    @Test
    fun testUrbReturnSubmitRoundTrip() {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        val seqNum = 1001
        val devId = 42
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)

        UsbIpUrb.writeRetSubmit(
            output = dos,
            seqNum = seqNum,
            devId = devId,
            direction = UsbIpConstants.USBIP_DIR_IN,
            endpoint = 1,
            status = UsbIpConstants.USBIP_STATUS_OK,
            actualLength = payload.size,
            startFrame = 0,
            numberOfPackets = 0,
            errorCount = 0,
            setup = null,
            data = payload
        )
        dos.flush()

        val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))
        val cmd = dis.readInt()
        val readSeq = dis.readInt()
        val readDev = dis.readInt()
        val readDir = dis.readInt()
        val readEp = dis.readInt()
        val readStatus = dis.readInt()
        val readLen = dis.readInt()
        dis.readInt() // startFrame
        dis.readInt() // numPackets
        dis.readInt() // errorCount
        val setup = ByteArray(8)
        dis.readFully(setup)

        val readPayload = ByteArray(readLen)
        dis.readFully(readPayload)

        assertEquals(UsbIpConstants.USBIP_RET_SUBMIT, cmd)
        assertEquals(seqNum, readSeq)
        assertEquals(devId, readDev)
        assertEquals(UsbIpConstants.USBIP_DIR_IN, readDir)
        assertEquals(1, readEp)
        assertEquals(UsbIpConstants.USBIP_STATUS_OK, readStatus)
        assertEquals(payload.size, readLen)
        assertEquals(0x05.toByte(), readPayload[4])
    }

    @Test
    fun testVirtualInputBridgeDecoding() {
        val bridge = VirtualInputBridge()
        // Generic HID report: 8 bytes (lx, ly, rx, ry, buttons, ...)
        val report = byteArrayOf(
            0x80.toByte(), // lx centered (128 -> 0f)
            0x00.toByte(), // ly full up (0 -> -1f)
            0x80.toByte(), // rx centered
            0x80.toByte(), // ry centered
            0x01.toByte(), // Button A pressed (bit 0)
            0x00, 0x00, 0x00
        )

        val state = bridge.decodeHidReport(report)
        assertNotNull(state)
        assertEquals(0f, state.leftStickX, 0.01f)
        assertEquals(-1f, state.leftStickY, 0.01f)
        assertTrue(state.buttonA)
        assertEquals(false, state.buttonB)
    }
}
