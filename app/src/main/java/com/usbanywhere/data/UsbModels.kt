package com.usbanywhere.data

import com.usbanywhere.catalog.DeviceIdentity
import com.usbanywhere.catalog.UsbCategory
import com.usbanywhere.catalog.UsbDeviceCatalog

data class UsbInterfaceInfo(
    val interfaceNumber: Int,
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
    val endpointCount: Int = 0
)

data class UsbExportedDevice(
    val busId: String,
    val path: String,
    val busNum: Int,
    val devNum: Int,
    val speed: Int,
    val vendorId: Int,
    val productId: Int,
    val bcdDevice: Int = 0x0100,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val configurationValue: Int = 1,
    val numConfigurations: Int = 1,
    val interfaces: List<UsbInterfaceInfo>,
    val productName: String = "USB Device",
    val manufacturerName: String = "Generic",
    var isShared: Boolean = true,
    var isAttached: Boolean = false,
    var packetsTransferred: Long = 0,
    var bytesTransferred: Long = 0
) {
    val identity: DeviceIdentity by lazy {
        UsbDeviceCatalog.resolve(
            vendorId = vendorId,
            productId = productId,
            deviceClass = deviceClass,
            deviceSubclass = deviceSubclass,
            deviceProtocol = deviceProtocol,
            interfaces = interfaces
        )
    }

    val category: UsbCategory
        get() = identity.category

    val friendlyName: String
        get() = if (productName.isNotBlank() && productName != "USB Device") {
            productName
        } else {
            identity.productName
        }

    val displayName: String
        get() = "$friendlyName ($vendorHex:$productHex)"

    val vendorHex: String
        get() = String.format("%04X", vendorId)

    val productHex: String
        get() = String.format("%04X", productId)

    val speedLabel: String
        get() = when (speed) {
            1 -> "Low Speed (1.5 Mbps)"
            2 -> "Full Speed (12 Mbps)"
            3 -> "High Speed (480 Mbps)"
            5 -> "Super Speed (5 Gbps)"
            else -> "Standard"
        }
}

data class RemoteServer(
    val ipAddress: String,
    val port: Int = 3240,
    val hostName: String = "Remote Host",
    val latencyMs: Long = 0,
    val isConnected: Boolean = false,
    val devices: List<UsbExportedDevice> = emptyList()
)

data class ServerRuntimeStats(
    val isRunning: Boolean = false,
    val listeningIp: String = "0.0.0.0",
    val listeningPort: Int = 3240,
    val activeClientsCount: Int = 0,
    val totalPacketsSent: Long = 0,
    val totalPacketsReceived: Long = 0,
    val totalBytesSent: Long = 0,
    val totalBytesReceived: Long = 0,
    val currentPollingRateHz: Double = 0.0,
    val lastError: String? = null
)

enum class TunnelState {
    DISCONNECTED,
    CONNECTING,
    ACTIVE,
    RECONNECTING
}
