package com.usbanywhere.catalog

import com.usbanywhere.data.UsbInterfaceInfo

object UsbCategoryClassifier {

    fun classify(
        deviceClass: Int,
        deviceSubclass: Int,
        deviceProtocol: Int,
        interfaces: List<UsbInterfaceInfo>,
        productName: String = "",
        vendorName: String = ""
    ): UsbCategory {
        val combinedName = "$vendorName $productName".lowercase()

        // 1. Keyword semantic inspection (high confidence for gaming/makers)
        if (matchesAny(combinedName, "wheel", "racing", "pedal", "fanatec", "thrustmaster", "g29", "g920", "g923", "simucube", "moza")) {
            return UsbCategory.STEERING_WHEEL
        }
        if (matchesAny(combinedName, "dualsense", "dualshock", "xbox", "controller", "gamepad", "joy-con", "switch pro", "flydigi", "8bitdo")) {
            return UsbCategory.CONTROLLER
        }
        if (matchesAny(combinedName, "mouse", "viper", "deathadder", "superlight", "trackball", "basilisk", "aerox")) {
            return UsbCategory.MOUSE
        }
        if (matchesAny(combinedName, "keyboard", "keychron", "ducky", "clavier", "teclado", "wooting")) {
            return UsbCategory.KEYBOARD
        }
        if (matchesAny(combinedName, "camera", "webcam", "cam ", "uvc", "capture", "cam link", "c920", "kiyo", "brio")) {
            return UsbCategory.CAMERA
        }
        if (matchesAny(combinedName, "ch340", "ch341", "cp210", "ft232", "arduino", "ender", "marlin", "prusa", "voron", "cnc", "uart", "serial")) {
            return UsbCategory.PRINTER_3D
        }
        if (matchesAny(combinedName, "audio", "dac", "headset", "headphone", "microphone", "soundcard", "yeti", "rode", "focusrite", "schiit")) {
            return UsbCategory.AUDIO
        }
        if (matchesAny(combinedName, "flash", "disk", "ssd", "hdd", "storage", "mass storage", "sandisk", "kingston", "crucial")) {
            return UsbCategory.STORAGE
        }

        // 2. USB-IF Standard Class Evaluation (100% universal fallback)
        val hasVideo = deviceClass == 0x0E || interfaces.any { it.interfaceClass == 0x0E }
        if (hasVideo) return UsbCategory.CAMERA

        val hasAudio = deviceClass == 0x01 || interfaces.any { it.interfaceClass == 0x01 }
        if (hasAudio) return UsbCategory.AUDIO

        val hasStorage = deviceClass == 0x08 || interfaces.any { it.interfaceClass == 0x08 }
        if (hasStorage) return UsbCategory.STORAGE

        val hasCdc = deviceClass == 0x02 || deviceClass == 0x0A || interfaces.any { it.interfaceClass == 0x02 || it.interfaceClass == 0x0A }
        if (hasCdc) return UsbCategory.PRINTER_3D

        val hidInterface = interfaces.firstOrNull { it.interfaceClass == 0x03 }
        if (deviceClass == 0x03 || hidInterface != null) {
            val protocol = hidInterface?.interfaceProtocol ?: deviceProtocol
            return when (protocol) {
                2 -> UsbCategory.MOUSE
                1 -> UsbCategory.KEYBOARD
                else -> UsbCategory.CONTROLLER
            }
        }

        // Vendor-Specific with Serial / Maker characteristics
        if (deviceClass == 0xFF || interfaces.any { it.interfaceClass == 0xFF }) {
            // Check for known maker vendor ranges (WCH 0x1A86, FTDI 0x0403, SiLabs 0x10C4)
            if (matchesAny(combinedName, "qinheng", "wch", "ftdi", "silicon labs", "prolific")) {
                return UsbCategory.PRINTER_3D
            }
        }

        return UsbCategory.GENERIC
    }

    private fun matchesAny(text: String, vararg keywords: String): Boolean {
        for (kw in keywords) {
            if (text.contains(kw)) return true
        }
        return false
    }
}
