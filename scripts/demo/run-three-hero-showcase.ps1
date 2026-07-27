[CmdletBinding()]
param(
    [ValidateSet("Preflight", "Continuity", "Keda", "Observability", "All")]
    [string]$Scene = "Preflight",

    [string]$Namespace = "inner-cosmos-w3",

    [string]$ExpectedContext = "kind-kubedeploy",

    [ValidateRange(100, 5000)]
    [int]$KedaEventCount = 3000,

    [switch]$HoldViews
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$runId = "hero-" + (Get-Date -Format "yyyyMMdd-HHmmss")
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) "inner-cosmos-$runId"
$ownedProcesses = [Collections.Generic.List[Diagnostics.Process]]::new()
$demoAccount = $null
$kedaDirty = $false

function Write-Scene {
    param([string]$Title)
    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
}

function Invoke-Kubectl {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    # Windows PowerShell promotes native stderr (including harmless admission warnings)
    # to ErrorRecord when the script preference is Stop. Capture it, then decide solely
    # from kubectl's exit code.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & kubectl @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) {
        throw "kubectl $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }
    return ($output -join [Environment]::NewLine).Trim()
}

function Get-FreeTcpPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try {
        return ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally {
        $listener.Stop()
    }
}

function Start-PortForward {
    param(
        [Parameter(Mandatory = $true)][string]$TargetNamespace,
        [Parameter(Mandatory = $true)][string]$Resource,
        [Parameter(Mandatory = $true)][int]$RemotePort
    )
    $localPort = Get-FreeTcpPort
    $safeName = ($Resource -replace "[^A-Za-z0-9._-]", "-")
    $stdout = Join-Path $tempRoot "$safeName-$localPort.out.log"
    $stderr = Join-Path $tempRoot "$safeName-$localPort.err.log"
    $process = Start-Process -FilePath "kubectl.exe" -ArgumentList @(
        "-n", $TargetNamespace, "port-forward", $Resource,
        "$localPort`:$RemotePort", "--address=127.0.0.1"
    ) -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    $ownedProcesses.Add($process)

    $deadline = (Get-Date).AddSeconds(15)
    do {
        if ($process.HasExited) {
            $detail = if (Test-Path -LiteralPath $stderr) {
                Get-Content -LiteralPath $stderr -Raw
            } else {
                "no stderr"
            }
            throw "Port-forward for $Resource exited early: $detail"
        }
        Start-Sleep -Milliseconds 150
    } until ((Test-NetConnection -ComputerName 127.0.0.1 -Port $localPort -InformationLevel Quiet) -or
        (Get-Date) -ge $deadline)

    if (-not (Test-NetConnection -ComputerName 127.0.0.1 -Port $localPort -InformationLevel Quiet)) {
        throw "Port-forward for $Resource did not become ready on 127.0.0.1:$localPort."
    }
    return $localPort
}

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$BaseUrl,
        [Parameter(Mandatory = $true)][string]$Path,
        [ValidateSet("GET", "POST", "DELETE")]
        [string]$Method = "GET",
        [Parameter(Mandatory = $true)][Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [object]$Body,
        [hashtable]$Headers = @{}
    )
    $params = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        WebSession = $Session
        Headers = $Headers
        UseBasicParsing = $true
        TimeoutSec = 45
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    $response = Invoke-WebRequest @params
    return ($response.Content | ConvertFrom-Json)
}

function Get-CsrfHeader {
    param(
        [string]$BaseUrl,
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session
    )
    $csrf = Invoke-Api -BaseUrl $BaseUrl -Path "/api/auth/csrf" -Session $Session
    if (-not $csrf.success -or [string]::IsNullOrWhiteSpace($csrf.data.token)) {
        throw "Unable to obtain a CSRF token."
    }
    return @{ $csrf.data.headerName = $csrf.data.token }
}

