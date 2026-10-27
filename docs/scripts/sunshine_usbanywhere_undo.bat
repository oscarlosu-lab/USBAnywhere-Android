@echo off
:: ============================================================================
:: USBAnywhere & Sunshine Streaming Handoff - UNDO COMMAND
:: Automatically restores Android TV/Tablet control back to Local Navigation
:: ============================================================================

set PORT=3242
set TARGET_IP=%SUNSHINE_CLIENT_IP%

:: Fallback if SUNSHINE_CLIENT_IP is not provided by older Sunshine versions
if "%TARGET_IP%"=="" (
    if not "%~1"=="" (
        set TARGET_IP=%~1
    ) else (
        echo [USBAnywhere] Warning: SUNSHINE_CLIENT_IP not defined. Specify IP as argument: %0 [Android_IP]
        exit /b 0
    )
)

echo [USBAnywhere] Restoring gamepad to local Android TV navigation at %TARGET_IP%:%PORT%...
curl.exe -s --connect-timeout 2 -m 3 -X POST "http://%TARGET_IP%:%PORT%/api/stream/stop" >nul 2>&1

exit /b 0
