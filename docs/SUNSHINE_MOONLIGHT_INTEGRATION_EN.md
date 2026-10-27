# ☀️ Sunshine & Moonlight Integration Guide — USBAnywhere Android

🌐 **Language / Idioma:** [Español](SUNSHINE_MOONLIGHT_INTEGRATION_ES.md) | **English**

This guide demonstrates how to configure automatic gamepad handoff between **Android TV / Google TV** and your **Sunshine PC Host** using Moonlight.

---

## 🎯 What Problem Does This Solve?

When streaming via Moonlight on Android TV or Nvidia Shield:
1. **Gamepad Hijacking**: If your gamepad remains connected via USB/IP to the PC, exiting a game leaves you unable to navigate Android TV home menus because the PC still owns the USB device.
2. **Setup Friction**: Having to manually toggle servers or unplug cables before and after each gaming session breaks the couch console experience.

### 💡 The USBAnywhere Solution:
USBAnywhere embeds a lightweight **HTTP micro-server (port 3242)** in the Android app. When Sunshine starts an application, it sends a webhook switching the gamepad to **`STREAMING_PC`**. When the stream ends, Sunshine immediately sends an Undo command restoring the gamepad to **`LOCAL_ANDROID_TV`**.

---

## 🚀 Method 1: Direct Sunshine Web UI Configuration (Recommended)

Sunshine provides **Command Preparations** both globally and per individual application.

1. Open the Sunshine Web UI on your PC: `https://localhost:47990`
2. Navigate to the **Configuration** tab (or edit an app under **Applications**).
3. Find the **Command Preparations** section and add a new entry:

| Field | Command |
|---|---|
| **Do Command** | `curl.exe -s --connect-timeout 2 -m 3 -X POST "http://%SUNSHINE_CLIENT_IP%:3242/api/stream/start"` |
| **Undo Command** | `curl.exe -s --connect-timeout 2 -m 3 -X POST "http://%SUNSHINE_CLIENT_IP%:3242/api/stream/stop"` |

> [!TIP]
> **Dynamic `%SUNSHINE_CLIENT_IP%` Variable**: Sunshine automatically injects the IP address of the currently connecting Moonlight client. You don't need a static IP; it dynamically targets whatever device initiated the stream.
> If your Sunshine build doesn't populate this variable, replace `%SUNSHINE_CLIENT_IP%` with your Android TV's local LAN IP (e.g. `192.168.1.150`).

---

## 🛠️ Method 2: Fail-Safe Helper Scripts (.bat / .ps1)

Pre-built helper scripts with safety timeouts are located in the `docs/scripts/` folder:

* [`docs/scripts/sunshine_usbanywhere_do.bat`](scripts/sunshine_usbanywhere_do.bat)
* [`docs/scripts/sunshine_usbanywhere_undo.bat`](scripts/sunshine_usbanywhere_undo.bat)
* [`docs/scripts/sunshine_usbanywhere_handoff.ps1`](scripts/sunshine_usbanywhere_handoff.ps1)

### In Sunshine Web UI:
* **Do Command**:
  ```cmd
  cmd /c "C:\Path\To\sunshine_usbanywhere_do.bat"
  ```
* **Undo Command**:
  ```cmd
  cmd /c "C:\Path\To\sunshine_usbanywhere_undo.bat"
  ```

---

## 📄 Method 3: Direct `apps.json` Sunshine Configuration

If you prefer editing Sunshine's `apps.json` directly (typically located in `C:\Program Files\Sunshine\config\apps.json`), append the command preparation:

```json
{
  "name": "Desktop (with USBAnywhere)",
  "output": "",
  "cmd": "",
  "prep-cmd": [
    {
      "do": "curl.exe -s --connect-timeout 2 -m 3 -X POST \"http://%SUNSHINE_CLIENT_IP%:3242/api/stream/start\"",
      "undo": "curl.exe -s --connect-timeout 2 -m 3 -X POST \"http://%SUNSHINE_CLIENT_IP%:3242/api/stream/stop\""
    }
  ]
}
```

---

## 🎮 Gamepad Hardware Quick-Toggle

If you ever need to toggle control without closing Moonlight or touching a keyboard:

* Hold **`Guide + Select`** (or **`Back + Start`**) on your controller for **2.5 seconds**.
* The Android TV screen will display a reactive HUD toast notification:
  * `🎮 Local Android TV Control Restored`
* Holding it again for 2.5s switches back:
  * `🚀 Gamepad Forwarded to Sunshine PC Streaming`

---

## 🧪 Network Diagnostics (From PC)

Verify that your Android TV handoff daemon is listening:

```powershell
curl.exe http://<ANDROID_TV_IP>:3242/api/status

# Expected Response:
# {"status":"ok","mode":"LOCAL_ANDROID_TV","title":"Local Android TV","port":3242}
```
