package com.usbanywhere.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log
import com.usbanywhere.data.AppPreferences
import com.usbanywhere.server.UsbIpServerService

/**
 * Handles USB device attachment events.
 * Strictly non-intrusive: Disabled by default in the manifest and checks [AppPreferences]
 * before starting the background service.
 */
class UsbAttachedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UsbAttachedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val isEnabled = AppPreferences.isAutostartOnUsb(context)
            if (!isEnabled) {
                Log.d(TAG, "USB attached but auto-start is disabled by user. Ignoring event (Zero-Intrusion).")
                return
            }

            Log.i(TAG, "USB attached & user opted in. Starting UsbIpServerService in background.")
            try {
                UsbIpServerService.startService(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start USB/IP server on USB attach", e)
            }
        }
    }
}
