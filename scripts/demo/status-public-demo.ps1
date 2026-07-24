[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$stateDir = Join-Path $root ".demo-runtime"
& docker ps `
    --filter "label=com.docker.compose.project=inner-cosmos-public-demo" `
    --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"
if ($LASTEXITCODE -ne 0) { throw "Unable to inspect the public demo compose project." }
if (Test-Path (Join-Path $stateDir "demo-info.txt")) {
    Get-Content -LiteralPath (Join-Path $stateDir "demo-info.txt")
}
$apk = Join-Path $root "src\main\resources\static\downloads\inner-cosmos-demo.apk"
if (Test-Path -LiteralPath $apk) {
    $actualHash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Host "apk_sha256_actual=$actualHash"
}
$pidFile = Join-Path $stateDir "cloudflared.pid"
if (Test-Path -LiteralPath $pidFile) {
    $pidValue = (Get-Content -LiteralPath $pidFile -Raw).Trim()
    $running = $pidValue -match "^\d+$" -and
        $null -ne (Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue)
    Write-Host "tunnel_running=$running"
}
