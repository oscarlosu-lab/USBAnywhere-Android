package com.usbanywhere.gaming.multiplayer

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages deterministic player slot assignments (P1 through P4) for multi-controller living room setups.
 * Prevents player numbers from randomly scrambling when USB hubs or wireless gamepads reconnect.
 */
object MultiplayerHubManager {

    private const val TAG = "MultiplayerHubManager"
    const val MAX_SLOTS = 4

    private val initialSlots = List(MAX_SLOTS) { index ->
        GamepadSlot(slotNumber = index + 1)
    }

    private val _slots = MutableStateFlow(initialSlots)
    val slots: StateFlow<List<GamepadSlot>> = _slots.asStateFlow()

    /**
     * Assigns or restores a gamepad to its deterministic player slot.
     * Returns the assigned slot number (1-4), or -1 if all slots are full.
     */
    @Synchronized
    fun registerDevice(deviceKey: String, deviceName: String): Int {
        if (deviceKey.isEmpty()) return -1

        val currentList = _slots.value.toMutableList()

        // 1. Check if device is already registered in a slot (preserve reservation!)
        val existingIndex = currentList.indexOfFirst { it.deviceKey == deviceKey }
        if (existingIndex != -1) {
            val existing = currentList[existingIndex]
            currentList[existingIndex] = existing.copy(
                deviceName = deviceName,
                isConnected = true,
                connectedAtMs = System.currentTimeMillis()
            )
            _slots.value = currentList
            Log.i(TAG, "Restored device '$deviceName' to reserved Slot P${existing.slotNumber}")
            return existing.slotNumber
        }

        // 2. Find first unoccupied slot
        val emptyIndex = currentList.indexOfFirst { !it.isOccupied }
        if (emptyIndex != -1) {
            val slotNum = emptyIndex + 1
            currentList[emptyIndex] = GamepadSlot(
                slotNumber = slotNum,
                deviceKey = deviceKey,
                deviceName = deviceName,
                isConnected = true,
                connectedAtMs = System.currentTimeMillis()
            )
            _slots.value = currentList
            Log.i(TAG, "Assigned device '$deviceName' to new Slot P$slotNum")
            return slotNum
        }

        // 3. Find first slot that is disconnected
        val disconnectedIndex = currentList.indexOfFirst { !it.isConnected }
        if (disconnectedIndex != -1) {
            val slotNum = disconnectedIndex + 1
            currentList[disconnectedIndex] = GamepadSlot(
                slotNumber = slotNum,
                deviceKey = deviceKey,
                deviceName = deviceName,
                isConnected = true,
                connectedAtMs = System.currentTimeMillis()
            )
            _slots.value = currentList
            Log.i(TAG, "Replaced disconnected Slot P$slotNum with device '$deviceName'")
            return slotNum
        }

        Log.w(TAG, "All $MAX_SLOTS player slots are occupied. Cannot assign '$deviceName'")
        return -1
    }

    /**
     * Marks a device as disconnected, but preserves its slot reservation.
     */
    @Synchronized
    fun unregisterDevice(deviceKey: String) {
        val currentList = _slots.value.toMutableList()
        val index = currentList.indexOfFirst { it.deviceKey == deviceKey }
        if (index != -1) {
            val existing = currentList[index]
            currentList[index] = existing.copy(isConnected = false)
            _slots.value = currentList
            Log.i(TAG, "Marked Slot P${existing.slotNumber} ('${existing.deviceName}') as disconnected (Reservation kept)")
        }
    }

    /**
     * Swaps two player slots (e.g. swap P1 and P2).
     */
    @Synchronized
    fun swapSlots(slotA: Int, slotB: Int): Boolean {
        if (slotA !in 1..MAX_SLOTS || slotB !in 1..MAX_SLOTS || slotA == slotB) return false

        val currentList = _slots.value.toMutableList()
        val indexA = slotA - 1
        val indexB = slotB - 1

        val itemA = currentList[indexA]
        val itemB = currentList[indexB]

        currentList[indexA] = itemB.copy(slotNumber = slotA, slotColorHex = GamepadSlot.defaultColorForSlot(slotA))
        currentList[indexB] = itemA.copy(slotNumber = slotB, slotColorHex = GamepadSlot.defaultColorForSlot(slotB))

        _slots.value = currentList
        Log.i(TAG, "Swapped Slot P$slotA and Slot P$slotB")
        return true
    }

    /**
     * Clears all reservations and resets to 4 empty slots.
     */
    @Synchronized
    fun clearAll() {
        _slots.value = List(MAX_SLOTS) { index -> GamepadSlot(slotNumber = index + 1) }
        Log.i(TAG, "Cleared all multiplayer player slot reservations")
    }
}
