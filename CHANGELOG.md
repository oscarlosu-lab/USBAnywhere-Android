# CHANGELOG - USBAnywhere Android

## [3.4.0-rc.1] - 2026-11-26 (Release Candidate)

### Added
- **Sunshine Webhook Token Authentication & Header Bounds (`SunshineHandoffServer.kt`)**:
  - Added optional `X-USBAnywhere-Token` / `Authorization: Bearer` authentication on HTTP port `3242` and strict HTTP header size bounds.
- **Dynamic TCP `3241` Inventory Parser (`DynamicInventoryParser.kt`)**:
  - Structured JSON parser for dynamic remote device discovery and listing over TCP `3241`.

## [3.3.2] - 2026-11-20

### Fixed
- **UVC / HTC Vive Facial Tracker USB Permission (`CAMERA`)**: Added `android.permission.CAMERA` and chained runtime `CAMERA` permission prompts before `UsbManager.requestPermission()` for `USB_CLASS_VIDEO` devices on Android 10+ and Meta Quest 3.

## [3.3.1] - 2026-11-15

### Fixed
- **USB Configuration `bInterval` Clamping (`UrbSession.sanitizeConfigDescriptor`)**:
  - Clamps zero `bInterval` values on Interrupt (`0x03`) and Isochronous (`0x01`) endpoints inside both live EP0 and fallback `0x02` Configuration descriptors to `1` so strict Windows `USBHUB3` host validation enumerates non-compliant USB peripherals cleanly.
- **USB String Descriptor Fallback Synthesis (`UrbSession.extractDescriptorFallback`)**:
  - Synthesizes `0x03` String descriptors (`0x0409` en-US LANGID and UTF-16LE Manufacturer, Product, and SerialNumber strings from Android `UsbDevice` metadata) when control IN EP0 `GET_DESCRIPTOR` stalls on hardware keyboards, mice, or hubs.

---

## [3.3.0] - 2026-11-10

### Added
- **Android TV / Nvidia Shield D-Pad & Gamepad Navigation (`TvFocus.kt`)**:
  - Full remote control and gamepad navigation across all tabs, top bar actions, switches, buttons, and device cards with high-contrast focus borders (`#D2F800`) and subtle scaling (`1.02x`).

### Fixed
- **USB Keyboard & Pure HID Enumeration on Windows UDE Clients (Issue #8)**:
  - Fixed `USB\VID_0000&PID_0002` enumeration failure when attaching USB keyboards and composite HID peripherals exported from Android:
    - Accurate USB speed deduction (`USB_SPEED_LOW` / `USB_SPEED_FULL` / `USB_SPEED_HIGH`) instead of hardcoding `USB_SPEED_HIGH`.
    - Cleared `URB_SHORT_NOT_OK` (`0x0001`) on USBDEVFS Bulk/Interrupt IN URBs to prevent short-packet stalls on `usbip2_ude.sys`.
    - Skipped `0x0600` (`DEVICE_QUALIFIER`) control probes on pure HID devices in `UsbHostManager.refineDevice`.
    - Added `extractDescriptorFallback` serving cached `0x01` (Device), `0x02` (Configuration), and `0x21` (HID) descriptors from `UsbDeviceConnection.getRawDescriptors()`, plus synthesized `0x22` HID Report Descriptors (Boot Keyboard, Boot Mouse, Gamepad) and benign `USBIP_STATUS_OK` completion for stalled `SET_IDLE` / `SET_PROTOCOL` requests.

---

## [3.2.0] - 2026-11-01

### Added
- **Obsidian Precision Design System (v3.2.0)**:
  - High-contrast dark obsidian surfaces (`#0B0C0E` / `#121418`), electric lime primary actions (`#D2F800`), and On-Demand Dynamic Control API (`TCP 3241` `LIST` / `BIND` / `UNBIND` + `UDP 3241` LAN discovery).
