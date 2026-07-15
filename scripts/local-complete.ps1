[CmdletBinding()]
param([ValidateSet("Up", "Down", "Config")][string]$Action = "Up")

$ErrorActionPreference = "Stop"
$compose = Join-Path $PSScriptRoot "../deploy/compose/local-complete.yml"

function New-RandomSecret {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

if (-not $env:SPRING_DATASOURCE_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD = (New-RandomSecret) }
if (-not $env:REDIS_PASSWORD) { $env:REDIS_PASSWORD = (New-RandomSecret) }

$external = @("LLM_PROVIDER", "LLM_API_KEY", "OIDC_ISSUER_URI", "OIDC_JWK_SET_URI", "OIDC_AUDIENCE",
    "OIDC_AUTHORIZATION_URI", "OIDC_TOKEN_URI", "OIDC_MOBILE_CLIENT_ID", "OIDC_MOBILE_REDIRECT_URI")
if ($Action -eq "Down") {
    foreach ($name in $external) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
            [Environment]::SetEnvironmentVariable($name, (New-RandomSecret), "Process")
        }
    }
} else {
    foreach ($name in $external) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
            throw "$name is an external human-managed prerequisite for local-complete."
        }
    }
}

switch ($Action) {
    "Config" { docker compose -f $compose config --quiet }
    "Up" {
        docker compose -f $compose run --rm tls-init | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "TLS initialization failed." }
        docker compose -f $compose up -d --build
    }
    "Down" { docker compose -f $compose down }
}
