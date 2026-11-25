# Security Policy — USBAnywhere Android

## Supported Versions

| Version | Supported | Status |
| --- | --- | --- |
| 3.3.x | Yes | Active (Current: v3.3.2) |
| < 3.3 | No | Unsupported |

## Security Model
- **Non-Intrusive Components**: `BootCompletedReceiver` and `UsbAttachedReceiver` are disabled by default and opt-in only.
- **Rootless OTG Mediation**: Uses standard Android `UsbManager` user-space permissions (with `CAMERA` runtime permission gating for `USB_CLASS_VIDEO` UVC devices on Android 10+).
- **Backup Disabled / Hardened**: Sensitive runtime preferences are excluded from cloud backup.

## Reporting a Vulnerability
Please report security vulnerabilities privately via [GitHub Security Advisories](https://github.com/oscarlosu-lab/USBAnywhere-Android/security/advisories/new) instead of opening a public issue.
