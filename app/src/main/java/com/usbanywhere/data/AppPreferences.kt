package com.usbanywhere.data

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log

/**
 * Centralized persistent preferences manager with strict Non-Intrusive design.
 *
 * All auto-start and background hooks (Boot completed, USB plug-in, Kiosk mode)
 * are DISABLED BY DEFAULT (opt-in only). USBAnywhere never runs in the background
 * or triggers Android system prompts without explicit user consent.
 */
object AppPreferences {

    private const val TAG = "AppPreferences"
    private const val PREFS_NAME = "usbanywhere_preferences"

    // Preference keys
    const val KEY_AUTOSTART_ON_BOOT = "pref_autostart_on_boot"
    const val KEY_AUTOSTART_ON_USB = "pref_autostart_on_usb"
    const val KEY_ESPORTS_8000HZ = "pref_esports_8000hz"
    const val KEY_KEEP_AWAKE = "pref_keep_awake"
    const val KEY_KIOSK_MODE = "pref_kiosk_mode"
    const val KEY_SERVER_PORT = "pref_server_port"
    const val KEY_SUNSHINE_PORT = "pref_sunshine_port"
    const val KEY_NOTIFICATIONS_ENABLED = "pref_notifications_enabled"
    const val KEY_HAPTIC_FEEDBACK_ENABLED = "pref_haptic_feedback_enabled"

    // Default values: Strictly Non-Intrusive & Opt-in
    const val DEFAULT_AUTOSTART_ON_BOOT = false
    const val DEFAULT_AUTOSTART_ON_USB = false
    const val DEFAULT_ESPORTS_8000HZ = true
    const val DEFAULT_KEEP_AWAKE = true
    const val DEFAULT_KIOSK_MODE = false
    const val DEFAULT_SERVER_PORT = 3240
    const val DEFAULT_SUNSHINE_PORT = 3242
    const val DEFAULT_NOTIFICATIONS_ENABLED = true
    const val DEFAULT_HAPTIC_FEEDBACK_ENABLED = true

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAutostartOnBoot(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTOSTART_ON_BOOT, DEFAULT_AUTOSTART_ON_BOOT)
    }

    fun setAutostartOnBoot(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTOSTART_ON_BOOT, enabled).apply()
        syncComponentState(context)
    }

    fun isAutostartOnUsb(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTOSTART_ON_USB, DEFAULT_AUTOSTART_ON_USB)
    }

    fun setAutostartOnUsb(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTOSTART_ON_USB, enabled).apply()
        syncComponentState(context)
    }

    fun isEsports8000Hz(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ESPORTS_8000HZ, DEFAULT_ESPORTS_8000HZ)
    }

    fun setEsports8000Hz(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ESPORTS_8000HZ, enabled).apply()
    }

    fun isKeepAwake(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_KEEP_AWAKE, DEFAULT_KEEP_AWAKE)
    }

    fun setKeepAwake(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_KEEP_AWAKE, enabled).apply()
    }

    fun isKioskMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_KIOSK_MODE, DEFAULT_KIOSK_MODE)
    }

    fun setKioskMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_KIOSK_MODE, enabled).apply()
    }

    fun getServerPort(context: Context): Int {
        val port = getPrefs(context).getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT)
        return if (port in 1024..65535) port else DEFAULT_SERVER_PORT
    }

    fun setServerPort(context: Context, port: Int) {
        val validPort = if (port in 1024..65535) port else DEFAULT_SERVER_PORT
        getPrefs(context).edit().putInt(KEY_SERVER_PORT, validPort).apply()
    }

    fun getSunshinePort(context: Context): Int {
        val port = getPrefs(context).getInt(KEY_SUNSHINE_PORT, DEFAULT_SUNSHINE_PORT)
        return if (port in 1024..65535) port else DEFAULT_SUNSHINE_PORT
    }

    fun setSunshinePort(context: Context, port: Int) {
        val validPort = if (port in 1024..65535) port else DEFAULT_SUNSHINE_PORT
        getPrefs(context).edit().putInt(KEY_SUNSHINE_PORT, validPort).apply()
    }

    fun isNotificationsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFICATIONS_ENABLED, DEFAULT_NOTIFICATIONS_ENABLED)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun isHapticFeedbackEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, DEFAULT_HAPTIC_FEEDBACK_ENABLED)
    }

    fun setHapticFeedbackEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, enabled).apply()
    }

    /**
     * Synchronizes Android OS Component visibility with user preferences.
     * When disabled, the OS components are marked COMPONENT_ENABLED_STATE_DISABLED,
     * which prevents Android from waking the process on boot or displaying
     * "Open USBAnywhere" popups when USB devices are attached.
     */
    fun syncComponentState(context: Context) {
        try {
            val pm = context.packageManager ?: return
            val bootComponent = ComponentName(context, "com.usbanywhere.receiver.BootCompletedReceiver")
            val usbComponent = ComponentName(context, "com.usbanywhere.receiver.UsbAttachedReceiver")

            val bootWanted = isAutostartOnBoot(context)
            val usbWanted = isAutostartOnUsb(context)

            val bootState = if (bootWanted) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            val usbState = if (usbWanted) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            pm.setComponentEnabledSetting(bootComponent, bootState, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(usbComponent, usbState, PackageManager.DONT_KILL_APP)

            Log.d(TAG, "Component state synchronized: BootReceiver=$bootWanted, UsbReceiver=$usbWanted")
        } catch (e: Throwable) {
            // Defensive wrapper for mock test environments or restricted devices
            Log.w(TAG, "Could not sync component enabled settings: ${e.message}")
        }
    }
}
