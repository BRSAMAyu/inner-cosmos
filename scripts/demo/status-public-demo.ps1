[CmdletBinding()]
param([int]$HttpTimeoutSeconds = 12)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$stateDir = Join-Path $root ".demo-runtime"
$infoFile = Join-Path $stateDir "demo-info.txt"
$pidFile = Join-Path $stateDir "cloudflared.pid"
$cloudflared = Join-Path $root "scripts\demo\bin\cloudflared.exe"
$apk = Join-Path $root "src\main\resources\static\downloads\inner-cosmos-demo.apk"
. (Join-Path $PSScriptRoot "public-demo-common.ps1")

& docker ps `
    --filter "label=com.docker.compose.project=inner-cosmos-public-demo" `
    --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"
$dockerReachable = $LASTEXITCODE -eq 0
$info = Read-DemoInfo -Path $infoFile
$mode = if ($info["tunnel_mode"] -in @("quick", "named")) { [string]$info["tunnel_mode"] } else { "quick" }
$recordedPort = if ($info["port"] -match "^\d+$") { [int]$info["port"] } else { 0 }
$tunnel = Get-TunnelProcessEvidence -PidFile $pidFile -ExpectedExecutable $cloudflared -Mode $mode -Port $recordedPort
$http = if ($info["origin"]) {
    Test-PublicDemoHttp -Origin ([string]$info["origin"]) -TimeoutSec $HttpTimeoutSeconds
} else {
    [pscustomobject]@{ Healthy = $false; StatusCode = 0; Reason = "origin-missing" }
}
$actualHash = if (Test-Path -LiteralPath $apk) {
    (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
} else { "" }
$apkMatches = -not [string]::IsNullOrWhiteSpace($actualHash) -and
    -not [string]::IsNullOrWhiteSpace([string]$info["apk_sha256"]) -and
    $actualHash -eq ([string]$info["apk_sha256"]).ToLowerInvariant()
$ready = $dockerReachable -and $tunnel.Valid -and $http.Healthy -and $apkMatches

Write-Output "demo_state=$(if ($ready) { 'READY' } else { 'STALE_OR_UNAVAILABLE' })"
Write-Output "tunnel_mode=$mode"
Write-Output "tunnel_process_valid=$($tunnel.Valid)"
Write-Output "tunnel_reason=$($tunnel.Reason)"
Write-Output "public_http_healthy=$($http.Healthy)"
Write-Output "public_http_status=$($http.StatusCode)"
Write-Output "apk_sha256_matches=$apkMatches"
Write-Output "apk_sha256_actual=$actualHash"
if ($ready) {
    Write-Output "origin=$($info["origin"])"
    Write-Output "app=$($info["app"])"
    Write-Output "apk=$($info["apk"])"
    Write-Output "provider=$($info["provider"])"
    Write-Output "verification=$($info["verification"])"
} elseif ($info["origin"]) {
    Write-Output "stale_origin_recorded=$($info["origin"])"
}

$watchdogPidFile = Join-Path $stateDir "watchdog.pid"
$watchdogRunning = $false
if (Test-Path -LiteralPath $watchdogPidFile) {
    $watchdogPid = (Get-Content -LiteralPath $watchdogPidFile -Raw).Trim()
    $watchdogRunning = $watchdogPid -match "^\d+$" -and
        $null -ne (Get-Process -Id ([int]$watchdogPid) -ErrorAction SilentlyContinue)
}
Write-Output "watchdog_running=$watchdogRunning"
if (-not $ready) { exit 2 }
