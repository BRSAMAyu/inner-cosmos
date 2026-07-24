[CmdletBinding()]
param([switch]$DeleteData)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$stateDir = Join-Path $root ".demo-runtime"
$compose = Join-Path $root "deploy\compose\public-demo.yml"
$runtimeEnvFile = Join-Path $stateDir "infrastructure.env"
$runtimeEnv = if (Test-Path $runtimeEnvFile) {
    Get-Content -LiteralPath $runtimeEnvFile -Raw | ConvertFrom-StringData
} else { @{} }

if (Test-Path (Join-Path $stateDir "cloudflared.pid")) {
    $pidValue = (Get-Content (Join-Path $stateDir "cloudflared.pid") -Raw).Trim()
    if ($pidValue -match "^\d+$") {
        Stop-Process -Id ([int]$pidValue) -Force -ErrorAction SilentlyContinue
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
    Remove-Item -LiteralPath (Join-Path $stateDir "cloudflared.pid") -Force -ErrorAction SilentlyContinue
}
Write-Host "PUBLIC_DEMO_STOPPED data_deleted=$($DeleteData.IsPresent)"