function New-DemoConversation {
    param(
        [string]$BaseUrl,
        [switch]$CreateRichReply
    )
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $username = ("hero" + (Get-Date -Format "MMddHHmmss") + (Get-Random -Minimum 10 -Maximum 99)).ToLowerInvariant()
    $password = "HeroDemo!" + (Get-Random -Minimum 100000 -Maximum 999999)
    $headers = Get-CsrfHeader -BaseUrl $BaseUrl -Session $session
    $registered = Invoke-Api -BaseUrl $BaseUrl -Path "/api/auth/register" -Method POST `
        -Session $session -Headers $headers -Body @{
            username = $username
            password = $password
            nickname = "Hero Scene"
        }
    if (-not $registered.success) {
        throw "Hero account registration failed."
    }
    $headers = Get-CsrfHeader -BaseUrl $BaseUrl -Session $session
    $dialog = Invoke-Api -BaseUrl $BaseUrl -Path "/api/dialog/session/create" -Method POST `
        -Session $session -Headers $headers -Body @{ title = "Three Hero Showcase" }
    if (-not $dialog.success) {
        throw "Hero dialog creation failed."
    }

    $result = [pscustomobject]@{
        Username = $username
        Password = $password
        Session = $session
        SessionId = [long]$dialog.data.id
        BaseUrl = $BaseUrl
    }
    $script:demoAccount = $result

    if ($CreateRichReply) {
        $headers = Get-CsrfHeader -BaseUrl $BaseUrl -Session $session
        $headers["Idempotency-Key"] = "$runId-observability-message"
        $reply = Invoke-Api -BaseUrl $BaseUrl -Path "/api/v1/aurora/message-rich" -Method POST `
            -Session $session -Headers $headers -Body @{
                sessionId = $result.SessionId
                message = "Please turn this demo pressure into one action I can begin in ten minutes."
                mode = "ACTION_SPLIT"
            }
        if (-not $reply.success) {
            throw "Fresh observability conversation failed."
        }
    }
    return $result
}

function Remove-DemoAccount {
    if ($null -eq $script:demoAccount) {
        return
    }
    try {
        # A finish event reads the dialog while the worker builds projections. Do not erase
        # the temporary account until that event has either been published or never existed.
        if ($null -ne $script:demoAccount.SessionId) {
            $deadline = (Get-Date).AddSeconds(45)
            do {
                $finishStatus = Invoke-Psql @"
SELECT COALESCE((
  SELECT status
  FROM tb_outbox_event
  WHERE dedup_key = 'dialog-session:$($script:demoAccount.SessionId):finished:v1'
  ORDER BY created_at DESC
  LIMIT 1
), 'ABSENT');
"@
                if ($finishStatus -in @("PUBLISHED", "ABSENT")) {
                    break
                }
                Start-Sleep -Seconds 1
            } until ((Get-Date) -ge $deadline)
            if ($finishStatus -notin @("PUBLISHED", "ABSENT")) {
                throw "Dialog finish event did not settle before account cleanup: $finishStatus"
            }
        }
        $headers = Get-CsrfHeader -BaseUrl $script:demoAccount.BaseUrl -Session $script:demoAccount.Session
        $null = Invoke-Api -BaseUrl $script:demoAccount.BaseUrl -Path "/api/user/account" -Method DELETE `
            -Session $script:demoAccount.Session -Headers $headers -Body @{
                password = $script:demoAccount.Password
            }
        Write-Host "cleanup_demo_account=PASS"
    } catch {
        Write-Warning "Demo account cleanup needs review: $($_.Exception.Message)"
    } finally {
        $script:demoAccount = $null
    }
}

function Get-PostgresPod {
    $pod = Invoke-Kubectl @(
        "-n", $Namespace, "get", "pod",
        "-l", "app.kubernetes.io/component=postgres",
        "-o", "jsonpath={.items[0].metadata.name}"
    )
    if ([string]::IsNullOrWhiteSpace($pod)) {
        throw "PostgreSQL Pod was not found."
    }
    return $pod
}

function Invoke-Psql {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $pod = Get-PostgresPod
    return Invoke-Kubectl @(
        "-n", $Namespace, "exec", $pod, "-c", "postgres", "--",
        "psql", "-U", "inner_cosmos", "-d", "inner_cosmos",
        "-v", "ON_ERROR_STOP=1", "-Atc", $Sql
    )
}

