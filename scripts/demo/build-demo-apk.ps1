[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ServerOrigin,
    [int]$MaxWorkers = 2
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$web = Join-Path $root "web"

try { $uri = [Uri]$ServerOrigin } catch { throw "ServerOrigin must be an absolute HTTPS origin." }
if (-not $uri.IsAbsoluteUri -or $uri.Scheme -ne "https" -or
    $uri.AbsolutePath -ne "/" -or $uri.Query -or $uri.Fragment -or $uri.UserInfo) {
    throw "ServerOrigin must be a clean HTTPS origin without path, credentials, query, or fragment."
}
$origin = $uri.GetLeftPart([UriPartial]::Authority)

$old = @{
    VITE_NATIVE_SHELL = $env:VITE_NATIVE_SHELL
    VITE_DEMO_MODE = $env:VITE_DEMO_MODE
    VITE_API_BASE_URL = $env:VITE_API_BASE_URL
    VITE_API_ALLOWED_ORIGINS = $env:VITE_API_ALLOWED_ORIGINS
}
$env:VITE_NATIVE_SHELL = "true"
$env:VITE_DEMO_MODE = "true"
$env:VITE_API_BASE_URL = $origin
$env:VITE_API_ALLOWED_ORIGINS = $origin

Push-Location $web
try {
    & npm.cmd run build:demo
    if ($LASTEXITCODE -ne 0) { throw "Demo web bundle failed." }
    & npx.cmd --no-install cap sync android
    if ($LASTEXITCODE -ne 0) { throw "Capacitor Android sync failed." }
    $nativeAssetRoot = Join-Path $web "android\app\src\main\assets\public"
    $nativeScripts = @(Get-ChildItem -LiteralPath $nativeAssetRoot -Recurse -File -Filter "*.js")
    $originEmbedded = $nativeScripts | Select-String -Pattern $origin -SimpleMatch -Quiet
    $personaEntryEmbedded = $nativeScripts | Select-String -Pattern "/api/public/demo/personas" -SimpleMatch -Quiet
    if ($nativeScripts.Count -eq 0 -or -not $originEmbedded -or -not $personaEntryEmbedded) {
        throw "Synced Android bundle is missing the public Demo origin or passwordless persona entry."
    }
    Push-Location "android"
    try {
        & .\gradlew.bat assembleDebug --no-daemon "--max-workers=$MaxWorkers"
        if ($LASTEXITCODE -ne 0) { throw "Android APK build failed." }
    } finally { Pop-Location }
} finally {
    Pop-Location
    foreach ($name in $old.Keys) {
        [Environment]::SetEnvironmentVariable($name, $old[$name], "Process")
    }
}

$apk = Join-Path $web "android\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path -LiteralPath $apk)) { throw "APK was not produced: $apk" }
$downloadDir = Join-Path $root "src\main\resources\static\downloads"
New-Item -ItemType Directory -Force -Path $downloadDir | Out-Null
$downloadApk = Join-Path $downloadDir "inner-cosmos-demo.apk"
Copy-Item -LiteralPath $apk -Destination $downloadApk -Force
$hash = (Get-FileHash -LiteralPath $downloadApk -Algorithm SHA256).Hash.ToLowerInvariant()

[pscustomobject]@{
    Status = "PASS"
    ServerOrigin = $origin
    Apk = $downloadApk
    Sha256 = $hash
} | Format-List
