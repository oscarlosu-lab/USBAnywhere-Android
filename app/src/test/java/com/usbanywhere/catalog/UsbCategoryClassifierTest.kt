package com.usbanywhere.catalog

import com.usbanywhere.data.UsbInterfaceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class UsbCategoryClassifierTest {

    @Test
    fun testRealtekCameraClassification() {
        // Realtek Camera: DeviceClass 0xEF, Interfaces: 0x0E (Video) + 0x01 (Audio)
        val ifaces = listOf(
            UsbInterfaceInfo(0, 0x0E, 1, 0),
            UsbInterfaceInfo(1, 0x0E, 2, 0),
            UsbInterfaceInfo(2, 0x01, 1, 0)
        )
        val cat = UsbCategoryClassifier.classify(
            deviceClass = 0xEF,
            deviceSubclass = 2,
            deviceProtocol = 1,
            interfaces = ifaces,
            productName = "USB Camera",
            vendorName = "Realtek Semiconductor Corp."
        )
        assertEquals(UsbCategory.CAMERA, cat)
    }

    @Test
    fun testCh340SerialPrinterClassification() {
        // CH340 Serial: DeviceClass 0xFF, InterfaceClass 0xFF
        val ifaces = listOf(
            UsbInterfaceInfo(0, 0xFF, 1, 2)
        )
        val cat = UsbCategoryClassifier.classify(
            deviceClass = 0xFF,
            deviceSubclass = 0,
            deviceProtocol = 0,
            interfaces = ifaces,
            productName = "CH340 serial converter",
            vendorName = "QinHeng Electronics"
        )
        assertEquals(UsbCategory.PRINTER_3D, cat)
    }

    @Test
    fun testDualSenseControllerClassification() {
        val ifaces = listOf(
            UsbInterfaceInfo(0, 0x03, 0, 0)
        )
        val cat = UsbCategoryClassifier.classify(
            deviceClass = 0,
            deviceSubclass = 0,
            deviceProtocol = 0,
            interfaces = ifaces,
            productName = "DualSense Wireless Controller",
            vendorName = "Sony Interactive Entertainment"
        )
        assertEquals(UsbCategory.CONTROLLER, cat)
    }

    @Test
    fun testSteeringWheelClassification() {
        val ifaces = listOf(
            UsbInterfaceInfo(0, 0x03, 0, 0)
        )
        val cat = UsbCategoryClassifier.classify(
            deviceClass = 0,
            deviceSubclass = 0,
            deviceProtocol = 0,
            interfaces = ifaces,
            productName = "G29 Driving Force Racing Wheel",
            vendorName = "Logitech, Inc."
        )
        assertEquals(UsbCategory.STEERING_WHEEL, cat)
    }

    @Test
    fun testMouseClassification() {
        // HID Boot Mouse: class 3, subclass 1, protocol 2
        val ifaces = listOf(
            UsbInterfaceInfo(0, 3, 1, 2)
        )
        val cat = UsbCategoryClassifier.classify(
            deviceClass = 0,
            deviceSubclass = 0,
            deviceProtocol = 0,
            interfaces = ifaces,
            productName = "Optical Gaming Mouse",
            vendorName = "Generic"
        )
        assertEquals(UsbCategory.MOUSE, cat)
    }

    @Test
    fun testCatalogResolution() {
        val identityCam = UsbDeviceCatalog.resolve(
            vendorId = 0x0BDA,
            productId = 0x5100,
            deviceClass = 0xEF,
            deviceSubclass = 2,
            deviceProtocol = 1,
            interfaces = listOf(UsbInterfaceInfo(0, 0x0E, 1, 0))
        )
        assertEquals(UsbCategory.CAMERA, identityCam.category)
        assertEquals("Realtek USB Camera", identityCam.productName)

        val identitySerial = UsbDeviceCatalog.resolve(
            vendorId = 0x1A86,
            productId = 0x7523,
            deviceClass = 0xFF,
            deviceSubclass = 0,
            deviceProtocol = 0,
            interfaces = listOf(UsbInterfaceInfo(0, 0xFF, 1, 2))
        )
        assertEquals(UsbCategory.PRINTER_3D, identitySerial.category)
    }
}
