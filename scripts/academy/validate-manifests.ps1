[CmdletBinding()]
param(
    [string]$Overlay = "deploy/k8s/overlays/academy-eks",
    [switch]$ClusterSchemaDryRun
)

$ErrorActionPreference = "Stop"
& (Join-Path $PSScriptRoot "validate-schema-version.ps1") | Out-Null

$renderedLines = @(& kubectl kustomize $Overlay 2>$null)
if ($LASTEXITCODE -ne 0) { throw "Kustomize render failed." }
$rendered = $renderedLines -join "`n"

# `kubectl apply --dry-run=client` still performs API discovery for resource
# mapping, including when validation is disabled. Keep the default contract
# genuinely offline so a stale Academy kubeconfig cannot break manifest
# validation; opt into the discovery-backed check only in a live session.
if ($ClusterSchemaDryRun) {
    $renderedLines | & kubectl apply --dry-run=client --validate=false -f - -o name | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Kubernetes cluster-schema dry-run failed." }
}

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

# The source-level validator catches migration/config drift. These rendered checks ensure an
# overlay cannot accidentally delete or replace one role's gate after the source files pass.
$schemaGateCount = [regex]::Matches($rendered, '(?m)^\s*name:\s*wait-for-schema-version\s*$').Count
$schemaVersionRefCount = [regex]::Matches(
    $rendered, '(?m)^\s*key:\s*INNER_COSMOS_EXPECTED_SCHEMA_VERSION\s*$').Count
$schemaFailedClosedCount = [regex]::Matches(
    $rendered, 'flyway_schema_history\s+WHERE\s+NOT success').Count
if ($schemaGateCount -ne 3 -or $schemaVersionRefCount -ne 3 -or $schemaFailedClosedCount -ne 3) {
    throw "Academy render must retain one fail-closed authoritative schema gate for api, worker, and scheduler."
}

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
    SchemaVersionGates = $schemaGateCount
    OfflineStructuralValidation = $true
    ClusterSchemaDryRun = [bool]$ClusterSchemaDryRun
    SecretValuesInGit = $false
    SqsRuntimeDependency = $false
    DynamicStorageDependency = $false
}
