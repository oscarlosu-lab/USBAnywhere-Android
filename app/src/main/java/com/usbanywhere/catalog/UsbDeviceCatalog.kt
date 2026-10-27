package com.usbanywhere.catalog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.usbanywhere.data.UsbInterfaceInfo
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

object UsbDeviceCatalog {
    private const val TAG = "UsbDeviceCatalog"
    private const val DB_NAME = "usb_catalog.db"

    private var database: SQLiteDatabase? = null
    private val identityCache = ConcurrentHashMap<Pair<Int, Int>, DeviceIdentity>()

    // Curated instant-fallback table for key gaming and maker devices
    private val CURATED_OVERRIDES = mapOf(
        (0x0BDA to 0x5100) to ("Realtek USB Camera" to "Realtek Semiconductor Corp."),
        (0x1A86 to 0x7523) to ("CH340 Serial Converter (3D Printer)" to "QinHeng Electronics"),
        (0x054C to 0x0CE6) to ("DualSense Wireless Controller" to "Sony Corp."),
        (0x054C to 0x05C4) to ("DualShock 4 Wireless Controller" to "Sony Corp."),
        (0x045E to 0x0B12) to ("Xbox Wireless Controller" to "Microsoft Corp."),
        (0x045E to 0x028E) to ("Xbox 360 Controller" to "Microsoft Corp."),
        (0x057E to 0x2009) to ("Switch Pro Controller" to "Nintendo Co., Ltd."),
        (0x046D to 0xC24F) to ("G29 Driving Force Racing Wheel" to "Logitech, Inc."),
        (0x046D to 0xC262) to ("G920 Driving Force Racing Wheel" to "Logitech, Inc."),
        (0x046D to 0xC266) to ("G923 TRUEFORCE Racing Wheel" to "Logitech, Inc."),
        (0x044F to 0xB66E) to ("T300RS Racing Wheel" to "Thrustmaster"),
        (0x046D to 0x082D) to ("HD Pro Webcam C920" to "Logitech, Inc."),
        (0x1532 to 0x00B6) to ("DeathAdder V3 Pro (8000 Hz)" to "Razer Inc."),
        (0x1532 to 0x00A5) to ("Viper V2 Pro (8000 Hz)" to "Razer Inc.")
    )

    fun initialize(context: Context) {
        if (database != null) return

        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                dbFile.parentFile?.mkdirs()
                context.assets.open(DB_NAME).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Extracted $DB_NAME from assets to ${dbFile.absolutePath}")
            }

            database = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            Log.i(TAG, "Successfully opened $DB_NAME")
        } catch (e: Exception) {
            Log.w(TAG, "Could not open $DB_NAME from assets (using fallback catalog): ${e.message}")
        }
    }

    fun resolve(
        vendorId: Int,
        productId: Int,
        deviceClass: Int,
        deviceSubclass: Int,
        deviceProtocol: Int,
        interfaces: List<UsbInterfaceInfo>
    ): DeviceIdentity {
        val cacheKey = vendorId to productId
        identityCache[cacheKey]?.let { return it }

        var productName = ""
        var vendorName = ""

        // 1. Check curated overrides
        CURATED_OVERRIDES[cacheKey]?.let { (p, v) ->
            productName = p
            vendorName = v
        }

        // 2. Query SQLite database if not found in curated overrides
        if (productName.isEmpty() && database != null && database?.isOpen == true) {
            try {
                // Query product
                val pCursor = database?.rawQuery(
                    "SELECT name FROM products WHERE vid = ? AND pid = ? LIMIT 1",
                    arrayOf(vendorId.toString(), productId.toString())
                )
                pCursor?.use {
                    if (it.moveToFirst()) {
                        productName = it.getString(0) ?: ""
                    }
                }

                // Query vendor
                val vCursor = database?.rawQuery(
                    "SELECT name FROM vendors WHERE vid = ? LIMIT 1",
                    arrayOf(vendorId.toString())
                )
                vCursor?.use {
                    if (it.moveToFirst()) {
                        vendorName = it.getString(0) ?: ""
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying $DB_NAME: ${e.message}")
            }
        }

        // 3. Fallbacks if name still empty
        if (vendorName.isBlank()) {
            vendorName = when (vendorId) {
                0x054C -> "Sony"
                0x045E -> "Microsoft"
                0x057E -> "Nintendo"
                0x046D -> "Logitech"
                0x1532 -> "Razer"
                0x0BDA -> "Realtek"
                0x1A86 -> "WCH"
                0x0403 -> "FTDI"
                0x10C4 -> "Silicon Labs"
                else -> String.format("VID:%04X", vendorId)
            }
        }

        // 4. Classify category
        val category = UsbCategoryClassifier.classify(
            deviceClass = deviceClass,
            deviceSubclass = deviceSubclass,
            deviceProtocol = deviceProtocol,
            interfaces = interfaces,
            productName = productName,
            vendorName = vendorName
        )

        if (productName.isBlank()) {
            productName = "${category.title} (${String.format("%04X:%04X", vendorId, productId)})"
        }

        val identity = DeviceIdentity(
            productName = productName,
            vendorName = vendorName,
            category = category
        )

        identityCache[cacheKey] = identity
        return identity
    }
}
