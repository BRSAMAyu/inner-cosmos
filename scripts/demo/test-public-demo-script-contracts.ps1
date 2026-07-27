[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$files = @(
    "public-demo-common.ps1",
    "build-demo-apk.ps1",
    "run-public-demo.ps1",
    "status-public-demo.ps1",
    "stop-public-demo.ps1",
    "watch-public-demo.ps1",
    "verify-public-demo.ps1",
    "test-30-user-burst.ps1"
)

foreach ($name in $files) {
    $tokens = $null
    $errors = $null
    $path = Join-Path $PSScriptRoot $name
    [Management.Automation.Language.Parser]::ParseFile(
        $path, [ref]$tokens, [ref]$errors
    ) | Out-Null
    if ($errors.Count -gt 0) {
        throw "$name has PowerShell parser errors: $($errors[0].Message)"
    }
}

. (Join-Path $PSScriptRoot "public-demo-common.ps1")
if (-not (Test-CleanHttpsOrigin "https://demo.example.com")) {
    throw "Clean HTTPS origin was rejected."
}
foreach ($invalid in @(
    "http://demo.example.com",
    "https://demo.example.com/path",
    "https://user@demo.example.com",
    "not-a-url"
)) {
    if (Test-CleanHttpsOrigin $invalid) { throw "Unsafe origin was accepted: $invalid" }
}

$tempDir = Join-Path ([IO.Path]::GetTempPath()) ("inner-cosmos-demo-contract-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Path $tempDir | Out-Null
try {
    $infoPath = Join-Path $tempDir "demo-info.txt"
    @(
        "origin=https://demo.example.com"
        "tunnel_mode=named"
        "apk_sha256=abc123"
    ) | Set-Content -LiteralPath $infoPath -Encoding utf8
    $info = Read-DemoInfo -Path $infoPath
    if ($info["origin"] -ne "https://demo.example.com" -or $info["tunnel_mode"] -ne "named") {
        throw "Demo metadata parser lost a required field."
    }

    # A live but unrelated PID must never be accepted as cloudflared.
    $pidPath = Join-Path $tempDir "cloudflared.pid"
    $PID | Set-Content -LiteralPath $pidPath -Encoding ascii
    $fakeCloudflared = Join-Path $tempDir "cloudflared.exe"
    $evidence = Get-TunnelProcessEvidence -PidFile $pidPath `
        -ExpectedExecutable $fakeCloudflared -Mode quick
    if ($evidence.Valid) { throw "Unrelated process PID was accepted as the tunnel." }
} finally {
    Remove-Item -LiteralPath $tempDir -Recurse -Force
}

$common = Get-Content -LiteralPath (Join-Path $PSScriptRoot "public-demo-common.ps1") -Raw
$run = Get-Content -LiteralPath (Join-Path $PSScriptRoot "run-public-demo.ps1") -Raw
$status = Get-Content -LiteralPath (Join-Path $PSScriptRoot "status-public-demo.ps1") -Raw
$verify = Get-Content -LiteralPath (Join-Path $PSScriptRoot "verify-public-demo.ps1") -Raw
$burst = Get-Content -LiteralPath (Join-Path $PSScriptRoot "test-30-user-burst.ps1") -Raw
if ($common -match '--token(?:\s|")') {
    throw "Named Tunnel token must not appear in the cloudflared command line."
}
if ($common -notmatch 'CLOUDFLARED_TUNNEL_TOKEN' -or $common -notmatch 'TUNNEL_TOKEN') {
    throw "Named Tunnel must consume an external operator token."
}
if ($run -notmatch 'Remove-Item -LiteralPath \$demoInfoFile' -or
    $run -notmatch 'tunnel_mode=\$TunnelMode' -or
    $run -notmatch '\[switch\]\$StrictVerification') {
    throw "Run script does not preserve stale-state invalidation and optional strict verification."
}
if ($status -notmatch '\$tunnel\.Valid' -or
    $status -notmatch '\$http\.Healthy' -or
    $status -notmatch '\$apkMatches' -or
    $status -notmatch 'STALE_OR_UNAVAILABLE') {
    throw "Status script does not require tunnel, HTTP, and APK evidence."
}
if ($verify -notmatch 'verificationWarnings' -or
    $verify -match 'did not use the required dual-kernel runtime') {
    throw "Default launch verification still hard-blocks an observability runtime label."
}
if ($burst -notmatch '\[int\]\$SandboxEntryUsers = 50' -or
    $burst -notmatch '/api/public/demo/enter/\$key' -or
    $burst -notmatch '/api/public/demo/sandbox' -or
    $burst -notmatch '\[Math\]::Min\(\$UserCount - 1, 29\)') {
    throw "Classroom burst script does not pin 50-session sandbox isolation and bounded discovery."
}

Write-Output "PUBLIC_DEMO_SCRIPT_CONTRACTS_PASS files=$($files.Count)"
