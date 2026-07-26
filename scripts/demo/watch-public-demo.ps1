[CmdletBinding()]
param(
    [int]$IntervalSeconds = 10,
    [switch]$RestartNamedTunnel
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$stateDir = Join-Path $root ".demo-runtime"
$infoFile = Join-Path $stateDir "demo-info.txt"
$pidFile = Join-Path $stateDir "cloudflared.pid"
$cloudflared = Join-Path $root "scripts\demo\bin\cloudflared.exe"
$stdout = Join-Path $stateDir "cloudflared.stdout.log"
$stderr = Join-Path $stateDir "cloudflared.stderr.log"
$watchdogPidFile = Join-Path $stateDir "watchdog.pid"
. (Join-Path $PSScriptRoot "public-demo-common.ps1")

if ($IntervalSeconds -lt 3) { throw "IntervalSeconds must be at least 3." }
$PID | Set-Content -Encoding ascii $watchdogPidFile
$consecutiveHealthFailures = 0
$restartAttempts = 0

try {
    while ($true) {
        $info = Read-DemoInfo -Path $infoFile
        if (-not $info["origin"] -or -not $info["tunnel_mode"]) {
            throw "Demo metadata is absent or incomplete; watchdog will not guess an origin."
        }
        $mode = [string]$info["tunnel_mode"]
        if ($mode -notin @("quick", "named")) { throw "Unknown tunnel mode '$mode'." }
        $recordedPort = if ($info["port"] -match "^\d+$") { [int]$info["port"] } else { 0 }
        $tunnel = Get-TunnelProcessEvidence -PidFile $pidFile `
            -ExpectedExecutable $cloudflared -Mode $mode -Port $recordedPort
        if (-not $tunnel.Valid) {
            if ($mode -ne "named" -or -not $RestartNamedTunnel) {
                throw "Tunnel is unavailable ($($tunnel.Reason)); Quick Tunnel cannot be restarted without changing the APK origin."
            }
            if ($restartAttempts -ge 6) {
                throw "Named Tunnel restart limit reached."
            }
            $restartAttempts++
            $restartPort = if ($info["port"] -match "^\d+$") { [int]$info["port"] } else { 8080 }
            $replacement = Start-DemoTunnel -Executable $cloudflared -Mode named -Port $restartPort `
                -Stdout $stdout -Stderr $stderr
            $replacement.Id | Set-Content -Encoding ascii $pidFile
            Start-Sleep -Seconds ([Math]::Min(15, 2 * $restartAttempts))
            continue
        }

        $health = Test-PublicDemoHttp -Origin ([string]$info["origin"]) -TimeoutSec 10
        if ($health.Healthy) {
            if ($consecutiveHealthFailures -gt 0) {
                Write-Output "$((Get-Date).ToString('o')) public-demo recovered"
            }
            $consecutiveHealthFailures = 0
            $restartAttempts = 0
        } else {
            $consecutiveHealthFailures++
            Write-Warning "$((Get-Date).ToString('o')) public health failed ($($health.Reason)); attempt=$consecutiveHealthFailures"
        }
        Start-Sleep -Seconds $IntervalSeconds
    }
} finally {
    Remove-Item -LiteralPath $watchdogPidFile -Force -ErrorAction SilentlyContinue
}
