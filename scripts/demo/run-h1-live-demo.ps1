[CmdletBinding()]
param(
    [ValidateSet("HardCrash", "GracefulDelete")]
    [string]$FaultMode = "HardCrash",
    [string]$ExpectedContext = "kind-kubedeploy",
    [string]$Namespace = "inner-cosmos-w3",
    [int]$ArmTimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"

function Invoke-Kube {
    param([string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & kubectl @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($code -ne 0) {
        throw "kubectl $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }
    return ($output -join [Environment]::NewLine).Trim()
}

function Invoke-Psql {
    param([string]$Sql)
    return Invoke-Kube @(
        "-n", $Namespace, "exec", "inner-cosmos-postgres-0", "-c", "postgres", "--",
        "psql", "-U", "inner_cosmos", "-d", "inner_cosmos", "-Atc", $Sql
    )
}

$context = (Invoke-Kube @("config", "current-context")).Trim()
if ($context -ne $ExpectedContext) {
    throw "Current context '$context' is not '$ExpectedContext'."
}
if (-not (Test-NetConnection -ComputerName 127.0.0.1 -Port 8081 -InformationLevel Quiet) -or
    -not (Test-NetConnection -ComputerName 127.0.0.1 -Port 3000 -InformationLevel Quiet)) {
    throw "H1 client/Grafana forwards are missing. Run start-live-showcase.ps1 first."
}

Invoke-Kube @("-n", $Namespace, "scale", "deployment/inner-cosmos-api", "--replicas=2") | Out-Null
Invoke-Kube @("-n", $Namespace, "rollout", "status", "deployment/inner-cosmos-api", "--timeout=90s") | Out-Null
$baselineTurnId = Invoke-Psql "SELECT COALESCE(MAX(id), 0) FROM tb_conversation_turn;"

Write-Host ""
Write-Host "H1 LIVE DEMO ARMED" -ForegroundColor Cyan
Write-Host "Client:    http://127.0.0.1:8081/app/aurora/"
Write-Host "Dashboard: http://127.0.0.1:3000/d/inner-cosmos-recovery/continuity-contract-c2b7-pod-recovery-live?orgId=1&refresh=2s"
Write-Host ""
Write-Host "ACTION FOR PRESENTER: send one Aurora message now."
Write-Host "baseline_turn_id=$baselineTurnId api=2/2"
Write-Host "Waiting for the next durable generation lease to identify the exact serving Pod..."

$deadline = (Get-Date).AddSeconds($ArmTimeoutSeconds)
$lease = ""
do {
    $lease = Invoke-Psql @"
SELECT COALESCE(lease_owner, '')
FROM tb_conversation_turn
WHERE lease_owner IS NOT NULL
  AND id > $baselineTurnId
  AND status NOT IN ('COMPLETED','INTERRUPTED','CANCELLED','FAILED')
ORDER BY id DESC
LIMIT 1;
"@
    if (-not [string]::IsNullOrWhiteSpace($lease)) { break }
    Start-Sleep -Milliseconds 150
} until ((Get-Date) -ge $deadline)

if ([string]::IsNullOrWhiteSpace($lease) -or $lease -notmatch "^([^:]+):") {
    throw "No active Aurora generation lease appeared within $ArmTimeoutSeconds seconds."
}
$targetPod = $Matches[1]
$turn = Invoke-Psql @"
SELECT id || '|' || status
FROM tb_conversation_turn
WHERE lease_owner = '$lease'
ORDER BY id DESC
LIMIT 1;
"@
$turnParts = $turn -split "\|"
$turnId = [long]$turnParts[0]
$restartBefore = [int](Invoke-Kube @(
    "-n", $Namespace, "get", "pod", $targetPod,
    "-o", "jsonpath={.status.containerStatuses[0].restartCount}"
))

Write-Host ""
Write-Host "TARGET_LOCKED turn=$turnId pod=$targetPod lease_owner=$lease"
if ($FaultMode -eq "HardCrash") {
    Write-Host "COMMAND: kubectl -n $Namespace exec $targetPod -c app -- kill -9 1"
    Write-Host "EXPECTED: client detects loss -> durable replay -> recovered; another API remains Ready."
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & kubectl -n $Namespace exec $targetPod -c app -- kill -9 1 2>&1 | Out-Null
    } finally {
        $ErrorActionPreference = $previous
    }
    Write-Host "FAULT_INJECTED=container_pid1_sigkill"
} else {
    Write-Host "COMMAND: kubectl -n $Namespace delete pod $targetPod --wait=false"
    Write-Host "EXPECTED: graceful drain normally finishes without showing a client error."
    Invoke-Kube @("-n", $Namespace, "delete", "pod", $targetPod, "--wait=false") | Out-Null
    Write-Host "FAULT_INJECTED=graceful_pod_delete"
}

$deadline = (Get-Date).AddSeconds(90)
$turnStatus = ""
do {
    $ready = Invoke-Kube @(
        "-n", $Namespace, "get", "deployment", "inner-cosmos-api",
        "-o", "jsonpath={.status.availableReplicas}/{.spec.replicas}"
    )
    $turnStatus = Invoke-Psql "SELECT status FROM tb_conversation_turn WHERE id = $turnId;"
    $restartNow = if ($FaultMode -eq "HardCrash") {
        [int](Invoke-Kube @(
            "-n", $Namespace, "get", "pod", $targetPod,
            "-o", "jsonpath={.status.containerStatuses[0].restartCount}"
        ))
    } else { $restartBefore }
    Write-Host "api=$ready target_restarts=$restartNow turn=$turnStatus"
    if ($turnStatus -eq "COMPLETED" -and $ready -eq "2/2" -and
        ($FaultMode -ne "HardCrash" -or $restartNow -gt $restartBefore)) {
        break
    }
    Start-Sleep -Seconds 2
} until ((Get-Date) -ge $deadline)

if ($turnStatus -ne "COMPLETED") {
    throw "Turn $turnId did not complete after $FaultMode; final status=$turnStatus."
}
Invoke-Kube @("-n", $Namespace, "rollout", "status", "deployment/inner-cosmos-api", "--timeout=90s") | Out-Null
Write-Host ""
Write-Host "H1_LIVE_PASS turn=$turnId final_status=COMPLETED api=2/2" -ForegroundColor Green
Write-Host "Explain: Redis carries live cursor/session state; PostgreSQL is transcript truth; fencing prevents duplicate commits."
