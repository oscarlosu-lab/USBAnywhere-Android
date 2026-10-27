package com.usbanywhere.gaming.dualsense

/**
 * Hardware profiles and descriptor constants for Sony PlayStation controllers
 * (DualSense PS5, DualSense Edge, DualShock 4).
 */
object DualSenseProfile {

    const val SONY_VENDOR_ID = 0x054C

    // Product IDs
    const val PID_DUALSENSE_PS5 = 0x0CE6
    const val PID_DUALSENSE_EDGE = 0x0DF2
    const val PID_DUALSHOCK4_V1 = 0x05C4
    const val PID_DUALSHOCK4_V2 = 0x09CC

    // USB Interfaces
    const val INTERFACE_CLASS_AUDIO = 0x01
    const val INTERFACE_CLASS_HID = 0x03

    // DualSense specific report IDs
    const val REPORT_ID_INPUT_USB = 0x01
    const val REPORT_ID_OUTPUT_USB = 0x02
    const val USB_INPUT_REPORT_SIZE = 64
    const val USB_OUTPUT_REPORT_SIZE = 64

    fun isSonyController(vendorId: Int, productId: Int): Boolean {
        if (vendorId != SONY_VENDOR_ID) return false
        return when (productId) {
            PID_DUALSENSE_PS5, PID_DUALSENSE_EDGE,
            PID_DUALSHOCK4_V1, PID_DUALSHOCK4_V2 -> true
            else -> false
        }
    }

    fun isDualSense(vendorId: Int, productId: Int): Boolean {
        return vendorId == SONY_VENDOR_ID &&
                (productId == PID_DUALSENSE_PS5 || productId == PID_DUALSENSE_EDGE)
    }

    fun getDisplayName(vendorId: Int, productId: Int): String {
        if (vendorId != SONY_VENDOR_ID) return "Unknown Controller"
        return when (productId) {
            PID_DUALSENSE_PS5 -> "Sony DualSense (PS5)"
            PID_DUALSENSE_EDGE -> "Sony DualSense Edge"
            PID_DUALSHOCK4_V1 -> "Sony DualShock 4 v1"
            PID_DUALSHOCK4_V2 -> "Sony DualShock 4 v2"
            else -> "Sony Gamepad (0x${productId.toString(16).uppercase()})"
        }
    }
}
