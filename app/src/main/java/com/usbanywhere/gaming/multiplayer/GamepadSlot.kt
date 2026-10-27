package com.usbanywhere.gaming.multiplayer

/**
 * Represents a deterministic multiplayer player slot (P1, P2, P3, P4).
 */
data class GamepadSlot(
    val slotNumber: Int, // 1 to 4
    val deviceKey: String = "",
    val deviceName: String = "Empty Slot",
    val isConnected: Boolean = false,
    val connectedAtMs: Long = 0L,
    val slotColorHex: String = defaultColorForSlot(slotNumber)
) {
    val isOccupied: Boolean get() = deviceKey.isNotEmpty()

    companion object {
        fun defaultColorForSlot(slot: Int): String {
            return when (slot) {
                1 -> "#3B82F6" // P1: Neon Blue
                2 -> "#EF4444" // P2: Crimson Red
                3 -> "#10B981" // P3: Emerald Green
                4 -> "#F59E0B" // P4: Amber Yellow
                else -> "#94A3B8"
            }
        }
    }
}
