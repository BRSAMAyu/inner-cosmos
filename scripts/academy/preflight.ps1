[CmdletBinding()]
param(
    [ValidateSet("Offline", "Live")]
    [string]$Mode = "Offline",
    [string]$Region = $env:AWS_REGION,
    [string]$ClusterName = $env:EKS_CLUSTER_NAME,
    [string]$LogicalClusterAlias = $(if ($env:EKS_LOGICAL_ALIAS) { $env:EKS_LOGICAL_ALIAS } else { "academy-lab" }),
    [string]$EvidencePath = "",
    [string]$ProbeImage = "public.ecr.aws/aws-cli/aws-cli:2.35.20@sha256:386057cca84505a3420558c22c2598f254de4684492d705a3d64d86208fdbfc5"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Invoke-Captured {
    param([Parameter(Mandatory)][string]$Command, [string[]]$Arguments = @())
    $saved = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = @(& $Command @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
        return [pscustomobject]@{ ExitCode = $exitCode; Output = $output }
    } finally {
        $ErrorActionPreference = $saved
    }
}

function Get-NonBlankCount {
    param([object[]]$Lines)
    return @($Lines | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ }).Count
}

function Set-Result {
    param([Parameter(Mandatory)][string]$Name,
          [Parameter(Mandatory)][string]$Status,
          [hashtable]$Detail = @{})
    $entry = [ordered]@{ status = $Status }
    foreach ($key in $Detail.Keys) { $entry[$key] = $Detail[$key] }
    $script:results[$Name] = $entry
}

function Wait-ProbePod {
    param([Parameter(Mandatory)][string]$Namespace,
          [Parameter(Mandatory)][string]$Name,
          [int]$TimeoutSeconds = 90)
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        $phaseResult = Invoke-Captured kubectl @("-n", $Namespace, "get", "pod", $Name,
            "-o", "jsonpath={.status.phase}")
        if ($phaseResult.ExitCode -eq 0) {
            $phase = ($phaseResult.Output -join "").Trim()
            if ($phase -in @("Succeeded", "Failed")) { return $phase }
        }
        Start-Sleep -Seconds 2
    }
    return "Timeout"
}

$timestamp = [DateTimeOffset]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
$suffix = [Guid]::NewGuid().ToString("N").Substring(0, 10)
$probeNamespace = "inner-cosmos-probe-$suffix"
$queueName = "inner-cosmos-capability-$suffix"
$results = [ordered]@{}
$cleanup = [ordered]@{ namespace = "NOT_CREATED"; sqs_queue = "NOT_CREATED"; status = "PASS" }
$criticalFailure = $false
$queueUrl = $null
$namespaceCreated = $false

$git = Invoke-Captured git @("rev-parse", "HEAD")
$gitSha = if ($git.ExitCode -eq 0) { ($git.Output -join "").Trim() } else { "UNKNOWN" }

foreach ($tool in @("aws", "kubectl", "git")) {
    if (Get-Command $tool -ErrorAction SilentlyContinue) {
        Set-Result "tool_$tool" "PASS"
    } else {
        Set-Result "tool_$tool" "FAIL"
        $criticalFailure = $true
    }
}

$render = Invoke-Captured kubectl @("kustomize", "deploy/k8s/overlays/academy-eks")
if ($render.ExitCode -eq 0) {
    $rendered = $render.Output -join "`n"
    $forbidden = @(
        "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN",
        "kind: StorageClass", "ebs.csi.aws.com", "amazonaws.com/role-arn",
        "sqs", "kind: Secret`nstringData:", "kind: Secret`ndata:"
    )
    $matches = @($forbidden | Where-Object { $rendered -match [Regex]::Escape($_) })
    if ($matches.Count -eq 0) {
        Set-Result "academy_kustomize" "PASS" @{ resources = Get-NonBlankCount ($render.Output | Where-Object { $_ -eq "---" }) }
    } else {
        Set-Result "academy_kustomize" "FAIL_FORBIDDEN_DEPENDENCY" @{ finding_count = $matches.Count }
        $criticalFailure = $true
    }
} else {
    Set-Result "academy_kustomize" "FAIL"
    $criticalFailure = $true
}

