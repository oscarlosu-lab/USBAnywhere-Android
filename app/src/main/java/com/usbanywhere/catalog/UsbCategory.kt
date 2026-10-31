package com.usbanywhere.catalog

enum class UsbCategory(
    val title: String,
    val emoji: String,
    val badgeColorHex: Long,
    val filterKey: String
) {
    ALL("Todos", "🌐", 0xFF8E919A, "all"),
    CONTROLLER("Mando", "🎮", 0xFFD2F800, "controller"),
    STEERING_WHEEL("Volante", "🏎️", 0xFFE2E8F0, "wheel"),
    MOUSE("Ratón", "🖱️", 0xFFA1A1AA, "mouse"),
    KEYBOARD("Teclado", "⌨️", 0xFFCBD5E1, "keyboard"),
    CAMERA("Cámara", "📷", 0xFFE2E8F0, "camera"),
    PRINTER_3D("Impresora 3D", "🖨️", 0xFFF59E0B, "serial"),
    AUDIO("Audio", "🎧", 0xFFD2F800, "audio"),
    STORAGE("Almacenamiento", "💾", 0xFFA1A1AA, "storage"),
    GENERIC("USB", "🔌", 0xFF71717A, "generic")
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
