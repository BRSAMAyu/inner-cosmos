[CmdletBinding()]
param(
    [ValidateSet("deepseek", "glm", "gemini")]
    [string]$Provider = "gemini",
    [int]$Port = 8080,
    [int]$MaxBuildWorkers = 1,
    [switch]$SkipVerification,
    [switch]$StrictVerification,
    [switch]$NoWatchdog,
    [switch]$EnableLiveObservability,
    [switch]$SkipApkBuild
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$stateDir = Join-Path $root ".demo-runtime"
$configPath = Join-Path $stateDir "fixed-tunnel.json"
$pidPath = Join-Path $stateDir "cloudflared.pid"
$cloudflared = Join-Path $PSScriptRoot "bin\cloudflared.exe"
. (Join-Path $PSScriptRoot "public-demo-common.ps1")

if (-not (Test-Path -LiteralPath $configPath)) {
    throw "Fixed tunnel configuration is missing. Run set-fixed-public-demo.ps1 first."
}
$config = Get-Content -LiteralPath $configPath -Raw -Encoding utf8 | ConvertFrom-Json
if ($config.version -ne 1 -or
    -not (Test-CleanHttpsOrigin ([string]$config.publicOrigin)) -or
    [string]::IsNullOrWhiteSpace([string]$config.protectedTunnelToken)) {
    throw "Fixed tunnel configuration is invalid. Run set-fixed-public-demo.ps1 again."
}

try {
    $secureToken = ConvertTo-SecureString -String ([string]$config.protectedTunnelToken)
    $credential = [PSCredential]::new("cloudflared", $secureToken)
    $plainToken = $credential.GetNetworkCredential().Password
} catch {
    throw "The Tunnel token cannot be decrypted by this Windows user. Re-run set-fixed-public-demo.ps1 as the account that will start the Demo."
}
if ([string]::IsNullOrWhiteSpace($plainToken)) {
    throw "The stored Tunnel token decrypted to an empty value."
}

$reuse = $false
if (Test-Path -LiteralPath $pidPath) {
    $evidence = Get-TunnelProcessEvidence -PidFile $pidPath `
        -ExpectedExecutable $cloudflared -Mode named -Port $Port
    $reuse = $evidence.Valid
}

$arguments = @{
    Provider = $Provider
    Port = $Port
    MaxBuildWorkers = $MaxBuildWorkers
    TunnelMode = "named"
    PublicOrigin = [string]$config.publicOrigin
}
foreach ($name in @(
    "SkipVerification",
    "StrictVerification",
    "NoWatchdog",
    "EnableLiveObservability",
    "SkipApkBuild"
)) {
    if ($PSBoundParameters.ContainsKey($name)) { $arguments[$name] = $true }
}
if ($reuse) { $arguments["ReuseTunnel"] = $true }

$previousToken = $env:CLOUDFLARED_TUNNEL_TOKEN
try {
    $env:CLOUDFLARED_TUNNEL_TOKEN = $plainToken
    & (Join-Path $PSScriptRoot "run-public-demo.ps1") @arguments
} finally {
    $env:CLOUDFLARED_TUNNEL_TOKEN = $previousToken
    $plainToken = $null
    $credential = $null
    $secureToken = $null
}