if ($Mode -eq "Live" -and -not $criticalFailure) {
    if ($Region -ne "us-east-1" -or [string]::IsNullOrWhiteSpace($ClusterName)) {
        Set-Result "academy_environment" "FAIL_CLOSED" @{ expected_region = "us-east-1"; cluster_name_supplied = -not [string]::IsNullOrWhiteSpace($ClusterName) }
        $criticalFailure = $true
    } else {
        Set-Result "academy_environment" "PASS" @{ region = "us-east-1"; logical_cluster_alias = $LogicalClusterAlias }
    }
}

try {
    if ($Mode -eq "Live" -and -not $criticalFailure) {
        $identity = Invoke-Captured aws @("sts", "get-caller-identity", "--region", $Region, "--output", "json")
        if ($identity.ExitCode -eq 0) { Set-Result "human_lab_role_identity" "PASS_CURRENT_SESSION" }
        else { Set-Result "human_lab_role_identity" "FAIL"; $criticalFailure = $true }

        $cluster = Invoke-Captured aws @("eks", "describe-cluster", "--name", $ClusterName, "--region", $Region, "--output", "json")
        if ($cluster.ExitCode -eq 0) { Set-Result "eks_api" "PASS_CURRENT_SESSION" }
        else { Set-Result "eks_api" "FAIL"; $criticalFailure = $true }

        if (-not $criticalFailure) {
            $kubeconfig = Invoke-Captured aws @("eks", "update-kubeconfig", "--name", $ClusterName, "--region", $Region)
            if ($kubeconfig.ExitCode -ne 0) { Set-Result "kubeconfig" "FAIL"; $criticalFailure = $true }
            else { Set-Result "kubeconfig" "PASS_LOCAL_ONLY" }
        }

        if (-not $criticalFailure) {
            $ready = Invoke-Captured kubectl @("get", "--raw=/readyz")
            if ($ready.ExitCode -eq 0) { Set-Result "kubernetes_api" "PASS" }
            else { Set-Result "kubernetes_api" "FAIL"; $criticalFailure = $true }
        }

        if (-not $criticalFailure) {
            $nodes = Invoke-Captured kubectl @("get", "nodes", "-o", "name")
            $nodeCount = if ($nodes.ExitCode -eq 0) { Get-NonBlankCount $nodes.Output } else { 0 }
            if ($nodeCount -gt 0) { Set-Result "kubernetes_nodes" "PASS" @{ count = $nodeCount } }
            else { Set-Result "kubernetes_nodes" "FAIL" @{ count = 0 }; $criticalFailure = $true }

            $namespaceCreate = Invoke-Captured kubectl @("create", "namespace", $probeNamespace)
            if ($namespaceCreate.ExitCode -eq 0) {
                $namespaceCreated = $true
                $cleanup.namespace = "PENDING"
                Set-Result "probe_namespace" "PASS_CREATED"
            } else {
                Set-Result "probe_namespace" "FAIL"
                $criticalFailure = $true
            }
        }

        if ($namespaceCreated) {
            $permissions = [ordered]@{
                deployment = "deployments.apps"; statefulset = "statefulsets.apps";
                persistentvolume = "persistentvolumes"; pdb = "poddisruptionbudgets.policy";
                hpa = "horizontalpodautoscalers.autoscaling"; networkpolicy = "networkpolicies.networking.k8s.io";
                httproute = "httproutes.gateway.networking.k8s.io"
            }
            $denied = 0
            foreach ($key in $permissions.Keys) {
                $can = Invoke-Captured kubectl @("auth", "can-i", "create", $permissions[$key], "-n", $probeNamespace)
                $allowed = $can.ExitCode -eq 0 -and (($can.Output -join "`n") -match '(?m)^yes\s*$')
                if (-not $allowed) { $denied++ }
            }
            Set-Result "kubernetes_workload_rbac" $(if ($denied -eq 0) { "PASS" } else { "PARTIAL" }) @{ checked = $permissions.Count; denied = $denied }
            if ($denied -gt 0) { $criticalFailure = $true }

            $gateway = Invoke-Captured kubectl @("get", "gatewayclass", "-o", "name")
            $gatewayCount = if ($gateway.ExitCode -eq 0) { Get-NonBlankCount $gateway.Output } else { 0 }
            Set-Result "gateway_api" $(if ($gatewayCount -gt 0) { "PASS" } else { "ABSENT_OR_DENIED" }) @{ count = $gatewayCount }
            if ($gatewayCount -eq 0) { $criticalFailure = $true }

            $storage = Invoke-Captured kubectl @("get", "storageclass", "-o", "name")
            $csi = Invoke-Captured kubectl @("get", "csidriver", "ebs.csi.aws.com", "-o", "name")
            $ebsCsiPresent = $csi.ExitCode -eq 0
            Set-Result "ebs_csi" $(if ($ebsCsiPresent) { "PRESENT_REVIEW_REQUIRED" } else { "ABSENT_EXPECTED" })

            $storageCount = if ($storage.ExitCode -eq 0) { Get-NonBlankCount $storage.Output } else { 0 }
            if ($storage.ExitCode -ne 0) {
                Set-Result "storage_class" "DENIED"
                $criticalFailure = $true
            } elseif ($storageCount -eq 0) {
                Set-Result "storage_class" "ABSENT_STATIC_PV_REQUIRED" @{ count = 0 }
            } else {
                $storageJson = Invoke-Captured kubectl @("get", "storageclass", "-o", "jsonpath={range .items[*]}{.provisioner}{'\n'}{end}")
                $provisioners = @($storageJson.Output | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ })
                $legacyOnly = $storageJson.ExitCode -eq 0 -and $provisioners.Count -eq $storageCount `
                    -and @($provisioners | Where-Object { $_ -ne "kubernetes.io/aws-ebs" }).Count -eq 0
                if ($legacyOnly -and -not $ebsCsiPresent) {
                    Set-Result "storage_class" "PRESENT_LEGACY_NO_CSI_STATIC_PV_REQUIRED" @{ count = $storageCount; provisioner_class = "legacy_aws_ebs" }
                } else {
                    Set-Result "storage_class" "PRESENT_UNVERIFIED_FAIL_CLOSED" @{ count = $storageCount; provisioner_class = "unverified" }
                    $criticalFailure = $true
                }
            }
            if ($ebsCsiPresent) { $criticalFailure = $true }

            $metrics = Invoke-Captured kubectl @("get", "--raw=/apis/metrics.k8s.io/v1beta1/nodes")
            Set-Result "metrics_api" $(if ($metrics.ExitCode -eq 0) { "PASS" } else { "ABSENT_OR_DENIED" })
            if ($metrics.ExitCode -ne 0) { $criticalFailure = $true }

            $queueCreate = Invoke-Captured aws @("sqs", "create-queue", "--queue-name", $queueName, "--region", $Region, "--output", "json")
            if ($queueCreate.ExitCode -eq 0) {
                try { $queueUrl = (($queueCreate.Output -join "`n") | ConvertFrom-Json).QueueUrl } catch { $queueUrl = $null }
                if ($queueUrl) {
                    $cleanup.sqs_queue = "PENDING"
                    $send = Invoke-Captured aws @("sqs", "send-message", "--queue-url", $queueUrl, "--message-body", "capability-probe", "--region", $Region)
                    $receive = Invoke-Captured aws @("sqs", "receive-message", "--queue-url", $queueUrl, "--max-number-of-messages", "1", "--wait-time-seconds", "1", "--region", $Region)
                    Set-Result "sqs_human_lab_role" $(if ($send.ExitCode -eq 0 -and $receive.ExitCode -eq 0) { "PASS_CREATE_SEND_RECEIVE" } else { "PARTIAL" })
                } else { Set-Result "sqs_human_lab_role" "FAIL_SANITIZED_PARSE" }
            } else { Set-Result "sqs_human_lab_role" "DENIED_OR_UNAVAILABLE" }

            foreach ($probe in @(
                @{ name = "workload-sts-$suffix"; command = "aws sts get-caller-identity --region us-east-1 >/dev/null 2>&1"; result = "sqs_workload_identity_sts" },
                @{ name = "workload-sqs-$suffix"; command = "aws sqs list-queues --region us-east-1 --max-results 1 >/dev/null 2>&1"; result = "sqs_workload_identity_data_plane" }
            )) {
                $run = Invoke-Captured kubectl @("-n", $probeNamespace, "run", $probe.name, "--restart=Never",
                    "--image=$ProbeImage", "--command", "--", "sh", "-c", $probe.command)
                if ($run.ExitCode -ne 0) {
                    Set-Result $probe.result "PROBE_CREATE_FAILED"
                    $criticalFailure = $true
                    continue
                }
                $phase = Wait-ProbePod -Namespace $probeNamespace -Name $probe.name
                if ($phase -eq "Failed") { Set-Result $probe.result "NO_CREDENTIALS_OR_DENIED_EXPECTED" }
                elseif ($phase -eq "Succeeded") { Set-Result $probe.result "PASS_UNEXPECTED_REVIEW_REQUIRED"; $criticalFailure = $true }
                else { Set-Result $probe.result "PROBE_TIMEOUT"; $criticalFailure = $true }
            }

            $targetNamespace = $env:K8S_NAMESPACE
            if ($targetNamespace) {
                foreach ($service in @("inner-cosmos-postgres", "inner-cosmos-redis")) {
                    $endpoint = Invoke-Captured kubectl @("-n", $targetNamespace, "get", "endpoints", $service, "-o", "jsonpath={.subsets[*].addresses[*].ip}")
                    $hasEndpoint = $endpoint.ExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace(($endpoint.Output -join "").Trim())
                    Set-Result "${service}_readiness" $(if ($hasEndpoint) { "PASS_ENDPOINT_READY" } else { "NOT_DEPLOYED_OR_NOT_READY" })
                }
            } else {
                Set-Result "deployed_data_services" "NOT_CHECKED_NO_K8S_NAMESPACE"
            }
        }
    }
} finally {
    if ($queueUrl) {
        $deleteQueue = Invoke-Captured aws @("sqs", "delete-queue", "--queue-url", $queueUrl, "--region", $Region)
        $cleanup.sqs_queue = if ($deleteQueue.ExitCode -eq 0) { "PASS" } else { "FAIL" }
        if ($deleteQueue.ExitCode -ne 0) { $cleanup.status = "FAIL"; $criticalFailure = $true }
    }
    if ($namespaceCreated) {
        $deleteNamespace = Invoke-Captured kubectl @("delete", "namespace", $probeNamespace, "--wait=true", "--timeout=90s")
        $cleanup.namespace = if ($deleteNamespace.ExitCode -eq 0) { "PASS" } else { "FAIL" }
        if ($deleteNamespace.ExitCode -ne 0) { $cleanup.status = "FAIL"; $criticalFailure = $true }
    }
}

if (-not $EvidencePath) {
    $safeTimestamp = [DateTimeOffset]::UtcNow.ToString("yyyyMMdd-HHmmss")
    $EvidencePath = Join-Path "target/academy-preflight" "preflight-$safeTimestamp.json"
}
$parent = Split-Path -Parent $EvidencePath
if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }

$document = [ordered]@{
    schema_version = 1
    profile = "academy-eks"
    mode = $Mode
    recorded_at = $timestamp
    git_sha = $gitSha
    region = if ($Mode -eq "Live") { "us-east-1" } else { "NOT_ACCESSED" }
    logical_cluster_alias = if ($Mode -eq "Live") { $LogicalClusterAlias } else { "NOT_ACCESSED" }
    sensitive_identifiers_recorded = $false
    results = $results
    cleanup = $cleanup
    overall = if ($criticalFailure) { "FAIL_CLOSED" } else { "PASS" }
}
$document | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 -Path $EvidencePath

[pscustomobject]@{
    Status = $document.overall
    Mode = $Mode
    Evidence = (Resolve-Path $EvidencePath).Path
    SensitiveIdentifiersRecorded = $false
    Cleanup = $cleanup.status
}

if ($criticalFailure) { exit 1 }
