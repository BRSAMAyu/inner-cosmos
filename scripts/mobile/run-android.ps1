[CmdletBinding()]
param(
    [string]$Avd = "Medium_Phone_API_36.1",
    [switch]$Reset,
    [switch]$SkipDoctor,
    [switch]$SkipStackBuild
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$emulator = Join-Path $sdk "emulator\emulator.exe"

if (-not $SkipDoctor) { & (Join-Path $PSScriptRoot "doctor.ps1") -Avd $Avd }
& (Join-Path $PSScriptRoot "run-local-stack.ps1") -Action Up -Rebuild:(-not $SkipStackBuild)

$running = @(& $adb devices | Select-String '^emulator-\d+\s+device')
if ($running.Count -eq 0) {
    $arguments = @("-avd", $Avd, "-no-snapshot-save")
    if ($Reset) { $arguments += "-wipe-data" } else { $arguments += "-no-snapshot-load" }
    Start-Process -FilePath $emulator -ArgumentList $arguments | Out-Null
}

$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 2
    $deviceLine = & $adb devices | Select-String '^emulator-\d+\s+device' | Select-Object -First 1
    if ($deviceLine) {
        $serial = $deviceLine.ToString().Split("`t")[0]
        $boot = (& $adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
    }
} while ($boot -ne "1" -and (Get-Date) -lt $deadline)
if ($boot -ne "1") { throw "AVD did not complete boot" }

Push-Location (Join-Path $root "web")
try {
    & pnpm mobile:android:local
    if ($LASTEXITCODE -ne 0) { throw "Android debug build failed" }
} finally { Pop-Location }

$apk = Join-Path $root "web\android\app\build\outputs\apk\debug\app-debug.apk"
& $adb -s $serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw "APK install failed" }
& $adb -s $serial shell am force-stop sg.innercosmos.app.dev
& $adb -s $serial shell am start -W -n "sg.innercosmos.app.dev/sg.innercosmos.app.MainActivity"
if ($LASTEXITCODE -ne 0) { throw "Activity launch failed" }
Start-Sleep -Seconds 3
& (Join-Path $PSScriptRoot "verify-android.ps1") -Serial $serial
