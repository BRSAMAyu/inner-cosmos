[CmdletBinding()]
param(
    [string]$Serial = "emulator-5554",
    [string]$EvidenceDirectory = ""
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$apk = Join-Path $root "web\android\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "Debug APK missing: $apk" }
if (-not $EvidenceDirectory) { $EvidenceDirectory = Join-Path $root "evidence\mobile\latest" }
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null

$state = (& $adb -s $Serial get-state 2>&1).Trim()
if ($state -ne "device") { throw "Android target is not ready: $Serial state=$state" }
$package = (& $adb -s $Serial shell pm path sg.innercosmos.app.dev 2>&1 | Out-String).Trim()
if ($package -notmatch '^package:') { throw "sg.innercosmos.app.dev is not installed" }
$focus = (& $adb -s $Serial shell dumpsys activity activities | Select-String "(?:mResumedActivity|topResumedActivity|ResumedActivity)" | Out-String)
if ($focus -notmatch 'sg\.innercosmos\.app\.dev') { throw "Inner Cosmos activity is not resumed" }

$screenshot = Join-Path $EvidenceDirectory "android-launch.png"
& cmd.exe /d /c ('"{0}" -s {1} exec-out screencap -p > "{2}"' -f $adb, $Serial, $screenshot)
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $screenshot)) { throw "Screenshot capture failed" }
# Never persist raw logcat: native plugins can include secure-storage arguments even when the
# JavaScript application never logs them. Evidence is a strict allowlist of process health only.
$processId = (& $adb -s $Serial shell pidof sg.innercosmos.app.dev 2>&1 | Out-String).Trim()
$logSummary = @()
if ($processId -match '^\d+$') {
    $rawLog = & $adb -s $Serial logcat -d --pid $processId -t 1500 2>&1
    $fatalCount = @($rawLog | Select-String -Pattern 'FATAL EXCEPTION|AndroidRuntime.*FATAL').Count
    $anrCount = @($rawLog | Select-String -Pattern 'ANR in sg\.innercosmos\.app\.dev').Count
    $logSummary = @(
        "package_process_running=true"
        "fatal_exception_count=$fatalCount"
        "anr_count=$anrCount"
        "raw_logcat_persisted=false"
    )
} else {
    $logSummary = @("package_process_running=false", "raw_logcat_persisted=false")
}
$logSummary | Set-Content -Encoding utf8 (Join-Path $EvidenceDirectory "logcat-summary.txt")
$hash = (Get-FileHash -Algorithm SHA256 $apk).Hash.ToLowerInvariant()
@(
    "serial=$Serial"
    "state=$state"
    "package=sg.innercosmos.app.dev"
    "apk_sha256=$hash"
    "verified_at=$((Get-Date).ToString('o'))"
) | Set-Content -Encoding utf8 (Join-Path $EvidenceDirectory "runtime.txt")
Write-Host "ANDROID_RUNTIME_PASS apk_sha256=$hash evidence=$EvidenceDirectory"
