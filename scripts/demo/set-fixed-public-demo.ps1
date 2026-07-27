[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$PublicOrigin,
    [Security.SecureString]$TunnelToken
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$stateDir = Join-Path $root ".demo-runtime"
$configPath = Join-Path $stateDir "fixed-tunnel.json"
. (Join-Path $PSScriptRoot "public-demo-common.ps1")

if (-not (Test-CleanHttpsOrigin $PublicOrigin)) {
    throw "PublicOrigin must be a clean HTTPS origin, for example https://demo.example.com."
}
$originUri = [Uri]$PublicOrigin
if ($originUri.Host.EndsWith(".trycloudflare.com", [StringComparison]::OrdinalIgnoreCase)) {
    throw "A fixed public demo cannot use a temporary trycloudflare.com hostname."
}
if ($null -eq $TunnelToken) {
    $TunnelToken = Read-Host "Cloudflare remotely-managed Tunnel token" -AsSecureString
}
if ($TunnelToken.Length -lt 20) {
    throw "Tunnel token is missing or implausibly short."
}

New-Item -ItemType Directory -Force -Path $stateDir | Out-Null
$config = [ordered]@{
    version = 1
    publicOrigin = $originUri.GetLeftPart([UriPartial]::Authority)
    protectedTunnelToken = ConvertFrom-SecureString -SecureString $TunnelToken
    protection = "Windows-DPAPI-CurrentUser"
}
$config | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding utf8

Write-Output "FIXED_PUBLIC_DEMO_CONFIGURED"
Write-Output "origin=$($config.publicOrigin)"
Write-Output "credential_scope=current-windows-user"
Write-Output "next=powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$PSScriptRoot\start-fixed-public-demo.ps1`""
