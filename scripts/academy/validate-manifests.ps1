[CmdletBinding()]
param([string]$Overlay = "deploy/k8s/overlays/academy-eks")

$ErrorActionPreference = "Stop"
$renderedLines = @(& kubectl kustomize $Overlay 2>$null)
if ($LASTEXITCODE -ne 0) { throw "Kustomize render failed." }
$rendered = $renderedLines -join "`n"

$renderedLines | & kubectl apply --dry-run=client --validate=false -f - -o name | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Kubernetes client-side schema dry-run failed." }

$required = @(
    'kind: Deployment', 'kind: StatefulSet', 'kind: Job', 'kind: PersistentVolume',
    'kind: PersistentVolumeClaim', 'kind: PodDisruptionBudget',
    'kind: HorizontalPodAutoscaler', 'kind: NetworkPolicy',
    'kind: Gateway', 'kind: HTTPRoute', 'startupProbe:', 'readinessProbe:',
    'livenessProbe:', 'preStop:', 'topologySpreadConstraints:',
    'runAsNonRoot: true', 'automountServiceAccountToken: false',
    'key: inner-cosmos.academy/storage', 'hostPath:',
    'inner-cosmos/runtime-role: api', 'inner-cosmos/runtime-role: worker',
    'inner-cosmos/runtime-role: scheduler', 'inner-cosmos/runtime-role: migration'
)
$missing = @($required | Where-Object { $rendered -notmatch [Regex]::Escape($_) })
if ($missing.Count -gt 0) { throw "Academy manifest contract is missing $($missing.Count) required controls." }

$forbidden = @(
    'AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_SESSION_TOKEN',
    'amazonaws.com/role-arn', 'ebs.csi.aws.com', 'kind: StorageClass',
    'event-dispatcher: sqs', 'sqs.amazonaws.com'
)
$findings = @($forbidden | Where-Object { $rendered -match [Regex]::Escape($_) })
if ($rendered -match '(?m)^kind: Secret\s*$') { $findings += 'secret-resource' }
if ($findings.Count -gt 0) { throw "Academy manifest contains $($findings.Count) forbidden dependency or credential pattern(s)." }

$unpinnedInfrastructureImages = @(
    $renderedLines |
        Where-Object { $_ -match '^\s+image:\s+' -and $_ -notmatch 'inner-cosmos:academy-placeholder' } |
        Where-Object { $_ -notmatch '@sha256:[a-f0-9]{64}\s*$' }
)
if ($unpinnedInfrastructureImages.Count -gt 0) { throw "Academy infrastructure images must be digest pinned." }

[pscustomobject]@{
    Status = "PASS"
    Overlay = $Overlay
    Resources = @($renderedLines | Where-Object { $_ -eq '---' }).Count + 1
    ForbiddenFindings = 0
    MissingControls = 0
    SecretValuesInGit = $false
    SqsRuntimeDependency = $false
    DynamicStorageDependency = $false
}
