[CmdletBinding()]
param(
    [ValidateSet("Offline", "Status")]
    [string]$Mode = "Offline",

    [string]$Namespace = "inner-cosmos-w3",

    # Status mode deliberately requires an exact context guard. This prevents a presenter
    # from thinking they are inspecting the disposable kind showcase while actually pointed
    # at an Academy or another shared cluster.
    [string]$ExpectedContext = ""
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

function Invoke-Kubectl {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    & kubectl @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl $($Arguments -join ' ') failed."
    }
}

function Test-OptionalResource {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$Label
    )

    & kubectl @Arguments
    if ($LASTEXITCODE -eq 0) {
        Write-Host "$Label=READY"
    } else {
        Write-Host "$Label=NOT_INSTALLED_OR_UNAVAILABLE"
    }
}

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl is required. Install it before running the cloud-native presentation preflight."
}

Push-Location $root
try {
    $renderTargets = @(
        "deploy/k8s/base",
        "deploy/k8s/overlays/academy-eks",
        "deploy/k8s/overlays/kind-full",
        "deploy/k8s/observability",
        "deploy/k8s/extensions/keda",
        "deploy/k8s/extensions/rollouts",
        "deploy/k8s/extensions/kyverno"
    )

    foreach ($target in $renderTargets) {
        & kubectl kustomize $target | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Kustomize render failed: $target"
        }
        Write-Host "render=$target PASS"
    }

    $requiredEvidence = @(
        "evidence/w3/CN-ZERO-LOSS-DRAIN-002/proof.md",
        "evidence/w3/CN-EVENT-DRIVEN-AUTOSCALING-001/summary.md",
        "evidence/w3/CN-OTEL-SEMANTIC-TRACE-001/summary.md",
        "evidence/w3/CN-PROGRESSIVE-DELIVERY-001/run-log.txt",
        "evidence/w3/CN-POLICY-AS-CODE-001/run-log.txt"
    )
    foreach ($path in $requiredEvidence) {
        if (-not (Test-Path -LiteralPath $path)) {
            throw "Required presentation evidence is missing: $path"
        }
        Write-Host "evidence=$path PRESENT"
    }

    Write-Host "CLOUD_NATIVE_OFFLINE_READY"
    if ($Mode -eq "Offline") {
        return
    }

    if ([string]::IsNullOrWhiteSpace($ExpectedContext)) {
        throw "Status mode requires -ExpectedContext. Pass the exact disposable kind/Academy context name after verifying it yourself."
    }

    $currentContext = (& kubectl config current-context).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($currentContext)) {
        throw "Unable to resolve the current kubectl context."
    }
    if ($currentContext -ne $ExpectedContext) {
        throw "Refusing cluster inspection: current context '$currentContext' does not equal expected context '$ExpectedContext'."
    }
    Write-Host "context_guard=PASS"

    Invoke-Kubectl -Arguments @("-n", $Namespace, "get", "deploy,statefulset,pod,pdb,hpa")
    Test-OptionalResource -Arguments @("-n", $Namespace, "get", "scaledobject", "inner-cosmos-worker-outbox") `
        -Label "keda_scaledobject"
    Test-OptionalResource -Arguments @("-n", "observability", "get", "deploy,svc") `
        -Label "prometheus_grafana"
    Test-OptionalResource -Arguments @("-n", $Namespace, "get", "deploy,svc", "-l", "app.kubernetes.io/component=trace-backend") `
        -Label "jaeger"
    Test-OptionalResource -Arguments @("-n", "inner-cosmos-rollouts", "get", "rollout,analysisrun") `
        -Label "argo_rollouts"
    Test-OptionalResource -Arguments @("get", "clusterpolicy") `
        -Label "kyverno_policies"

    Write-Host "CLOUD_NATIVE_CLUSTER_STATUS_COMPLETE"
    Write-Host "Next: follow docs/demo/CLOUD-NATIVE-PRESENTATION-RUNBOOK.md. This script performs no cluster writes."
} finally {
    Pop-Location
}
