package com.usbanywhere.catalog

enum class UsbCategory(
    val title: String,
    val emoji: String,
    val badgeColorHex: Long,
    val filterKey: String
) {
    ALL("Todos", "🌐", 0xFF64748B, "all"),
    CONTROLLER("Mando", "🎮", 0xFF6366F1, "controller"),
    STEERING_WHEEL("Volante", "🏎️", 0xFFF97316, "wheel"),
    MOUSE("Ratón", "🖱️", 0xFF00E676, "mouse"),
    KEYBOARD("Teclado", "⌨️", 0xFF3B82F6, "keyboard"),
    CAMERA("Cámara", "📷", 0xFFEC4899, "camera"),
    PRINTER_3D("Impresora 3D", "🖨️", 0xFFF59E0B, "serial"),
    AUDIO("Audio", "🎧", 0xFF06B6D4, "audio"),
    STORAGE("Almacenamiento", "💾", 0xFF8B5CF6, "storage"),
    GENERIC("USB", "🔌", 0xFF94A3B8, "generic")
}

data class DeviceIdentity(
    val productName: String,
    val vendorName: String,
    val category: UsbCategory
) {
    val fullDisplayName: String
        get() = if (vendorName.isNotBlank() && vendorName != "Generic" && !productName.contains(vendorName, ignoreCase = true)) {
            "$vendorName $productName"
        } else {
            productName
        }
}
