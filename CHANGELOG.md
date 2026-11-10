# CHANGELOG - USBAnywhere Android

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