function Assert-Preflight {
    Write-Scene "PRE-FLIGHT | fail closed before any experiment"
    foreach ($command in @("kubectl", "curl.exe")) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
            throw "$command is required."
        }
    }
    $context = (Invoke-Kubectl @("config", "current-context")).Trim()
    if ($context -ne $ExpectedContext) {
        throw "Refusing showcase writes: current context '$context' is not '$ExpectedContext'."
    }
    Write-Host "context=$context PASS"

    foreach ($deployment in @("inner-cosmos-api", "inner-cosmos-worker", "inner-cosmos-scheduler")) {
        Invoke-Kubectl @("-n", $Namespace, "rollout", "status", "deployment/$deployment", "--timeout=60s") | Out-Null
        Write-Host "$deployment=READY"
    }
    Invoke-Kubectl @("-n", $Namespace, "get", "scaledobject", "inner-cosmos-worker-outbox") | Out-Null
    Invoke-Kubectl @("-n", "observability", "get", "deployment", "prometheus", "grafana") | Out-Null
    Invoke-Kubectl @("-n", $Namespace, "get", "deployment", "inner-cosmos-jaeger", "inner-cosmos-otel-collector") | Out-Null

    $outbox = Invoke-Psql "SELECT count(*) FROM tb_outbox_event WHERE status IN ('PENDING','PROCESSING','RETRY','DEAD');"
    if ([int]$outbox -ne 0) {
        throw "Outbox baseline is not idle ($outbox outstanding events). Rehearse only from a clean baseline."
    }
    Write-Host "outbox_baseline=0 PASS"
    Write-Host "PREFLIGHT_READY" -ForegroundColor Green
}

