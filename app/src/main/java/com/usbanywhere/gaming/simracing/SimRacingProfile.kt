package com.usbanywhere.gaming.simracing

/**
 * Hardware profiles and vendor identifiers for sim racing steering wheels and pedals.
 */
object SimRacingProfile {

    const val VENDOR_LOGITECH = 0x046D
    const val VENDOR_THRUSTMASTER = 0x044F
    const val VENDOR_FANATEC = 0x0EB7
    const val VENDOR_MOZA = 0x3416

    // Logitech Wheel Product IDs
    const val LOGITECH_G29 = 0xC24F
    const val LOGITECH_G920 = 0xC262
    const val LOGITECH_G923_PS = 0xC266
    const val LOGITECH_G923_XB = 0xC267
    const val LOGITECH_G27 = 0xC29B
    const val LOGITECH_DFGT = 0xC29A

    // Thrustmaster Wheel Product IDs
    const val TM_T300RS = 0xB66E
    const val TM_T150 = 0xB677
    const val TM_TGT = 0xB684
    const val TM_T248 = 0xB696

    fun isRacingWheel(vendorId: Int, productId: Int): Boolean {
        return when (vendorId) {
            VENDOR_LOGITECH -> when (productId) {
                LOGITECH_G29, LOGITECH_G920, LOGITECH_G923_PS,
                LOGITECH_G923_XB, LOGITECH_G27, LOGITECH_DFGT -> true
                else -> false
            }
            VENDOR_THRUSTMASTER -> when (productId) {
                TM_T300RS, TM_T150, TM_TGT, TM_T248 -> true
                else -> false
            }
            VENDOR_FANATEC -> true // All Fanatec devices are racing hardware
            VENDOR_MOZA -> true    // All Moza devices are racing hardware
            else -> false
        }
    }

    fun getWheelName(vendorId: Int, productId: Int): String {
        return when (vendorId) {
            VENDOR_LOGITECH -> when (productId) {
                LOGITECH_G29 -> "Logitech G29 Driving Force"
                LOGITECH_G920 -> "Logitech G920 Driving Force"
                LOGITECH_G923_PS, LOGITECH_G923_XB -> "Logitech G923 TrueForce"
                LOGITECH_G27 -> "Logitech G27 Racing Wheel"
                LOGITECH_DFGT -> "Logitech Driving Force GT"
                else -> "Logitech Wheel (0x${productId.toString(16).uppercase()})"
            }
            VENDOR_THRUSTMASTER -> when (productId) {
                TM_T300RS -> "Thrustmaster T300RS"
                TM_T150 -> "Thrustmaster T150"
                TM_TGT -> "Thrustmaster T-GT"
                TM_T248 -> "Thrustmaster T248"
                else -> "Thrustmaster Wheel (0x${productId.toString(16).uppercase()})"
            }
            VENDOR_FANATEC -> "Fanatec Direct Drive Wheel / Base"
            VENDOR_MOZA -> "Moza Racing Direct Drive Wheel"
            else -> "Sim Racing Hardware"
        }
    }
}
