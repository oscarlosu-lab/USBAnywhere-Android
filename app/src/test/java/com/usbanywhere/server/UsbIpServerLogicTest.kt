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

    @Test
    fun extractDescriptorFallbackServesDeviceConfigAndHidReportForKeyboard() {
        // Synthetic USB keyboard rawDescriptors: 18B Device + 9B Config + 9B Interface (HID Boot Keyboard) + 9B HID (report len 63) + 7B Endpoint
        val raw = byteArrayOf(
            // Device descriptor (18B)
            18, 0x01, 0x00, 0x02, 0x00, 0x00, 0x00, 64, 0x37, 0x38, 0x0D, 0x30, 0x00, 0x01, 1, 2, 0, 1,
            // Config descriptor (9B, totalLength = 34 = 0x0022)
            9, 0x02, 34, 0x00, 1, 1, 0, 0xA0.toByte(), 50,
            // Interface descriptor (9B, iface=0, class=3 HID, subclass=1 Boot, protocol=1 Keyboard)
            9, 0x04, 0, 0, 1, 0x03, 0x01, 0x01, 0,
            // HID descriptor (9B, report descriptor length = 63 = 0x003F)
            9, 0x21, 0x11, 0x01, 0, 1, 0x22, 63, 0x00,
            // Endpoint descriptor (7B)
            7, 0x05, 0x81.toByte(), 0x03, 8, 0x00, 10
        )

        val devDesc = UrbSession.extractDescriptorFallback(raw, 0x80, 0x06, 0x0100, 0, 64)!!
        assertEquals(18, devDesc.size)
        assertEquals(0x37, devDesc[8].toInt() and 0xFF)
        assertEquals(0x38, devDesc[9].toInt() and 0xFF)

        val cfgDesc = UrbSession.extractDescriptorFallback(raw, 0x80, 0x06, 0x0200, 0, 255)!!
        assertEquals(34, cfgDesc.size)

        val hidDesc = UrbSession.extractDescriptorFallback(raw, 0x81, 0x06, 0x2100, 0, 9)!!
        assertEquals(9, hidDesc.size)
        assertEquals(0x21, hidDesc[1].toInt() and 0xFF)

        val repDesc = UrbSession.extractDescriptorFallback(raw, 0x81, 0x06, 0x2200, 0, 63)!!
        assertEquals(63, repDesc.size)
        assertEquals(0x05, repDesc[0].toInt() and 0xFF)
        assertEquals(0xC0, repDesc[62].toInt() and 0xFF)
    }
}
