<#
.SYNOPSIS
    USBAnywhere & Sunshine Streaming Handoff Script (PowerShell)
.DESCRIPTION
    Integrates Sunshine Game Streamer with USBAnywhere Android.
    Invokes the embedded HTTP micro-server (port 3242) on start and stop events.
.PARAMETER Action
    'start' (Do Command), 'stop' (Undo Command), 'toggle', or 'status'.
.PARAMETER TargetIp
    IP address of the Android TV/Tablet. Defaults to $env:SUNSHINE_CLIENT_IP.
.PARAMETER Port
    Sunshine handoff port configured in USBAnywhere Android (default 3242).
.EXAMPLE
    .\sunshine_usbanywhere_handoff.ps1 -Action start
    .\sunshine_usbanywhere_handoff.ps1 -Action stop
    .\sunshine_usbanywhere_handoff.ps1 -Action status -TargetIp 192.168.1.150
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet("start", "stop", "toggle", "status")]
    [string]$Action,

    [Parameter(Position = 1)]
    [string]$TargetIp = $env:SUNSHINE_CLIENT_IP,

    [int]$Port = 3242
)

if ([string]::IsNullOrWhiteSpace($TargetIp)) {
    Write-Warning "[USBAnywhere] SUNSHINE_CLIENT_IP environment variable is empty and no TargetIp was specified."
    exit 0
}

$endpoint = switch ($Action) {
    "start"  { "/api/stream/start" }
    "stop"   { "/api/stream/stop" }
    "toggle" { "/api/toggle" }
    "status" { "/api/status" }
}

$url = "http://$TargetIp`:$Port$endpoint"

try {
    $response = Invoke-RestMethod -Uri $url -Method Post -TimeoutSec 2 -ErrorAction Stop
    Write-Host "[USBAnywhere] Handoff ($Action) OK -> Mode: $($response.mode)" -ForegroundColor Cyan
} catch {
    Write-Verbose "[USBAnywhere] Handoff request failed (device might be offline or using different port): $_"
}

exit 0
