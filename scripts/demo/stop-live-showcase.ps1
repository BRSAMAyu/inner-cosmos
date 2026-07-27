[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$stateFile = Join-Path $root ".demo-runtime\live-showcase.json"

if (-not (Test-Path -LiteralPath $stateFile)) {
    Write-Host "LIVE_SHOWCASE_ALREADY_STOPPED"
    return
}

$state = Get-Content -LiteralPath $stateFile -Raw -Encoding utf8 | ConvertFrom-Json
foreach ($forward in @($state.forwards)) {
    $process = Get-Process -Id ([int]$forward.pid) -ErrorAction SilentlyContinue
    if ($null -ne $process -and $process.ProcessName -eq "kubectl") {
        Stop-Process -Id $process.Id -Force
        Write-Host "stopped=$($forward.name) pid=$($process.Id)"
    }
}
Remove-Item -LiteralPath $stateFile -Force
Write-Host "LIVE_SHOWCASE_STOPPED"
