[CmdletBinding()]
param([switch]$DeleteData)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$stateDir = Join-Path $root ".demo-runtime"
$compose = Join-Path $root "deploy\compose\public-demo.yml"
$runtimeEnvFile = Join-Path $stateDir "infrastructure.env"
. (Join-Path $PSScriptRoot "public-demo-common.ps1")
$runtimeEnv = if (Test-Path $runtimeEnvFile) {
    Get-Content -LiteralPath $runtimeEnvFile -Raw | ConvertFrom-StringData
} else { @{} }

$infoFile = Join-Path $stateDir "demo-info.txt"
$info = Read-DemoInfo -Path $infoFile
$mode = if ($info["tunnel_mode"] -in @("quick", "named")) { [string]$info["tunnel_mode"] } else { "quick" }
$cloudflared = Join-Path $root "scripts\demo\bin\cloudflared.exe"
$tunnelPidFile = Join-Path $stateDir "cloudflared.pid"
$recordedPort = if ($info["port"] -match "^\d+$") { [int]$info["port"] } else { 0 }
$tunnelEvidence = Get-TunnelProcessEvidence -PidFile $tunnelPidFile `
    -ExpectedExecutable $cloudflared -Mode $mode -Port $recordedPort
if ($tunnelEvidence.Valid) {
    Stop-Process -Id ([int]$tunnelEvidence.Pid) -Force -ErrorAction SilentlyContinue
}
$watchdogPidFile = Join-Path $stateDir "watchdog.pid"
if (Test-Path -LiteralPath $watchdogPidFile) {
    $watchdogPid = (Get-Content -LiteralPath $watchdogPidFile -Raw).Trim()
    if ($watchdogPid -match "^\d+$") {
        $watchdogProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$watchdogPid" -ErrorAction SilentlyContinue
        if ($watchdogProcess -and [string]$watchdogProcess.CommandLine -match "watch-public-demo\.ps1") {
            Stop-Process -Id ([int]$watchdogPid) -Force -ErrorAction SilentlyContinue
        }
    }
}
$env:SPRING_DATASOURCE_PASSWORD = if ($env:SPRING_DATASOURCE_PASSWORD) {
    $env:SPRING_DATASOURCE_PASSWORD
} elseif ($runtimeEnv.SPRING_DATASOURCE_PASSWORD) {
    $runtimeEnv.SPRING_DATASOURCE_PASSWORD
} else { "stop-only-placeholder" }
$env:REDIS_PASSWORD = if ($env:REDIS_PASSWORD) {
    $env:REDIS_PASSWORD
} elseif ($runtimeEnv.REDIS_PASSWORD) {
    $runtimeEnv.REDIS_PASSWORD
} else { "stop-only-placeholder" }
$env:LLM_PROVIDER = if ($env:LLM_PROVIDER) { $env:LLM_PROVIDER } else { "deepseek" }
$env:LLM_API_KEY = if ($env:LLM_API_KEY) { $env:LLM_API_KEY } else { "stop-only-placeholder" }
$env:MEMORY_EMBEDDING_API_KEY = if ($env:MEMORY_EMBEDDING_API_KEY) { $env:MEMORY_EMBEDDING_API_KEY } else { "stop-only-placeholder" }
$env:MEMORY_EMBEDDING_BASE_URL = if ($env:MEMORY_EMBEDDING_BASE_URL) { $env:MEMORY_EMBEDDING_BASE_URL } else { "https://example.invalid/v1" }
$env:TTS_API_KEY = if ($env:TTS_API_KEY) { $env:TTS_API_KEY } else { "stop-only-placeholder" }
$env:TTS_WS_URL = if ($env:TTS_WS_URL) { $env:TTS_WS_URL } else { "wss://example.invalid/ws" }
$args = @("compose", "-p", "inner-cosmos-public-demo", "-f", $compose, "down")
if ($DeleteData) { $args += "--volumes" }
& docker @args
if ($LASTEXITCODE -ne 0) { throw "Unable to stop the public demo compose project." }
if ($DeleteData -and (Test-Path $stateDir)) {
    Remove-Item -LiteralPath $stateDir -Recurse -Force
} elseif (Test-Path $stateDir) {
    Remove-Item -LiteralPath $tunnelPidFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $watchdogPidFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $infoFile -Force -ErrorAction SilentlyContinue
}
Write-Host "PUBLIC_DEMO_STOPPED data_deleted=$($DeleteData.IsPresent)"
