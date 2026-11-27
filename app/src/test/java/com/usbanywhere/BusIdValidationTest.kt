package com.usbanywhere

import com.usbanywhere.server.UsbIpServerService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusIdValidationTest {
    @Test
    fun validBusIdsAccepted() {
        assertTrue(UsbIpServerService.isValidBusId("1-1"))
        assertTrue(UsbIpServerService.isValidBusId("2-1.3"))
        assertTrue(UsbIpServerService.isValidBusId(" 10-2.1.4 "))
    }

    @Test
    fun invalidOrInjectedBusIdsRejected() {
        assertFalse(UsbIpServerService.isValidBusId(null))
        assertFalse(UsbIpServerService.isValidBusId(""))
        assertFalse(UsbIpServerService.isValidBusId("1-1;id"))
        assertFalse(UsbIpServerService.isValidBusId("../etc/passwd"))
        assertFalse(UsbIpServerService.isValidBusId("$(whoami)"))
    }
}
