package com.usbanywhere.gaming.multiplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MultiplayerHubTest {

    @Before
    fun setUp() {
        MultiplayerHubManager.clearAll()
    }

    @Test
    fun testDeterministicSlotAssignment() {
        // Assign 4 distinct controllers
        val slot1 = MultiplayerHubManager.registerDevice("xbox_01", "Xbox Wireless Controller")
        val slot2 = MultiplayerHubManager.registerDevice("dualsense_01", "Sony DualSense")
        val slot3 = MultiplayerHubManager.registerDevice("switch_01", "Nintendo Pro Controller")
        val slot4 = MultiplayerHubManager.registerDevice("generic_01", "8BitDo Ultimate")

        assertEquals(1, slot1)
        assertEquals(2, slot2)
        assertEquals(3, slot3)
        assertEquals(4, slot4)

        // Verify state
        val slots = MultiplayerHubManager.slots.value
        assertEquals("Xbox Wireless Controller", slots[0].deviceName)
        assertEquals("Sony DualSense", slots[1].deviceName)
        assertTrue(slots[0].isConnected)
        assertTrue(slots[1].isConnected)

        // 5th device should be rejected when all 4 are active
        val slot5 = MultiplayerHubManager.registerDevice("extra_01", "Extra Pad")
        assertEquals(-1, slot5)
    }

    @Test
    fun testReconnectionPreservesPlayerSlot() {
        // P1 connects
        MultiplayerHubManager.registerDevice("xbox_01", "Xbox Controller")
        // P2 connects
        MultiplayerHubManager.registerDevice("dualsense_01", "DualSense")

        // P1 disconnects
        MultiplayerHubManager.unregisterDevice("xbox_01")
        val slotsAfterDisconnect = MultiplayerHubManager.slots.value
        assertFalse(slotsAfterDisconnect[0].isConnected)
        assertEquals("xbox_01", slotsAfterDisconnect[0].deviceKey) // Reservation kept!

        // P1 reconnects -> MUST get Slot 1 back, NOT assigned to another slot!
        val reconnectedSlot = MultiplayerHubManager.registerDevice("xbox_01", "Xbox Controller")
        assertEquals(1, reconnectedSlot)
        assertTrue(MultiplayerHubManager.slots.value[0].isConnected)
    }

    @Test
    fun testSwapSlots() {
        MultiplayerHubManager.registerDevice("pad_a", "Player A")
        MultiplayerHubManager.registerDevice("pad_b", "Player B")

        assertEquals("pad_a", MultiplayerHubManager.slots.value[0].deviceKey)
        assertEquals("pad_b", MultiplayerHubManager.slots.value[1].deviceKey)

        // Swap P1 and P2
        val swapped = MultiplayerHubManager.swapSlots(1, 2)
        assertTrue(swapped)

        assertEquals("pad_b", MultiplayerHubManager.slots.value[0].deviceKey)
        assertEquals("pad_a", MultiplayerHubManager.slots.value[1].deviceKey)
    }
}
