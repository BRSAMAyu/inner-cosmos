[CmdletBinding()]
param(
    [ValidateSet("Up", "Down", "Status")]
    [string]$Action = "Up",
    [switch]$Rebuild
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$compose = Join-Path $root "deploy\compose\mobile-local.yml"

if ($Action -eq "Down") {
    & docker compose -f $compose down
    exit $LASTEXITCODE
}
if ($Action -eq "Status") {
    & docker compose -f $compose ps
    exit $LASTEXITCODE
}

$arguments = @("compose", "-f", $compose, "up", "-d", "--wait")
if ($Rebuild) { $arguments += "--build" }
& docker @arguments
if ($LASTEXITCODE -ne 0) { throw "mobile-local compose startup failed" }

$deadline = (Get-Date).AddMinutes(5)
do {
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:8080/actuator/health" -TimeoutSec 3
        $realm = Invoke-RestMethod -Uri "http://127.0.0.1:8081/realms/inner-cosmos/.well-known/openid-configuration" -TimeoutSec 3
        if ($health.status -eq "UP" -and $realm.issuer -eq "http://10.0.2.2:8081/realms/inner-cosmos") { break }
    } catch { Start-Sleep -Seconds 2 }
} while ((Get-Date) -lt $deadline)

if ($health.status -ne "UP") { throw "Spring health did not become UP" }
if ($realm.issuer -ne "http://10.0.2.2:8081/realms/inner-cosmos") { throw "Keycloak issuer is not emulator-safe" }
Write-Host "MOBILE_LOCAL_STACK_PASS API=http://127.0.0.1:8080 IdP=http://127.0.0.1:8081"
