[CmdletBinding()]
param([switch]$SkipStackBuild)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$base = Join-Path $root "deploy\compose\mobile-local.yml"
$override = Join-Path $root "deploy\compose\desktop-local.yml"

$compose = @("compose", "-f", $base, "-f", $override)
if ($SkipStackBuild) { & docker @compose up -d }
else { & docker @compose up -d --build }
if ($LASTEXITCODE -ne 0) { throw "Desktop local stack failed to start" }

$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 2
    try { $healthy = (Invoke-RestMethod -Uri "http://127.0.0.1:8080/actuator/health" -TimeoutSec 3).status -eq "UP" }
    catch { $healthy = $false }
} while (-not $healthy -and (Get-Date) -lt $deadline)
if (-not $healthy) { throw "Desktop local API did not become healthy" }

Push-Location (Join-Path $root "web")
try {
    & .\node_modules\.bin\tauri.cmd dev --config src-tauri/tauri.local.conf.json
    if ($LASTEXITCODE -ne 0) { throw "Tauri desktop development run failed" }
} finally { Pop-Location }
