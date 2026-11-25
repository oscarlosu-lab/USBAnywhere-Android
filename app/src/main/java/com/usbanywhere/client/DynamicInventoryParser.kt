package com.usbanywhere.client

import com.usbanywhere.data.UsbExportedDevice
import org.json.JSONObject

internal data class DynamicInventoryEntry(
    val busId: String,
    val vendorId: Int,
    val productId: Int,
    val productName: String,
    val isShared: Boolean
)

internal object DynamicInventoryParser {
    private val busIdPattern = Regex("""\d+-[\d.]+""")
    private val entryPattern = Regex("""^\s*([0-9A-Za-z][0-9A-Za-z.-]*)\s*:\s*(.*?)\s*$""")
    private val vidPidPattern = Regex("""\(([0-9a-fA-F]{4}):([0-9a-fA-F]{4})\)\s*$""")
    private val sharedStatusPattern =
        Regex("""\[(?:Shared(?:\s*\(forced\))?|Attached[^\]]*)\]""", RegexOption.IGNORE_CASE)
    private val inactiveStatusPattern =
        Regex("""\[(?:Local|Not shared)\]""", RegexOption.IGNORE_CASE)
    private val statusSuffixPattern =
        Regex("""\s*\[(?:Local|Not shared|Shared(?:\s*\(forced\))?|Attached[^\]]*)\]\s*$""", RegexOption.IGNORE_CASE)

    fun parse(response: String): List<UsbExportedDevice> {
        val entries = JSONObject(response).optJSONArray("devices") ?: return emptyList()
        val parsedEntries = mutableListOf<DynamicInventoryEntry>()

        for (index in 0 until entries.length()) {
            val entry = when (val value = entries.opt(index)) {
                is JSONObject -> parseObject(value)
                is String -> parseLine(value)
                else -> null
            }
            if (entry != null) parsedEntries.add(entry)
        }

        return createDevices(parsedEntries)
    }

    internal fun parseObject(
        busId: String,
        vendorId: String,
        productId: String,
        productName: String,
        isShared: Boolean
    ): DynamicInventoryEntry? {
        val validBusId = busId.trim()
        if (!busIdPattern.matches(validBusId)) return null

        return DynamicInventoryEntry(
            busId = validBusId,
            vendorId = vendorId.toIntOrNull(16) ?: 0,
            productId = productId.toIntOrNull(16) ?: 0,
            productName = productName.ifBlank { "USB Device" },
            isShared = isShared
        )
    }

    private fun parseObject(entry: JSONObject): DynamicInventoryEntry? {
        val busId = entry.optString("busid").trim()
        val vidPid = entry.optString("vid_pid")
            .takeIf { it.matches(Regex("""[0-9a-fA-F]{4}:[0-9a-fA-F]{4}""")) }
            ?.split(":")
        val vendorId = entry.optString("vid").ifBlank { vidPid?.get(0).orEmpty() }
        val productId = entry.optString("pid").ifBlank { vidPid?.get(1).orEmpty() }
        return parseObject(
            busId = busId,
            vendorId = vendorId,
            productId = productId,
            productName = entry.optString("description", "USB Device"),
            isShared = entry.optBoolean("shared", false)
        )
    }

    internal fun parseLine(entry: String): DynamicInventoryEntry? {
        val match = entryPattern.matchEntire(entry) ?: return null
        val busId = match.groupValues[1]
        if (!busIdPattern.matches(busId)) return null

        val rawDescription = match.groupValues[2].trim()
        val vidPidMatch = vidPidPattern.find(rawDescription)
        val vendorId = vidPidMatch?.groupValues?.get(1)?.toIntOrNull(16) ?: 0
        val productId = vidPidMatch?.groupValues?.get(2)?.toIntOrNull(16) ?: 0
        val descriptionWithoutIds = vidPidMatch?.let {
            rawDescription.removeRange(it.range).trim()
        } ?: rawDescription
        val isShared = sharedStatusPattern.containsMatchIn(descriptionWithoutIds) &&
            !inactiveStatusPattern.containsMatchIn(descriptionWithoutIds)
        val description = statusSuffixPattern.replace(descriptionWithoutIds, "").trim()

        return DynamicInventoryEntry(
            busId = busId,
            vendorId = vendorId,
            productId = productId,
            productName = description.ifBlank { "USB Device" },
            isShared = isShared
        )
    }

    internal fun createDevices(entries: Iterable<DynamicInventoryEntry>): List<UsbExportedDevice> {
        val devices = LinkedHashMap<String, UsbExportedDevice>()
        for (entry in entries) {
            if (!busIdPattern.matches(entry.busId)) continue
            devices.putIfAbsent(
                entry.busId,
                UsbExportedDevice(
                    busId = entry.busId,
                    path = "/sys/bus/usb/devices/${entry.busId}",
                    busNum = entry.busId.substringBefore("-").toIntOrNull() ?: 1,
                    devNum = devices.size + 1,
                    speed = 3,
                    vendorId = entry.vendorId,
                    productId = entry.productId,
                    deviceClass = 0,
                    deviceSubclass = 0,
                    deviceProtocol = 0,
                    interfaces = emptyList(),
                    productName = entry.productName,
                    isShared = entry.isShared
                )
            )
        }
        return devices.values.toList()
    }
}
