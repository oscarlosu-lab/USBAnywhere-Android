package com.usbanywhere.server

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.usbanywhere.data.UsbExportedDevice
import com.usbanywhere.data.UsbInterfaceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class UsbHostManager(private val context: Context) {

    companion object {
        private const val TAG = "UsbHostManager"
        const val ACTION_USB_PERMISSION = "com.usbanywhere.USB_PERMISSION"
    }

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectedDevices = MutableStateFlow<List<UsbExportedDevice>>(emptyList())
    val connectedDevices: StateFlow<List<UsbExportedDevice>> = _connectedDevices.asStateFlow()

    private val activeConnections = ConcurrentHashMap<String, UsbDeviceConnection>()
    private val claimedInterfaces = ConcurrentHashMap<String, MutableList<UsbInterface>>()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        Log.i(TAG, "USB Permission result for ${device?.deviceName}: granted=$granted")
                        refreshDevices()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.i(TAG, "USB device attach/detach event received")
                    refreshDevices()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        refreshDevices()
    }

    fun destroy() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        closeAll()
    }

    /**
     * Scans currently connected OTG USB devices.
     */
    fun refreshDevices() {
        val deviceList = usbManager.deviceList
        val exportedList = mutableListOf<UsbExportedDevice>()

        var busIndex = 1
        for ((_, usbDev) in deviceList) {
            val busId = "1-$busIndex"
            val interfaces = mutableListOf<UsbInterfaceInfo>()

            for (i in 0 until usbDev.interfaceCount) {
                val iface = usbDev.getInterface(i)
                interfaces.add(
                    UsbInterfaceInfo(
                        interfaceNumber = iface.id,
                        interfaceClass = iface.interfaceClass,
                        interfaceSubclass = iface.interfaceSubclass,
                        interfaceProtocol = iface.interfaceProtocol,
                        endpointCount = iface.endpointCount
                    )
                )
            }

            val prodName = try { usbDev.productName ?: "USB Device" } catch (_: Exception) { "USB Device" }
            val mfgName = try { usbDev.manufacturerName ?: "Generic" } catch (_: Exception) { "Generic" }

            exportedList.add(
                UsbExportedDevice(
                    busId = busId,
                    path = "/sys/bus/usb/devices/$busId",
                    busNum = 1,
                    devNum = usbDev.deviceId,
                    speed = mapUsbSpeed(usbDev),
                    vendorId = usbDev.vendorId,
                    productId = usbDev.productId,
                    deviceClass = usbDev.deviceClass,
                    deviceSubclass = usbDev.deviceSubclass,
                    deviceProtocol = usbDev.deviceProtocol,
                    interfaces = interfaces,
                    productName = prodName,
                    manufacturerName = mfgName,
                    isShared = true
                )
            )
            busIndex++
        }
        _connectedDevices.value = exportedList
    }

    private fun mapUsbSpeed(device: UsbDevice): Int {
        // High Speed default for OTG host controllers on Android 8+
        return 3 // USB_SPEED_HIGH (480 Mbps)
    }

    /**
     * Request runtime permission for device if needed.
     */
    fun requestPermission(busId: String) {
        val rawDev = findNativeDevice(busId) ?: return
        if (!usbManager.hasPermission(rawDev)) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
            usbManager.requestPermission(rawDev, pendingIntent)
        }
    }

    fun hasPermission(busId: String): Boolean {
        val rawDev = findNativeDevice(busId) ?: return false
        return usbManager.hasPermission(rawDev)
    }

    fun findNativeDevice(busId: String): UsbDevice? {
        val list = usbManager.deviceList.values.toList()
        val index = busId.substringAfter("-", "1").toIntOrNull() ?: 1
        return if (index in 1..list.size) list[index - 1] else null
    }

    /**
     * Opens USB connection and claims all interfaces.
     */
    @Synchronized
    fun openDevice(busId: String): UsbDeviceConnection? {
        activeConnections[busId]?.let { return it }

        val rawDev = findNativeDevice(busId) ?: return null
        if (!usbManager.hasPermission(rawDev)) {
            Log.e(TAG, "Cannot open device $busId: Missing permission")
            return null
        }

        val conn = usbManager.openDevice(rawDev) ?: run {
            Log.e(TAG, "UsbManager.openDevice failed for $busId")
            return null
        }

        val claimed = mutableListOf<UsbInterface>()
        for (i in 0 until rawDev.interfaceCount) {
            val iface = rawDev.getInterface(i)
            val ok = conn.claimInterface(iface, true)
            if (ok) {
                claimed.add(iface)
                Log.d(TAG, "Claimed interface ${iface.id} on $busId")
            } else {
                Log.w(TAG, "Failed to claim interface ${iface.id} on $busId")
            }
        }

        activeConnections[busId] = conn
        claimedInterfaces[busId] = claimed
        return conn
    }

    @Synchronized
    fun closeDevice(busId: String) {
        val conn = activeConnections.remove(busId)
        val claimed = claimedInterfaces.remove(busId)
        if (conn != null && claimed != null) {
            for (iface in claimed) {
                try { conn.releaseInterface(iface) } catch (_: Exception) {}
            }
            try { conn.close() } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun closeAll() {
        for (busId in activeConnections.keys.toList()) {
            closeDevice(busId)
        }
    }

    fun findEndpoint(busId: String, endpointAddress: Int, direction: Int): UsbEndpoint? {
        val rawDev = findNativeDevice(busId) ?: return null
        val targetDir = if (direction == 1) UsbConstants.USB_DIR_IN else UsbConstants.USB_DIR_OUT

        for (i in 0 until rawDev.interfaceCount) {
            val iface = rawDev.getInterface(i)
            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                if (ep.endpointNumber == endpointAddress && ep.direction == targetDir) {
                    return ep
                }
            }
        }
        // Fallback: match by endpoint number only
        for (i in 0 until rawDev.interfaceCount) {
            val iface = rawDev.getInterface(i)
            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                if (ep.endpointNumber == endpointAddress) {
                    return ep
                }
            }
        }
        return null
    }
}