function Read-SharedText {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        return ""
    }
    $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    try {
        $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true)
        try {
            return $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

function Invoke-ContinuityScene {
    Write-Scene "HERO 1 | Aurora conversation survives Pod replacement"
    $servicePort = Start-PortForward -TargetNamespace $Namespace -Resource "svc/inner-cosmos-api" -RemotePort 8080
    $serviceUrl = "http://127.0.0.1:$servicePort"
    $account = New-DemoConversation -BaseUrl $serviceUrl

    $pod = Invoke-Kubectl @(
        "-n", $Namespace, "get", "pod",
        "-l", "app.kubernetes.io/component=api",
        "-o", "jsonpath={.items[0].metadata.name}"
    )
    $podPort = Start-PortForward -TargetNamespace $Namespace -Resource "pod/$pod" -RemotePort 8080
    $podUrl = "http://127.0.0.1:$podPort"

    $headers = Get-CsrfHeader -BaseUrl $podUrl -Session $account.Session
    $stage = Invoke-Api -BaseUrl $podUrl -Path "/api/v1/aurora/stream-stage" -Method POST `
        -Session $account.Session -Headers $headers -Body @{
            sessionId = $account.SessionId
            message = "For this live demo, acknowledge the pressure and give me two gentle next steps."
            mode = "ACTION_SPLIT"
        }
    if (-not $stage.success -or [string]::IsNullOrWhiteSpace($stage.data.token)) {
        throw "Unable to stage the continuity stream."
    }

    $cookies = $account.Session.Cookies.GetCookies([Uri]$podUrl)
    $cookieHeader = (($cookies | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join "; ")
    $sseOut = Join-Path $tempRoot "continuity-sse.log"
    $sseErr = Join-Path $tempRoot "continuity-sse.err.log"
    $sseUrl = "$podUrl/api/v1/aurora/stream?token=$([Uri]::EscapeDataString($stage.data.token))"
    $curlArgs = "-sS -N --max-time 45 -H `"Cookie: $cookieHeader`" `"$sseUrl`""
    $curl = Start-Process -FilePath "curl.exe" -ArgumentList $curlArgs `
        -RedirectStandardOutput $sseOut -RedirectStandardError $sseErr -WindowStyle Hidden -PassThru
    $ownedProcesses.Add($curl)

    $deadline = (Get-Date).AddSeconds(12)
    do {
        Start-Sleep -Milliseconds 25
        $streamText = Read-SharedText $sseOut
    } until ($streamText -match "event:\s*turn\.started" -or $curl.HasExited -or (Get-Date) -ge $deadline)
    if ($streamText -notmatch "event:\s*turn\.started") {
        throw "The SSE turn did not start in time."
    }

    Write-Host "t+0s  stream=STARTED pod=$pod"
    Invoke-Kubectl @("-n", $Namespace, "delete", "pod", $pod, "--wait=false") | Out-Null
    Write-Host "t+0s  pod_delete=ACCEPTED service_has_survivor=true"

    if (-not $curl.WaitForExit(45000)) {
        $curl.Kill()
        throw "The continuity SSE did not terminate within 45 seconds."
    }
    $streamText = Read-SharedText $sseOut
    if ($streamText -notmatch "event:\s*turn\.completed" -or $streamText -notmatch "event:\s*done") {
        throw "The live turn did not complete after Pod deletion. Inspect $sseOut."
    }

    Invoke-Kubectl @("-n", $Namespace, "rollout", "status", "deployment/inner-cosmos-api", "--timeout=90s") | Out-Null
    $messages = Invoke-Api -BaseUrl $serviceUrl -Path "/api/dialog/session/$($account.SessionId)/messages" `
        -Session $account.Session
    $userCount = @($messages.data | Where-Object speaker -eq "USER").Count
    $auroraCount = @($messages.data | Where-Object speaker -eq "AURORA").Count
    if ($userCount -ne 1 -or $auroraCount -lt 1) {
        throw "History exactness failed: USER=$userCount AURORA=$auroraCount."
    }
    Write-Host "t+done history=RESTORED user_messages=$userCount aurora_messages=$auroraCount"
    Write-Host "HERO_1_PASS | current SSE finished; another Pod served durable history" -ForegroundColor Green

    $headers = Get-CsrfHeader -BaseUrl $serviceUrl -Session $account.Session
    $null = Invoke-Api -BaseUrl $serviceUrl -Path "/api/dialog/session/$($account.SessionId)/finish" `
        -Method POST -Session $account.Session -Headers $headers -Body @{}
    return $account
}

function Get-KedaSnapshot {
    param([string]$Prefix)
    $replicas = Invoke-Kubectl @(
        "-n", $Namespace, "get", "deployment", "inner-cosmos-worker",
        "-o", "jsonpath={.spec.replicas},{.status.availableReplicas}"
    )
    $counts = Invoke-Psql @"
SELECT
  count(*) FILTER (WHERE status IN ('PENDING','PROCESSING','RETRY')),
  count(*) FILTER (WHERE status = 'PUBLISHED')
FROM tb_outbox_event
WHERE dedup_key LIKE '$Prefix-%';
"@
    $parts = $counts -split "\|"
    return [pscustomobject]@{
        Replicas = $replicas
        Outstanding = [int]$parts[0]
        Published = [int]$parts[1]
    }
}

function Restore-KedaScene {
    param([string]$Prefix)
    if (-not $script:kedaDirty) {
        return
    }
    Write-Host "keda_cleanup=START"
    $cleanupSucceeded = $false
    try {
        Invoke-Kubectl @("-n", $Namespace, "annotate", "scaledobject", "inner-cosmos-worker-outbox",
            "autoscaling.keda.sh/paused=true", "--overwrite") | Out-Null
        Invoke-Kubectl @("-n", $Namespace, "scale", "deployment/inner-cosmos-worker", "--replicas=0") | Out-Null
        Invoke-Kubectl @("-n", $Namespace, "wait", "--for=delete", "pod",
            "-l", "app.kubernetes.io/component=worker", "--timeout=90s") | Out-Null
        $null = Invoke-Psql @"
BEGIN;
DELETE FROM tb_inbox_receipt
WHERE event_id IN (
  SELECT event_id FROM tb_outbox_event WHERE dedup_key LIKE '$Prefix-%'
);
DELETE FROM tb_outbox_event WHERE dedup_key LIKE '$Prefix-%';
COMMIT;
"@
        Invoke-Kubectl @("-n", $Namespace, "set", "env", "deployment/inner-cosmos-worker",
            "INNER_COSMOS_EVENTS_OUTBOX_SMOKE_PROBE_ENABLED-",
            "JDBC_OUTBOX_POLL_DELAY_MS-") | Out-Null
        Invoke-Kubectl @("apply", "-k", "deploy/k8s/extensions/keda") | Out-Null
        Invoke-Kubectl @("-n", $Namespace, "annotate", "scaledobject", "inner-cosmos-worker-outbox",
            "autoscaling.keda.sh/paused-") | Out-Null
        Invoke-Kubectl @("-n", $Namespace, "rollout", "status", "deployment/inner-cosmos-worker", "--timeout=90s") | Out-Null
        $remaining = Invoke-Psql "SELECT count(*) FROM tb_outbox_event WHERE dedup_key LIKE '$Prefix-%';"
        if ([int]$remaining -ne 0) {
            throw "Synthetic KEDA rows remain after cleanup: $remaining"
        }
        Write-Host "keda_cleanup=PASS worker_baseline=1 synthetic_rows=0"
        $cleanupSucceeded = $true
    } finally {
        if ($cleanupSucceeded) {
            $script:kedaDirty = $false
        }
    }
}

function Invoke-KedaScene {
    Write-Scene "HERO 2 | KEDA scales on user-visible work pressure"
    $prefix = "$runId-keda"
    $script:kedaDirty = $true
    try {
        Invoke-Kubectl @("-n", $Namespace, "annotate", "scaledobject", "inner-cosmos-worker-outbox",
            "autoscaling.keda.sh/paused=true", "--overwrite") | Out-Null
        Invoke-Kubectl @("-n", $Namespace, "set", "env", "deployment/inner-cosmos-worker",
            "INNER_COSMOS_EVENTS_OUTBOX_SMOKE_PROBE_ENABLED=true",
            "JDBC_OUTBOX_POLL_DELAY_MS=3000") | Out-Null
        Invoke-Kubectl @("-n", $Namespace, "rollout", "status", "deployment/inner-cosmos-worker", "--timeout=90s") | Out-Null
        Invoke-Kubectl @("-n", $Namespace, "scale", "deployment/inner-cosmos-worker", "--replicas=0") | Out-Null

        $null = Invoke-Psql @"
INSERT INTO tb_outbox_event(
  event_id, dedup_key, aggregate_type, aggregate_id, event_type,
  schema_version, payload, status, available_at
)
SELECT
  (
    substr(md5('$prefix-' || g::text),1,8) || '-' ||
    substr(md5('$prefix-' || g::text),9,4) || '-' ||
    substr(md5('$prefix-' || g::text),13,4) || '-' ||
    substr(md5('$prefix-' || g::text),17,4) || '-' ||
    substr(md5('$prefix-' || g::text),21,12)
  )::uuid,
  '$prefix-' || g::text,
  'system',
  '$prefix-' || g::text,
  'system.outbox-smoke-probe.v1',
  1,
  jsonb_build_object('probeId', '$prefix-' || g::text),
  'PENDING',
  CURRENT_TIMESTAMP
FROM generate_series(1, $KedaEventCount) AS g;
"@
        Invoke-Kubectl @("-n", $Namespace, "scale", "deployment/inner-cosmos-worker", "--replicas=1") | Out-Null
        Invoke-Kubectl @("-n", $Namespace, "rollout", "status", "deployment/inner-cosmos-worker", "--timeout=90s") | Out-Null
        Invoke-Kubectl @("-n", $Namespace, "annotate", "scaledobject", "inner-cosmos-worker-outbox",
            "autoscaling.keda.sh/paused-") | Out-Null

        $timer = [Diagnostics.Stopwatch]::StartNew()
        $sawScaleOut = $false
        $deadline = (Get-Date).AddSeconds(160)
        do {
            $snapshot = Get-KedaSnapshot -Prefix $prefix
            Write-Host ("t+{0,3}s worker={1,-5} outstanding={2,-5} published={3}" -f
                [int]$timer.Elapsed.TotalSeconds, $snapshot.Replicas,
                $snapshot.Outstanding, $snapshot.Published)
            $desired = [int](($snapshot.Replicas -split ",")[0])
            if ($desired -ge 3) {
                $sawScaleOut = $true
            }
            if ($snapshot.Outstanding -eq 0) {
                break
            }
            Start-Sleep -Seconds 5
        } until ((Get-Date) -ge $deadline)

        if (-not $sawScaleOut) {
            throw "KEDA did not scale the worker above the baseline."
        }
        if ($snapshot.Outstanding -ne 0 -or $snapshot.Published -ne $KedaEventCount) {
            throw "KEDA workload did not drain exactly: outstanding=$($snapshot.Outstanding), published=$($snapshot.Published)."
        }
        $duplicates = Invoke-Psql @"
SELECT count(*) FROM (
  SELECT event_id, consumer_name, count(*)
  FROM tb_inbox_receipt
  WHERE event_id IN (
    SELECT event_id FROM tb_outbox_event WHERE dedup_key LIKE '$prefix-%'
  )
  GROUP BY event_id, consumer_name
  HAVING count(*) > 1
) d;
"@
        if ([int]$duplicates -ne 0) {
            throw "Duplicate consumer receipts were detected: $duplicates"
        }
        Write-Host "HERO_2_PASS | 1-to-N workers, backlog-to-0, duplicate_receipts=0" -ForegroundColor Green
    } finally {
        Restore-KedaScene -Prefix $prefix
    }
}

function Invoke-ObservabilityScene {
    param([object]$ExistingAccount)
    Write-Scene "HERO 3 | one trace crosses API and Worker"
    $servicePort = if ($null -ne $ExistingAccount) {
        ([Uri]$ExistingAccount.BaseUrl).Port
    } else {
        Start-PortForward -TargetNamespace $Namespace -Resource "svc/inner-cosmos-api" -RemotePort 8080
    }
    $serviceUrl = "http://127.0.0.1:$servicePort"
    $account = $ExistingAccount
    if ($null -eq $account) {
        $account = New-DemoConversation -BaseUrl $serviceUrl -CreateRichReply
        $headers = Get-CsrfHeader -BaseUrl $serviceUrl -Session $account.Session
        $null = Invoke-Api -BaseUrl $serviceUrl -Path "/api/dialog/session/$($account.SessionId)/finish" `
            -Method POST -Session $account.Session -Headers $headers -Body @{}
    }

    $traceparent = ""
    $deadline = (Get-Date).AddSeconds(45)
    do {
        $traceparent = Invoke-Psql @"
SELECT COALESCE(trace_id, '')
FROM tb_outbox_event
WHERE dedup_key = 'dialog-session:$($account.SessionId):finished:v1'
ORDER BY created_at DESC
LIMIT 1;
"@
        if (-not [string]::IsNullOrWhiteSpace($traceparent)) {
            break
        }
        Start-Sleep -Seconds 1
    } until ((Get-Date) -ge $deadline)
    if ($traceparent -notmatch "^00-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$") {
        throw "No valid traceparent was persisted for the finished dialog."
    }
    $traceId = $Matches[1]

    $jaegerPort = Start-PortForward -TargetNamespace $Namespace -Resource "svc/inner-cosmos-jaeger" -RemotePort 16686
    $prometheusPort = Start-PortForward -TargetNamespace "observability" -Resource "svc/prometheus" -RemotePort 9090
    $grafanaPort = Start-PortForward -TargetNamespace "observability" -Resource "svc/grafana" -RemotePort 3000

    $trace = $null
    $traceReady = $false
    $deadline = (Get-Date).AddSeconds(45)
    do {
        try {
            $trace = Invoke-RestMethod -Uri "http://127.0.0.1:$jaegerPort/api/traces/$traceId" -TimeoutSec 10
        } catch {
            $trace = $null
        }
        if ($null -ne $trace -and @($trace.data).Count -gt 0) {
            $candidateTrace = @($trace.data)[0]
            $candidateServices = @(
                $candidateTrace.processes.PSObject.Properties.Value.serviceName |
                    Sort-Object -Unique
            )
            if ($candidateServices -contains "inner-cosmos-api" -and
                $candidateServices -contains "inner-cosmos-worker") {
                $traceReady = $true
                break
            }
        }
        Start-Sleep -Seconds 2
    } until ((Get-Date) -ge $deadline)
    if (-not $traceReady) {
        throw "Jaeger did not expose a complete API-to-worker trace $traceId in time."
    }

    $firstTrace = @($trace.data)[0]
    $services = @($firstTrace.processes.PSObject.Properties.Value.serviceName | Sort-Object -Unique)
    $spans = @($firstTrace.spans)
    $forbidden = @(
        $spans.tags |
            Where-Object { $_.key -match "user\.id|request\.body|response\.body|db\.statement|prompt|completion" }
    ).Count
    if ($services -notcontains "inner-cosmos-api" -or $services -notcontains "inner-cosmos-worker") {
        throw "Trace does not cross both API and worker: $($services -join ', ')."
    }
    if ($forbidden -ne 0) {
        throw "Trace privacy contract failed: $forbidden forbidden tags."
    }

    $metricUrl = "http://127.0.0.1:$prometheusPort/api/v1/query?query=" +
        [Uri]::EscapeDataString('max(kube_deployment_status_replicas_available{namespace="inner-cosmos-w3",deployment="inner-cosmos-api"})')
    $metric = Invoke-RestMethod -Uri $metricUrl -TimeoutSec 10
    $apiReplicas = $metric.data.result[0].value[1]

    Write-Host "trace_id=$traceId"
    Write-Host "services=$($services -join ',') spans=$($spans.Count) forbidden_tags=$forbidden"
    Write-Host "prometheus_api_available_replicas=$apiReplicas"
    Write-Host "Grafana KEDA: http://127.0.0.1:$grafanaPort/d/inner-cosmos-events/work-pressure-contract-c2b7-outbox-and-keda?orgId=1&refresh=5s"
    Write-Host "Grafana recovery: http://127.0.0.1:$grafanaPort/d/inner-cosmos-recovery/continuity-contract-c2b7-pod-recovery-live?orgId=1&refresh=5s"
    Write-Host "Jaeger trace: http://127.0.0.1:$jaegerPort/trace/$traceId"
    Write-Host "HERO_3_PASS | fresh API-to-Worker trace; privacy scan=0" -ForegroundColor Green
}

