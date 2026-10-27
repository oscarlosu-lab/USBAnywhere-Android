package com.usbanywhere.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.usbanywhere.data.AppPreferences
import com.usbanywhere.server.UsbIpServerService

/**
 * Handles Android system boot events (BOOT_COMPLETED, QUICKBOOT_POWERON, LOCKED_BOOT_COMPLETED).
 * Strictly non-intrusive: Disabled by default in the manifest and checks [AppPreferences]
 * before taking any action.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            val isEnabled = AppPreferences.isAutostartOnBoot(context)
            if (!isEnabled) {
                Log.d(TAG, "Boot received but auto-start is disabled by user. Ignoring event (Zero-Intrusion).")
                return
            }

            Log.i(TAG, "Boot completed & user opted in. Launching UsbIpServerService in background.")
            try {
                UsbIpServerService.startService(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start USB/IP server on boot", e)
            }
        }
    }
}
