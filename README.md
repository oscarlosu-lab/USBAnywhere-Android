# 🌐 USBAnywhere Android — The Free, Open-Source VirtualHere Alternative for Android & Android TV

🌐 **Language / Idioma:** **English** | [Español](README_ES.md)

**v1.0.0** | **Part of the [USBAnywhere Ecosystem](https://github.com/oscarlosu-lab/USBAnywhere)**  
[🌍 Official Website](https://oscarlosu-lab.github.io/USBAnywhere/) | [🔗 USBAnywhere Windows Client](https://github.com/oscarlosu-lab/USBAnywhere) | [🖥️ USBAnywhere Windows Server](https://github.com/oscarlosu-lab/USBAnywhere-Server) | [💬 Reddit Community](https://www.reddit.com/r/UsbAnywhere/) | [☀️ Sunshine & Moonlight Setup](docs/SUNSHINE_MOONLIGHT_INTEGRATION_EN.md)

[![Website](https://img.shields.io/badge/Website-USBAnywhere-06B6D4?style=flat-square&logo=googlechrome&logoColor=white)](https://oscarlosu-lab.github.io/USBAnywhere/)
[![Reddit Community](https://img.shields.io/badge/Reddit-r%2FUsbAnywhere-FF4500?style=flat-square&logo=reddit&logoColor=white)](https://www.reddit.com/r/UsbAnywhere/)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-Support%20Project-FF5E5B?style=flat-square&logo=ko-fi&logoColor=white)](https://ko-fi.com/oscarlosu)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![Android TV](https://img.shields.io/badge/Android%20TV-Leanback%20Supported-blue.svg)](https://developer.android.com/tv)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20Material%203-purple.svg)](https://developer.android.com/jetpack/compose)
[![Protocol](https://img.shields.io/badge/Protocol-USB%2FIP%20(RFC%20Draft)-orange.svg)](https://docs.kernel.org/usb/usbip_protocol.html)
[![License](https://img.shields.io/badge/License-GPL--3.0-red.svg)](LICENSE)
[![Quality Gate](https://img.shields.io/badge/Tests-38%2F38%20Passing-brightgreen.svg)]()
[![Footprint](https://img.shields.io/badge/APK%20Size-2.07%20MB%20(90%25%20Shrunk)-success.svg)]()

> **Why pay $49.00 per server and be restricted to a single USB device?**  
> **USBAnywhere Android** is the definitive open-source USB-over-IP engine for Android phones, tablets, and **Android TV / Google TV (Nvidia Shield)**. It seamlessly integrates into the **USBAnywhere Suite**, enabling zero-cost, multi-device USB tunneling between your living room TV and remote gaming PCs running Sunshine/Moonlight or the USBAnywhere Windows Client.

---

## ⚔️ Why Choose USBAnywhere Over VirtualHere?

| Feature | **USBAnywhere Android** | **VirtualHere Android** |
| :--- | :---: | :---: |
| **Pricing & License** | **100% Free & Open Source (GPLv3)** | 💰 $49.00 USD per server (locked to hardware) |
| **Simultaneous USB Devices** | **♾️ Unlimited** | ⚠️ 1 Device Only in trial |
| **Root Requirement** | 🛡️ **Zero Root Required** (OTG user-space mediation) | ⚠️ Generic Android driver |
| **Sunshine & Moonlight Integration**| ⚡ **Built-in Webhook (Port 3242) + Auto-Handoff** | ❌ Manual batch scripts and disconnect pain |
| **Gamepad Quick-Toggle** | 🎮 **`Guide + Select` (2.5s)** switch TV/PC | ❌ None |
| **DualSense & Sim Racing** | 🏎️ **Dedicated Haptics Buffer & FFB Jitter-Guard** | ⚠️ Packet drop & wheel shudder over Wi-Fi |
| **Android TV / Shield 10-Foot UI** | 📺 **Native D-Pad Focus & Leanback Banner** | 🕰️ Basic Android interface |
| **WAN & Zero-Trust Security** | 🔒 **Tailscale, WireGuard, Cyber QR & PIN Auth** | ❌ Manual port forwarding |
| **Package Size** | 🚀 **2.07 MB APK** (Includes 20.5K device database) | ~6 MB |

---

## 🌐 The USBAnywhere Triad: Multi-Platform Synergy

```mermaid
flowchart LR
    subgraph LivingRoom ["🛋️ Living Room (Android TV / Nvidia Shield)"]
        ATV["<b>USBAnywhere Android</b><br><i>(Rootless Server / Client)</i>"]
        GP["🎮 Gamepad / Sim Wheel / Hotas"]
        DAC["🎧 USB DAC / Mic"]
        GP --> ATV
        DAC --> ATV
    end

    subgraph Network ["🌐 Low-Latency Network (LAN / Tailscale VPN)"]
        direction TB
        NET["TCP Port 3240 (USB/IP)<br>HTTP Port 3242 (Sunshine Webhook)<br>mDNS Discovery (_usbanywhere._tcp.)"]
    end

    subgraph GamingPC ["🖥️ Gaming Room / Cloud Host"]
        WIN_C["<b>USBAnywhere Windows Client</b><br><i>(WHQL Driver + WSK Engine)</i>"]
        WIN_S["<b>USBAnywhere Windows Server</b><br><i>(usbipd-win GUI)</i>"]
        SUN["☀️ Sunshine Game Streamer"]
    end

    ATV <==> NET
    NET <==> WIN_C
    NET <==> WIN_S
    NET <==> SUN
```

1. **Android as Living Room USB Server**: Plug racing wheels (Logitech G29, Fanatec), flight sticks, or DualSense gamepads into your Nvidia Shield or Android TV box. Stream your game with Sunshine/Moonlight while the [USBAnywhere Windows Client](https://github.com/oscarlosu-lab/USBAnywhere) receives raw USB packets with full force feedback and adaptive triggers.
2. **Android as Client for Remote Peripherals**: Access 3D printers (OctoPrint), CNCs, SDR dongles, or office scanners connected to a Windows host running [USBAnywhere Windows Server](https://github.com/oscarlosu-lab/USBAnywhere-Server).
3. **Sunshine Auto-Handoff**: Sunshine triggers `POST /api/stream/start` on launch and `POST /api/stream/stop` on exit, automatically toggling controller ownership without manual intervention.

### 🔗 USBAnywhere Ecosystem Components

| Component | Platform / Role | Description |
|---|---|---|
| **[USBAnywhere Client](https://github.com/oscarlosu-lab/USBAnywhere)** | 🖥️ Windows 10/11 (x64 / ARM64) | Connect to remote USB devices with WHQL drivers & WSK engine |
| **[USBAnywhere Server](https://github.com/oscarlosu-lab/USBAnywhere-Server)** | 🌐 Windows 10/11 (.NET 9.0) | Share local USB peripherals over network with GUI & Auto-Bind |
| **[USBAnywhere Android](https://github.com/oscarlosu-lab/USBAnywhere-Android)** | 📱 Android & Android TV | Rootless USB/IP server, Sunshine handoff & DualSense haptics |
| **[Official Website](https://oscarlosu-lab.github.io/USBAnywhere/)** | 🌍 Web Portal | Interactive guides, documentation & download hub |
| **[Reddit Community](https://www.reddit.com/r/UsbAnywhere/)** | 💬 r/UsbAnywhere | Support, discussions, community setups & announcements |
| **[Support on Ko-fi](https://ko-fi.com/oscarlosu)** | ☕ Community | Support hosting, driver testing & open-source engineering |

---

## ⚡ Key Highlights

### 🔌 Rootless USB/IP Server (Port 3240)
- **Zero Root Required**: Operates completely in user-space using `android.hardware.usb.UsbManager` (`UsbDeviceConnection`, `controlTransfer`, `bulkTransfer`).
- Share gamepads, flight sticks, racing wheels, 3D printers, CNCs, and webcams connected via USB-OTG or USB-C hubs.
- High-Performance Foreground Service with `WakeLock` and `WifiLock` preventing Android OS power throttling.

### 🎮 Gaming Synergy & Esports Polling Engine
- **Sunshine Auto-Handoff & Quick-Toggle**:
  - Embedded HTTP micro-server (Port 3242) for Sunshine `do` and `undo` commands.
  - Gamepad combo (`Guide + Select` held for 2.5s) toggles control between Android TV navigation and PC USB/IP streaming.
- **Sony DualSense Engine**:
  - Circular isochronous audio buffer delivering pristine haptics and adaptive triggers over Wi-Fi without audio underflows.
- **Sim Racing & HOTAS FFB Jitter-Guard**:
  - Monotonic sequence ordering and stale packet dropper eliminating violent wheel shakes from delayed Wi-Fi packets.
- **Multiplayer Hub Slot Locks**:
  - Deterministic P1-P4 player binding tied to USB port topology so controllers never scramble player numbers.
- **Live Esports Gamepad HUD**:
  - Real-time frequency tracker in **Hz**, min/max bounds, jitter ($\sigma$ in ms), analog stick crosshairs, and 1000/8000 Hz synthetic simulator.

### 🔒 WAN Zero-Trust Security & Audio Bridge
- **Tailscale & WireGuard Native Discovery**:
  - Automatically identifies CGNAT (`100.64.0.0/10`) and VPN interfaces.
  - Generates compact pairing tokens (`UA1:...`) and renders dynamic Cyber QR codes natively on Compose Canvas without heavy third-party libraries.
- **Zero-Trust Mutual PIN Authentication & TLS 1.3**:
  - Binary opcode `OP_REQ_AUTH (0x8008)` with ephemeral CSPRNG 6-digit PIN and brute-force lockout (5 attempts = 5 min ban).
- **UAC2 Audio & Headset Microphone Bridge**:
  - 48 kHz / 16-bit PCM stereo bridge with user-selectable buffer profiles: **2ms** (Esports local), **4ms** (Wi-Fi), **8ms** (WAN).

### 📺 10-Foot UI for Android TV & Google TV
- Official Leanback launcher integration with custom high-contrast cyber banner (`tv_banner.xml`).
- Declares `android.hardware.touchscreen=false` for full Android TV Play Store compliance.
- D-Pad remote control focus indicators with glowing `NeonCyan` borders.

### 🗄️ Embedded 20.5K Device Database
- Offline SQLite catalog with 3,427 vendors and 20,551 products indexed from official USB-IF databases.
- Semantic classification: 🎮 Gamepad, 🏎️ Racing Wheel, 🖱️ Mouse, ⌨️ Keyboard, 📷 Camera, 🖨️ 3D Printer, 🎧 Audio, 💾 Storage, 🔌 Generic.

### 🛡️ Opt-In Startup and USB Connection
- Startup and USB-attachment automation are off by default. Enable them from Settings if you want the app to respond automatically.

---

## 🔒 Security

For vulnerability reporting, see the [USBAnywhere Security Policy](https://github.com/oscarlosu-lab/USBAnywhere/blob/main/SECURITY.md).

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**.

> **USBAnywhere Android is 100% Free and Open Source.** You are free to run, study, share, and modify the software under the terms of the GNU General Public License as published by the Free Software Foundation.

See [LICENSE](LICENSE) for full legal terms and conditions.
