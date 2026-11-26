# Security Policy — USBAnywhere Android

## Supported Versions

| Version | Supported | Status |
| --- | --- | --- |
| 3.4.x | Yes | Latest Stable Release (Current: v3.4.1) |
| 3.3.x | Yes | Active Stable (v3.3.2) |
| < 3.3 | No | Unsupported |

## Security Model
- **Non-Intrusive Components**: `BootCompletedReceiver` and `UsbAttachedReceiver` are disabled by default and opt-in only.
- **Rootless OTG Mediation**: Uses standard Android `UsbManager` user-space permissions (with `horizonos.permission.USB_CAMERA` runtime gating on Meta Quest / Horizon OS and `CAMERA` on Android 10+ for `USB_CLASS_VIDEO` UVC devices).
- **Sunshine Webhook Hardening (`v3.4.0`)**: `SunshineHandoffServer` enforces strict HTTP header size bounds and optional token authentication (`X-USBAnywhere-Token` / `Authorization: Bearer`) on port `3242`.
- **Backup Disabled / Hardened**: Sensitive runtime preferences are excluded from cloud backup.

## Reporting a Vulnerability
Please report security vulnerabilities privately via [GitHub Security Advisories](https://github.com/oscarlosu-lab/USBAnywhere-Android/security/advisories/new) instead of opening a public issue.
