@echo off
:: ============================================================================
:: USBAnywhere & Sunshine Streaming Handoff - DO COMMAND
:: Automatically switches Android TV/Tablet from Local Navigation to PC Streaming
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

echo [USBAnywhere] Forwarding gamepad to Sunshine PC stream at %TARGET_IP%:%PORT%...
curl.exe -s --connect-timeout 2 -m 3 -X POST "http://%TARGET_IP%:%PORT%/api/stream/start" >nul 2>&1

exit /b 0
