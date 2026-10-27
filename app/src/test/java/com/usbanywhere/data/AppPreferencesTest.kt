package com.usbanywhere.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesTest {

    @Test
    fun testNonIntrusiveDefaults() {
        // Strict Zero-Intrusion verification: Boot, USB attach and Kiosk MUST default to false
        assertFalse("Boot auto-start must be disabled by default", AppPreferences.DEFAULT_AUTOSTART_ON_BOOT)
        assertFalse("USB attach auto-start must be disabled by default", AppPreferences.DEFAULT_AUTOSTART_ON_USB)
        assertFalse("Kiosk mode must be disabled by default", AppPreferences.DEFAULT_KIOSK_MODE)

        // Performance & UX defaults
        assertTrue("Esports 8000Hz should default to enabled", AppPreferences.DEFAULT_ESPORTS_8000HZ)
        assertTrue("Keep awake should default to enabled", AppPreferences.DEFAULT_KEEP_AWAKE)
        assertTrue("Notifications should default to enabled", AppPreferences.DEFAULT_NOTIFICATIONS_ENABLED)
        assertTrue("Haptics should default to enabled", AppPreferences.DEFAULT_HAPTIC_FEEDBACK_ENABLED)

        // Ports defaults
        assertEquals(3240, AppPreferences.DEFAULT_SERVER_PORT)
        assertEquals(3242, AppPreferences.DEFAULT_SUNSHINE_PORT)
    }

    @Test
    fun testPreferenceKeysIntegrity() {
        assertEquals("pref_autostart_on_boot", AppPreferences.KEY_AUTOSTART_ON_BOOT)
        assertEquals("pref_autostart_on_usb", AppPreferences.KEY_AUTOSTART_ON_USB)
        assertEquals("pref_kiosk_mode", AppPreferences.KEY_KIOSK_MODE)
        assertEquals("pref_esports_8000hz", AppPreferences.KEY_ESPORTS_8000HZ)
        assertEquals("pref_keep_awake", AppPreferences.KEY_KEEP_AWAKE)
        assertEquals("pref_server_port", AppPreferences.KEY_SERVER_PORT)
        assertEquals("pref_sunshine_port", AppPreferences.KEY_SUNSHINE_PORT)
        assertEquals("pref_notifications_enabled", AppPreferences.KEY_NOTIFICATIONS_ENABLED)
        assertEquals("pref_haptic_feedback_enabled", AppPreferences.KEY_HAPTIC_FEEDBACK_ENABLED)
    }
}
