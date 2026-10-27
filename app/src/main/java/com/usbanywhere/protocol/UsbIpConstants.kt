package com.usbanywhere.protocol

object UsbIpConstants {
    const val DEFAULT_PORT = 3240
    const val USBIP_VERSION = 0x0111 // USB/IP protocol version 1.1.1

    // Management OP Codes (Handshake)
    const val OP_REQ_DEVLIST: Short = 0x8005.toShort()
    const val OP_REP_DEVLIST: Short = 0x0005.toShort()
    const val OP_REQ_IMPORT: Short  = 0x8003.toShort()
    const val OP_REP_IMPORT: Short  = 0x0003.toShort()

    // Zero-Trust Security Extension OP Codes
    const val OP_REQ_AUTH: Short    = 0x8008.toShort()
    const val OP_REP_AUTH: Short    = 0x0008.toShort()

    const val AUTH_STATUS_SUCCESS      = 0
    const val AUTH_STATUS_BAD_PIN      = 1
    const val AUTH_STATUS_UNAUTHORIZED = 2
    const val AUTH_STATUS_TIMEOUT      = 3

    // URB Transfer Command Codes (Streaming)
    const val USBIP_CMD_SUBMIT = 0x00000001
    const val USBIP_RET_SUBMIT = 0x00000003
    const val USBIP_CMD_UNLINK = 0x00000002
    const val USBIP_RET_UNLINK = 0x00000004

    // Direction
    const val USBIP_DIR_OUT = 0 // Host -> Device
    const val USBIP_DIR_IN  = 1 // Device -> Host

    // Return Status Codes
    const val USBIP_STATUS_OK = 0
    const val USBIP_STATUS_ERROR = 1

    // Speeds
    const val USB_SPEED_UNKNOWN = 0
    const val USB_SPEED_LOW = 1
    const val USB_SPEED_FULL = 2
    const val USB_SPEED_HIGH = 3
    const val USB_SPEED_WIRELESS = 4
    const val USB_SPEED_SUPER = 5

    // Sizes
    const val SYSFS_PATH_MAX = 256
    const val SYSFS_BUSID_SIZE = 32
}
