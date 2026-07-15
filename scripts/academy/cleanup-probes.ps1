[CmdletBinding(SupportsShouldProcess)]
param([string]$Namespace = $env:K8S_NAMESPACE)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Namespace)) { throw "K8S_NAMESPACE is required." }

if ($PSCmdlet.ShouldProcess("namespace $Namespace", "Delete short-lived runtime and TLS Secrets")) {
    kubectl -n $Namespace delete secret inner-cosmos-runtime inner-cosmos-postgres-server-tls `
        inner-cosmos-postgres-client-ca inner-cosmos-redis-server-tls `
        inner-cosmos-redis-client-ca inner-cosmos-edge-tls --ignore-not-found | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Academy Secret cleanup failed." }
    [pscustomobject]@{ Status = "PASS"; SecretsDeleted = $true; NamespaceDeleted = $false }
}
