[CmdletBinding()]
param(
    [switch]$Repair,
    [string]$Avd = "Medium_Phone_API_36.1"
)

$ErrorActionPreference = "Stop"
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$required = [ordered]@{
    adb = Join-Path $sdk "platform-tools\adb.exe"
    emulator = Join-Path $sdk "emulator\emulator.exe"
    sdkmanager = Join-Path $sdk "cmdline-tools\latest\bin\sdkmanager.bat"
    avdmanager = Join-Path $sdk "cmdline-tools\latest\bin\avdmanager.bat"
}

function Assert-Command([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) { throw "Missing required command: $Name" }
    return $command.Source
}

Write-Host "Inner Cosmos mobile doctor"
Write-Host "Android SDK: $sdk"
$java = Assert-Command "java"
$node = Assert-Command "node"
$pnpm = Assert-Command "pnpm"
$docker = Assert-Command "docker"
$cargo = Assert-Command "cargo"
foreach ($item in $required.GetEnumerator()) {
    if (-not (Test-Path -LiteralPath $item.Value)) { throw "Missing Android tool $($item.Key): $($item.Value)" }
}

$javaVersion = (& $java --version | Select-Object -First 1)
if ($javaVersion -notmatch '(?:java|openjdk) 21\.') { throw "JDK 21 is required; found $javaVersion" }
if ((& $node --version) -notmatch '^v22\.') { throw "Node 22 is required" }
if ((& $pnpm --version) -notmatch '^11\.9\.') { throw "pnpm 11.9.x is required" }
if ((& $cargo --version) -notmatch '^cargo 1\.95\.') { Write-Warning "Rust differs from the verified 1.95 baseline" }

$packages = & $required.sdkmanager --list_installed 2>&1 | Out-String
$wanted = @(
    "platform-tools",
    "emulator",
    "platforms;android-36",
    "build-tools;36.1.0",
    "system-images;android-36.1;google_apis_playstore;x86_64"
)
$missing = @($wanted | Where-Object { $packages -notmatch [regex]::Escape($_) })
if ($missing.Count -gt 0 -and $Repair) {
    & $required.sdkmanager $missing
    if ($LASTEXITCODE -ne 0) { throw "sdkmanager repair failed" }
} elseif ($missing.Count -gt 0) {
    throw "Missing SDK packages: $($missing -join ', '). Run with -Repair."
}

$avds = @(& $required.emulator -list-avds)
if ($avds -notcontains $Avd) {
    if (-not $Repair) { throw "Missing AVD '$Avd'. Run with -Repair." }
    "no" | & $required.avdmanager create avd --force --name $Avd --package "system-images;android-36.1;google_apis_playstore;x86_64" --device "medium_phone"
    if ($LASTEXITCODE -ne 0) { throw "AVD creation failed" }
}

$acceleration = & $required.emulator -accel-check 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) { throw "Android virtualization acceleration is unavailable: $acceleration" }
& $docker version --format '{{.Server.Version}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Docker Desktop engine is not available" }

$drive = Get-PSDrive -Name ([IO.Path]::GetPathRoot($sdk).Substring(0,1))
if ($drive.Free -lt 20GB) { throw "At least 20 GB free disk space is required" }

Write-Host "DOCTOR_PASS"
Write-Host "JDK=$javaVersion Node=$(& $node --version) pnpm=$(& $pnpm --version)"
Write-Host "AVD=$Avd acceleration=PASS Docker=PASS freeGB=$([math]::Round($drive.Free / 1GB, 1))"
