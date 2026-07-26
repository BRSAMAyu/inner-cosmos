[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$dashboardDir = Join-Path $root "deploy\k8s\observability\dashboards"

$files = @(Get-ChildItem -LiteralPath $dashboardDir -Filter "*.json" -File | Sort-Object Name)
if ($files.Count -ne 5) {
    throw "Expected exactly 5 presentation dashboards, found $($files.Count)."
}

$uids = @{}
$requiredMetrics = @(
    "kube_pod_status_ready",
    "kube_pod_status_phase",
    "kube_deployment_status_replicas_available",
    "http_server_requests_seconds_count",
    "inner_cosmos_sse_connections_active",
    "inner_cosmos_ai_provider_calls_total",
    "inner_cosmos_ai_tokens_estimated_total",
    "inner_cosmos_outbox_ready",
    "inner_cosmos_outbox_oldest_ready_age_seconds",
    "inner_cosmos_outbox_dead"
)
$allJson = ""
foreach ($file in $files) {
    $raw = Get-Content -Raw -Encoding utf8 -LiteralPath $file.FullName
    $dashboard = $raw | ConvertFrom-Json -ErrorAction Stop
    if ([string]::IsNullOrWhiteSpace($dashboard.uid) -or [string]::IsNullOrWhiteSpace($dashboard.title)) {
        throw "Dashboard $($file.Name) is missing uid/title."
    }
    if ($uids.ContainsKey($dashboard.uid)) {
        throw "Duplicate dashboard uid: $($dashboard.uid)"
    }
    $uids[$dashboard.uid] = $true
    if ($dashboard.refresh -notin @("2s", "5s")) {
        throw "Dashboard $($file.Name) is not presentation-realtime (refresh=$($dashboard.refresh))."
    }
    if ($raw -match '(?i)user[_-]?id|session[_-]?id|message[_-]?content|gen_ai[._]prompt|gen_ai[._]completion') {
        throw "Dashboard $($file.Name) contains a forbidden identity/content field."
    }
    $allJson += $raw
}

foreach ($metric in $requiredMetrics) {
    if ($allJson -notmatch [regex]::Escape($metric)) {
        throw "No dashboard references required metric: $metric"
    }
}

& kubectl kustomize (Join-Path $root "deploy\k8s\observability") | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Observability Kustomize render failed."
}

Write-Host "OBSERVABILITY_DASHBOARDS_VALID files=$($files.Count) uids=$($uids.Count)"
Write-Host "OBSERVABILITY_KUSTOMIZE_RENDER=PASS"