New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
Push-Location $root
try {
    Assert-Preflight
    switch ($Scene) {
        "Preflight" {
            Write-Host "No cluster writes were performed."
        }
        "Continuity" {
            $null = Invoke-ContinuityScene
        }
        "Keda" {
            Invoke-KedaScene
        }
        "Observability" {
            Invoke-ObservabilityScene
        }
        "All" {
            $continuityAccount = Invoke-ContinuityScene
            Invoke-KedaScene
            Invoke-ObservabilityScene -ExistingAccount $continuityAccount
            Write-Scene "THREE HERO RESULT"
            Write-Host "ALL_THREE_HERO_SCENES_PASS" -ForegroundColor Green
            if ($HoldViews) {
                Write-Host "Grafana and Jaeger forwards are being held for the presenter."
                $null = Read-Host "Press Enter after the audience has seen the dashboards"
            }
        }
    }
} finally {
    if ($kedaDirty) {
        try {
            Restore-KedaScene -Prefix "$runId-keda"
        } catch {
            Write-Warning "Emergency KEDA cleanup failed: $($_.Exception.Message)"
        }
    }
    Remove-DemoAccount
    foreach ($process in $ownedProcesses) {
        try {
            if (-not $process.HasExited) {
                $process.Kill()
            }
        } catch {
            # Best effort: every process is owned by this script and only forwards localhost ports.
        }
    }
    Pop-Location
    Write-Host "temporary_artifacts=$tempRoot"
}
