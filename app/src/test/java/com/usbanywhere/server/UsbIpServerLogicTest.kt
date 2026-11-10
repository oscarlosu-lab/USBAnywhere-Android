package com.usbanywhere.server

import android.hardware.usb.UsbConstants
import com.usbanywhere.protocol.UsbIpConstants
import com.usbanywhere.protocol.UsbIpUrb
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

class UsbIpServerLogicTest {

    private val bulk = UsbConstants.USB_ENDPOINT_XFER_BULK
    private val intr = UsbConstants.USB_ENDPOINT_XFER_INT
    private val iso = UsbConstants.USB_ENDPOINT_XFER_ISOC

    @Test
    fun speedFullSpeedGamepadDongle() {
        assertEquals(UsbIpConstants.USB_SPEED_FULL, UsbHostManager.deduceSpeed(listOf(intr to 32, intr to 32)))
    }

    @Test
    fun speedHighSpeedDisk() {
        assertEquals(UsbIpConstants.USB_SPEED_HIGH, UsbHostManager.deduceSpeed(listOf(bulk to 512, bulk to 512)))
    }

    @Test
    fun speedSuperSpeedDisk() {
        assertEquals(UsbIpConstants.USB_SPEED_SUPER, UsbHostManager.deduceSpeed(listOf(bulk to 1024, bulk to 1024, intr to 8)))
    }

    @Test
    fun speedHighSpeedInterruptAndIso() {
        assertEquals(UsbIpConstants.USB_SPEED_HIGH, UsbHostManager.deduceSpeed(listOf(intr to 1024)))
        assertEquals(UsbIpConstants.USB_SPEED_HIGH, UsbHostManager.deduceSpeed(listOf(iso to (0x0800 or 512))))
        assertEquals(UsbIpConstants.USB_SPEED_FULL, UsbHostManager.deduceSpeed(listOf(iso to 192, bulk to 64)))
    }

    @Test
    fun busIdIsStableFromDeviceName() {
        assertEquals("1-5", UsbHostManager.busIdFor("/dev/bus/usb/001/005", 1005))
        assertEquals("2-12", UsbHostManager.busIdFor("/dev/bus/usb/002/012", 2012))
        assertEquals("1-77", UsbHostManager.busIdFor("weird", 77))
    }

    @Test
    fun controlClassification() {
        fun setup(t: Int, r: Int) = byteArrayOf(t.toByte(), r.toByte(), 1, 0, 0, 0, 0, 0)
        assertEquals(UrbSession.ACTION_SET_CONFIGURATION, UrbSession.classifyControl(setup(0x00, 0x09)))
        assertEquals(UrbSession.ACTION_SET_INTERFACE, UrbSession.classifyControl(setup(0x01, 0x0B)))
        assertEquals(UrbSession.ACTION_PASSTHROUGH, UrbSession.classifyControl(setup(0x80, 0x06))) // GET_DESCRIPTOR
        assertEquals(UrbSession.ACTION_PASSTHROUGH, UrbSession.classifyControl(setup(0x21, 0x09))) // HID SET_REPORT
        assertEquals(UrbSession.ACTION_PASSTHROUGH, UrbSession.classifyControl(setup(0x02, 0x01))) // CLEAR_FEATURE(HALT)
    }

    @Test
    fun failedIsoDescriptorsKeepOffsetsAndReportExdev() {
        val cmd = ByteBuffer.allocate(32)
            .putInt(0).putInt(192).putInt(0).putInt(0)
            .putInt(192).putInt(192).putInt(0).putInt(0)
            .array()
        val out = ByteBuffer.wrap(UrbSession.failedIsoDescriptors(cmd, 2))
        assertEquals(0, out.int); assertEquals(192, out.int); assertEquals(0, out.int); assertEquals(UsbIpConstants.EXDEV, out.int)
        assertEquals(192, out.int); assertEquals(192, out.int); assertEquals(0, out.int); assertEquals(UsbIpConstants.EXDEV, out.int)
    }

    @Test
    fun retSubmitWithErrnoStatusAndIsoDescriptors() {
        val bos = ByteArrayOutputStream()
        val iso = UrbSession.failedIsoDescriptors(ByteArray(0), 1)
        UsbIpUrb.writeRetSubmit(DataOutputStream(bos), 7, 0, UsbIpConstants.USBIP_DIR_IN, 0,
            UsbIpConstants.EPIPE, 0, 0, 1, 1, null, null, iso)
        val bytes = bos.toByteArray()
        assertEquals(48 + 16, bytes.size)
        val dis = DataInputStream(ByteArrayInputStream(bytes))
        assertEquals(UsbIpConstants.USBIP_RET_SUBMIT, dis.readInt())
        assertEquals(7, dis.readInt())
        repeat(3) { dis.readInt() }
        assertEquals(-32, dis.readInt())
    }
}
