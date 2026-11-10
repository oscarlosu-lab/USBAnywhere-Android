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
import com.usbanywhere.protocol.UsbIpConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class UsbHostManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "UsbHostManager"
        const val ACTION_USB_PERMISSION = "com.usbanywhere.USB_PERMISSION"

        @Volatile private var instance: UsbHostManager? = null

        /**
         * Single shared instance. The service and the UI used to create one each, so the
         * UI showed a stale list (permission granted, shared state...) until the tab was
         * re-created, and toggling "share" in the UI did not reach the server.
         */
        fun get(context: Context): UsbHostManager =
            instance ?: synchronized(this) {
                instance ?: UsbHostManager(context.applicationContext).also { instance = it }
            }

        /** "/dev/bus/usb/001/005" -> (1, 5). Falls back to (1, deviceId). */
        fun parseBusDev(deviceName: String, deviceId: Int): Pair<Int, Int> {
            val parts = deviceName.trimEnd('/').split('/')
            val bus = parts.getOrNull(parts.size - 2)?.toIntOrNull()
            val dev = parts.lastOrNull()?.toIntOrNull()
            return if (bus != null && dev != null) bus to dev else 1 to deviceId
        }

        fun busIdFor(deviceName: String, deviceId: Int): String {
            val (bus, dev) = parseBusDev(deviceName, deviceId)
            return "$bus-$dev"
        }

        /**
         * Android does not expose the negotiated speed of a host-mode device, so it is
         * deduced from the endpoint descriptors (which Android reports for the speed the
         * device is actually running at):
         *  - bulk 1024 bytes                      -> SuperSpeed
         *  - bulk 512 or interrupt/iso > 64 bytes -> High Speed
         *  - anything else                        -> Full Speed (descriptor-valid for LS/FS/HS devices)
         * Reporting High Speed for a Full Speed device (old behaviour) makes Windows reject
         * the descriptors (bMaxPacketSize0 != 64, bulk 64 bytes are invalid at HS).
         */
        fun deduceSpeed(endpoints: List<Pair<Int, Int>>): Int {
            var speed = UsbIpConstants.USB_SPEED_FULL
            for ((type, maxPacket) in endpoints) {
                val size = maxPacket and 0x7FF
                when (type) {
                    UsbConstants.USB_ENDPOINT_XFER_BULK -> {
                        if (size >= 1024) return UsbIpConstants.USB_SPEED_SUPER
                        if (size >= 512) speed = UsbIpConstants.USB_SPEED_HIGH
                    }
                    UsbConstants.USB_ENDPOINT_XFER_INT -> if (size > 64) speed = UsbIpConstants.USB_SPEED_HIGH
                    UsbConstants.USB_ENDPOINT_XFER_ISOC -> if (size > 1023 || (maxPacket shr 11) != 0) speed = UsbIpConstants.USB_SPEED_HIGH
                }
            }
            return speed
        }
    }

    /** Extra device-descriptor data that is only available once the device is opened. */
    data class DescriptorInfo(val bcdDevice: Int, val speed: Int)

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectedDevices = MutableStateFlow<List<UsbExportedDevice>>(emptyList())
    val connectedDevices: StateFlow<List<UsbExportedDevice>> = _connectedDevices.asStateFlow()

    private val activeConnections = ConcurrentHashMap<String, UsbDeviceConnection>()
    private val claimedInterfaces = ConcurrentHashMap<String, MutableList<UsbInterface>>()
    private val sharedStates = ConcurrentHashMap<String, Boolean>()
    private val descriptorCache = ConcurrentHashMap<String, DescriptorInfo>()
    /** busId -> (interface number -> current alternate setting) */
    private val currentAlt = ConcurrentHashMap<String, ConcurrentHashMap<Int, Int>>()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
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
        val present = mutableSetOf<String>()

        for ((_, usbDev) in deviceList) {
            val (busNum, devNum) = parseBusDev(usbDev.deviceName, usbDev.deviceId)
            val busId = "$busNum-$devNum"
            present.add(busId)

            // Only alternate setting 0 counts as an interface (Android lists every alt setting)
            val interfaces = mutableListOf<UsbInterfaceInfo>()
            for (i in 0 until usbDev.interfaceCount) {
                val iface = usbDev.getInterface(i)
                if (iface.alternateSetting != 0) continue
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
            val cached = descriptorCache[busId]
            val configValue = if (usbDev.configurationCount > 0) usbDev.getConfiguration(0).id else 1

            exportedList.add(
                UsbExportedDevice(
                    busId = busId,
                    path = "/sys/bus/usb/devices/$busId",
                    busNum = busNum,
                    devNum = devNum,
                    speed = cached?.speed ?: mapUsbSpeed(usbDev),
                    vendorId = usbDev.vendorId,
                    productId = usbDev.productId,
                    bcdDevice = cached?.bcdDevice ?: 0x0100,
                    deviceClass = usbDev.deviceClass,
                    deviceSubclass = usbDev.deviceSubclass,
                    deviceProtocol = usbDev.deviceProtocol,
                    configurationValue = configValue,
                    numConfigurations = usbDev.configurationCount.coerceAtLeast(1),
                    interfaces = interfaces,
                    productName = prodName,
                    manufacturerName = mfgName,
                    isShared = sharedStates.getOrDefault(busId, true),
                    hasPermission = usbManager.hasPermission(usbDev),
                    isAttached = activeConnections.containsKey(busId)
                )
            )
        }
        descriptorCache.keys.retainAll(present)
        _connectedDevices.value = exportedList
    }

    private fun mapUsbSpeed(device: UsbDevice): Int {
        val eps = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                eps.add(ep.type to ep.maxPacketSize)
            }
        }
        return deduceSpeed(eps)
    }

    /**
     * Reads data only available with an open connection (bcdDevice, HS capability) and
     * returns the device entry updated with it.
     */
    fun refineDevice(busId: String, conn: UsbDeviceConnection): UsbExportedDevice? {
        val base = _connectedDevices.value.find { it.busId == busId } ?: return null
        val raw = try { conn.rawDescriptors } catch (_: Exception) { null }
        var bcdDevice = base.bcdDevice
        var speed = base.speed
        if (raw != null && raw.size >= 18 && raw[1].toInt() == 1) {
            bcdDevice = (raw[12].toInt() and 0xFF) or ((raw[13].toInt() and 0xFF) shl 8)
            val bcdUsb = (raw[2].toInt() and 0xFF) or ((raw[3].toInt() and 0xFF) shl 8)
            val maxPacket0 = raw[7].toInt() and 0xFF
            if (speed == UsbIpConstants.USB_SPEED_FULL && bcdUsb >= 0x0200 && maxPacket0 == 64) {
                // Ambiguous FS/HS: a HS-capable device answers DEVICE_QUALIFIER, FS-only devices stall.
                val q = ByteArray(10)
                val n = try { conn.controlTransfer(0x80, 0x06, 0x0600, 0, q, q.size, 1000) } catch (_: Exception) { -1 }
                if (n >= 10 && q[1].toInt() == 6) speed = UsbIpConstants.USB_SPEED_HIGH
            }
            Log.i(TAG, "Device $busId: bcdUSB=0x${Integer.toHexString(bcdUsb)} maxPacket0=$maxPacket0 bcdDevice=0x${Integer.toHexString(bcdDevice)} speed=$speed")
        }
        descriptorCache[busId] = DescriptorInfo(bcdDevice, speed)
        return base.copy(bcdDevice = bcdDevice, speed = speed)
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

    fun setDeviceShared(busId: String, shared: Boolean) {
        sharedStates[busId] = shared
        if (!shared) {
            closeDevice(busId)
        }
        refreshDevices()
    }

    fun findNativeDevice(busId: String): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { busIdFor(it.deviceName, it.deviceId) == busId }

    /**
     * Opens USB connection and claims all interfaces (detaching Android's own drivers).
     */
    @Synchronized
    fun openDevice(busId: String): UsbDeviceConnection? {
        if (sharedStates.getOrDefault(busId, true).not()) {
            Log.w(TAG, "Refusing to open device $busId: not shared")
            return null
        }
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
        val alts = ConcurrentHashMap<Int, Int>()
        for (i in 0 until rawDev.interfaceCount) {
            val iface = rawDev.getInterface(i)
            if (iface.alternateSetting != 0 || alts.containsKey(iface.id)) continue
            alts[iface.id] = 0
            if (conn.claimInterface(iface, true)) {
                claimed.add(iface)
                Log.d(TAG, "Claimed interface ${iface.id} on $busId")
            } else {
                Log.w(TAG, "Failed to claim interface ${iface.id} on $busId")
            }
        }

        activeConnections[busId] = conn
        claimedInterfaces[busId] = claimed
        currentAlt[busId] = alts
        return conn
    }

    /** Handles SET_INTERFACE through the Android API (the kernel must know about it). */
    fun setInterface(busId: String, conn: UsbDeviceConnection, ifaceNum: Int, alt: Int): Boolean {
        val rawDev = findNativeDevice(busId) ?: return false
        for (i in 0 until rawDev.interfaceCount) {
            val iface = rawDev.getInterface(i)
            if (iface.id == ifaceNum && iface.alternateSetting == alt) {
                val ok = conn.setInterface(iface)
                if (ok) currentAlt[busId]?.put(ifaceNum, alt)
                return ok
            }
        }
        return false
    }

    /** Returns true if [value] is the configuration already active (Android configures devices itself). */
    fun isCurrentConfiguration(busId: String, value: Int): Boolean {
        val rawDev = findNativeDevice(busId) ?: return false
        if (value == 0) return false
        return rawDev.configurationCount == 0 || rawDev.getConfiguration(0).id == value
    }

    fun resetAltSettings(busId: String) {
        currentAlt[busId]?.replaceAll { _, _ -> 0 }
    }

    @Synchronized
    fun closeDevice(busId: String) {
        val conn = activeConnections.remove(busId)
        val claimed = claimedInterfaces.remove(busId)
        currentAlt.remove(busId)
        if (conn != null) {
            for (iface in claimed.orEmpty()) {
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

    /**
     * Finds the endpoint with the given number/direction in the currently selected
     * alternate settings.
     */
    fun findEndpoint(busId: String, endpointNumber: Int, direction: Int): UsbEndpoint? {
        val rawDev = findNativeDevice(busId) ?: return null
        val targetDir = if (direction == UsbIpConstants.USBIP_DIR_IN) UsbConstants.USB_DIR_IN else UsbConstants.USB_DIR_OUT
        val alts = currentAlt[busId]
        var fallback: UsbEndpoint? = null

        for (i in 0 until rawDev.interfaceCount) {
            val iface = rawDev.getInterface(i)
            val activeAlt = alts?.get(iface.id) ?: 0
            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                if (ep.endpointNumber == endpointNumber && ep.direction == targetDir) {
                    if (iface.alternateSetting == activeAlt) return ep
                    if (fallback == null) fallback = ep
                }
            }
        }
        return fallback
    }
}
