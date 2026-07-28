[CmdletBinding()]
param(
    [ValidateSet("Preflight", "Continuity", "Keda", "Observability", "All")]
    [string]$Scene = "Preflight",

    [string]$Namespace = "inner-cosmos-w3",

    [string]$ExpectedContext = "kind-kubedeploy",

    [ValidateRange(100, 5000)]
    [int]$KedaEventCount = 1200,

    [switch]$HoldViews
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$runId = "hero-" + (Get-Date -Format "yyyyMMdd-HHmmss")
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) "inner-cosmos-$runId"
$ownedProcesses = [Collections.Generic.List[Diagnostics.Process]]::new()
$demoAccount = $null
$kedaDirty = $false
$kedaScalePassed = $false

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

function Test-LiveShowcaseEndpoint {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$Port
    )
    $path = switch ($Name) {
        "kind-api" { "/actuator/health/readiness" }
        "grafana" { "/api/health" }
        "prometheus" { "/-/ready" }
        "jaeger" { "/api/services" }
        default { "/" }
    }
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$Port$path" `
            -UseBasicParsing -TimeoutSec 3
        return [int]$response.StatusCode -ge 200 -and [int]$response.StatusCode -lt 400
    } catch {
        return $false
    }
}

function Get-LiveShowcasePort {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$ExpectedPort
    )
    $stateFile = Join-Path $root ".demo-runtime\live-showcase.json"
    if (-not (Test-Path -LiteralPath $stateFile)) {
        return $null
    }
    try {
        $state = Get-Content -LiteralPath $stateFile -Raw -Encoding utf8 | ConvertFrom-Json
        if ($state.context -ne $ExpectedContext) {
            return $null
        }
        $forward = @($state.forwards | Where-Object name -eq $Name | Select-Object -First 1)
        if ($forward.Count -ne 1 -or [int]$forward[0].localPort -ne $ExpectedPort) {
            return $null
        }
        $process = Get-Process -Id ([int]$forward[0].pid) -ErrorAction SilentlyContinue
        if ($null -eq $process -or $process.ProcessName -ne "kubectl") {
            return $null
        }
        if (-not (Test-NetConnection -ComputerName 127.0.0.1 -Port $ExpectedPort -InformationLevel Quiet)) {
            return $null
        }
        # A kubectl port-forward can keep its local TCP listener after the selected
        # Pod disappears. TCP-only checks then report a false positive while every
        # HTTP request fails. Probe the actual service before reusing a fixed view.
        if (-not (Test-LiveShowcaseEndpoint -Name $Name -Port $ExpectedPort)) {
            return $null
        }
        return $ExpectedPort
    } catch {
        return $null
    }
}

function Get-OrStartServicePort {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$StablePort,
        [Parameter(Mandatory = $true)][string]$TargetNamespace,
        [Parameter(Mandatory = $true)][string]$Resource,
        [Parameter(Mandatory = $true)][int]$RemotePort
    )
    $livePort = Get-LiveShowcasePort -Name $Name -ExpectedPort $StablePort
    if ($null -ne $livePort) {
        Write-Host "view=$Name port=$livePort source=live-showcase"
        return [int]$livePort
    }
    $temporaryPort = Start-PortForward -TargetNamespace $TargetNamespace -Resource $Resource -RemotePort $RemotePort
    Write-Host "view=$Name port=$temporaryPort source=temporary"
    return [int]$temporaryPort
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

function New-W3cTraceContext {
    $traceBytes = New-Object byte[] 16
    $spanBytes = New-Object byte[] 8
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($traceBytes)
        $rng.GetBytes($spanBytes)
    } finally {
        $rng.Dispose()
    }
    $traceId = ([BitConverter]::ToString($traceBytes) -replace "-", "").ToLowerInvariant()
    $spanId = ([BitConverter]::ToString($spanBytes) -replace "-", "").ToLowerInvariant()
    return [pscustomobject]@{
        TraceId = $traceId
        Traceparent = "00-$traceId-$spanId-01"
    }
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
        $traceContext = New-W3cTraceContext
        $headers["traceparent"] = $traceContext.Traceparent
        $clientTimer = [Diagnostics.Stopwatch]::StartNew()
        $reply = Invoke-Api -BaseUrl $BaseUrl -Path "/api/v1/aurora/message-rich" -Method POST `
            -Session $session -Headers $headers -Body @{
                sessionId = $result.SessionId
                message = "Please turn this demo pressure into one action I can begin in ten minutes."
                mode = "ACTION_SPLIT"
            }
        $clientTimer.Stop()
        if (-not $reply.success) {
            throw "Fresh observability conversation failed."
        }
        $result | Add-Member -NotePropertyName AuroraTraceId -NotePropertyValue $traceContext.TraceId
        $result | Add-Member -NotePropertyName AuroraClientLatencyMs -NotePropertyValue ([long]$clientTimer.Elapsed.TotalMilliseconds)
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
    param([switch]$AllowShowcaseBacklog)
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
    foreach ($deployment in @("prometheus", "grafana")) {
        Invoke-Kubectl @("-n", "observability", "rollout", "status", "deployment/$deployment", "--timeout=60s") | Out-Null
        Write-Host "$deployment=READY"
    }
    foreach ($deployment in @("inner-cosmos-jaeger", "inner-cosmos-otel-collector")) {
        Invoke-Kubectl @("-n", $Namespace, "rollout", "status", "deployment/$deployment", "--timeout=60s") | Out-Null
        Write-Host "$deployment=READY"
    }
    $scaledObjectReady = Invoke-Kubectl @("-n", $Namespace, "get", "scaledobject",
        "inner-cosmos-worker-outbox", "-o", "jsonpath={.status.conditions[?(@.type=='Ready')].status}")
    if ($scaledObjectReady -ne "True") {
        throw "KEDA ScaledObject is not Ready."
    }
    Write-Host "inner-cosmos-worker-outbox=READY"

    $outbox = Invoke-Psql "SELECT count(*) FROM tb_outbox_event WHERE status IN ('PENDING','PROCESSING','RETRY','DEAD');"
    $nonShowcaseOutbox = Invoke-Psql @"
SELECT count(*)
FROM tb_outbox_event
WHERE status IN ('PENDING','PROCESSING','RETRY','DEAD')
  AND dedup_key NOT LIKE 'hero-%-keda-%';
"@
    if ([int]$nonShowcaseOutbox -ne 0 -or ((-not $AllowShowcaseBacklog) -and [int]$outbox -ne 0)) {
        throw "Outbox baseline is not idle ($outbox outstanding events). Rehearse only from a clean baseline."
    }
    if ([int]$outbox -eq 0) {
        Write-Host "outbox_baseline=0 PASS"
    } else {
        Write-Host "outbox_showcase_backlog=$outbox ALLOWED_FOR_H3"
    }
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
    $traceContext = New-W3cTraceContext
    $curlArgs = "-sS -N --max-time 45 -H `"Cookie: $cookieHeader`" -H `"traceparent: $($traceContext.Traceparent)`" `"$sseUrl`""
    $clientTimer = [Diagnostics.Stopwatch]::StartNew()
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
    $clientTimer.Stop()
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
    $account | Add-Member -NotePropertyName AuroraTraceId -NotePropertyValue $traceContext.TraceId
    $account | Add-Member -NotePropertyName AuroraClientLatencyMs -NotePropertyValue ([long]$clientTimer.Elapsed.TotalMilliseconds)

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

function Assert-KedaDrainInvariant {
    param(
        [Parameter(Mandatory = $true)][string]$Prefix,
        [ValidateRange(30, 180)][int]$TimeoutSeconds = 120
    )
    if (-not $script:kedaScalePassed) {
        return
    }
    Write-Host "keda_drain_validation=START"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $snapshot = Get-KedaSnapshot -Prefix $Prefix
        if ($snapshot.Outstanding -eq 0) {
            break
        }
        Start-Sleep -Seconds 2
    } until ((Get-Date) -ge $deadline)
    if ($snapshot.Outstanding -ne 0 -or $snapshot.Published -ne $KedaEventCount) {
        throw "KEDA drain invariant failed: outstanding=$($snapshot.Outstanding) published=$($snapshot.Published) expected=$KedaEventCount."
    }
    $receiptStats = Invoke-Psql @"
SELECT
  count(*),
  count(DISTINCT receipt.event_id)
FROM tb_inbox_receipt receipt
JOIN tb_outbox_event event ON event.event_id = receipt.event_id
WHERE event.dedup_key LIKE '$Prefix-%';
"@
    $receiptParts = $receiptStats -split "\|"
    $receiptCount = [int]$receiptParts[0]
    $distinctReceiptCount = [int]$receiptParts[1]
    $duplicateReceipts = $receiptCount - $distinctReceiptCount
    if ($receiptCount -ne $KedaEventCount -or $duplicateReceipts -ne 0) {
        throw "KEDA inbox invariant failed: receipts=$receiptCount expected=$KedaEventCount duplicate_receipts=$duplicateReceipts."
    }
    Write-Host "keda_drain=PASS published=$($snapshot.Published) receipts=$receiptCount duplicate_receipts=0"
}

function Restore-KedaScene {
    param([string]$Prefix)
    if (-not $script:kedaDirty) {
        return
    }
    Write-Host "keda_cleanup=START"
    $cleanupSucceeded = $false
    $invariantError = $null
    try {
        try {
            Assert-KedaDrainInvariant -Prefix $Prefix
        } catch {
            $invariantError = $_
        }
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
        if ($null -ne $invariantError) {
            throw $invariantError
        }
    } finally {
        if ($cleanupSucceeded) {
            $script:kedaDirty = $false
            $script:kedaScalePassed = $false
        }
    }
}

function Invoke-KedaScene {
    Write-Scene "HERO 2 | KEDA scales on user-visible work pressure"
    $prefix = "$runId-keda"
    $script:kedaDirty = $true
    try {
        $baseline = Get-KedaSnapshot -Prefix $prefix
        $baselineParts = $baseline.Replicas -split ","
        if ([int]$baselineParts[0] -ne 1 -or [int]$baselineParts[1] -ne 1) {
            throw "KEDA scene requires a 1/1 worker baseline; current desired/available=$($baseline.Replicas)."
        }
        $scaledObjectReady = Invoke-Kubectl @("-n", $Namespace, "get", "scaledobject",
            "inner-cosmos-worker-outbox", "-o", "jsonpath={.status.conditions[?(@.type=='Ready')].status}")
        if ($scaledObjectReady -ne "True") {
            throw "KEDA ScaledObject is not Ready."
        }
        $grafanaPort = Get-OrStartServicePort -Name "grafana" -StablePort 3000 `
            -TargetNamespace "observability" -Resource "svc/grafana" -RemotePort 3000
        Write-Host "Audience board: http://127.0.0.1:$grafanaPort/d/inner-cosmos-events/work-pressure-contract-c2b7-outbox-and-keda?orgId=1&refresh=5s&from=now-5m&to=now&viewPanel=6"
        Write-Host "Goal: business backlog -> worker 1/1 to at least 3/3 in under 45 seconds."
        Write-Host "H2_PRESENTER_READY baseline=1/1 scaledobject=Ready scale_gate=40s" -ForegroundColor Green
        $timer = [Diagnostics.Stopwatch]::StartNew()

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

        $sawScaleOut = $false
        $deadline = (Get-Date).AddSeconds(40)
        do {
            $snapshot = Get-KedaSnapshot -Prefix $prefix
            Write-Host ("t+{0,3}s worker={1,-5} outstanding={2,-5} published={3}" -f
                [int]$timer.Elapsed.TotalSeconds, $snapshot.Replicas,
                $snapshot.Outstanding, $snapshot.Published)
            $replicaParts = $snapshot.Replicas -split ","
            $desired = [int]$replicaParts[0]
            $available = if ($replicaParts.Count -gt 1 -and $replicaParts[1]) { [int]$replicaParts[1] } else { 0 }
            if ($desired -ge 3 -and $available -ge 3) {
                $sawScaleOut = $true
                break
            }
            Start-Sleep -Seconds 2
        } until ((Get-Date) -ge $deadline)
        $timer.Stop()

        if (-not $sawScaleOut) {
            throw "KEDA did not reach desired/available >= 3/3 inside the 40-second experiment gate."
        }
        Write-Host ("KEDA_SCALE_OUT_PASS elapsed_ms={0} worker={1} outstanding={2}" -f
            [long]$timer.Elapsed.TotalMilliseconds, $snapshot.Replicas, $snapshot.Outstanding) -ForegroundColor Green
        $script:kedaScalePassed = $true
        Write-Host "The backlog now drains naturally; HPA stabilization and scale-in remain visible for HERO 3."
    } catch {
        Restore-KedaScene -Prefix $prefix
        throw
    }
}

function Invoke-ObservabilityScene {
    param([object]$ExistingAccount)
    Write-Scene "HERO 3 | one trace crosses API and Worker"
    $sceneTimer = [Diagnostics.Stopwatch]::StartNew()
    $servicePort = if ($null -ne $ExistingAccount) {
        ([Uri]$ExistingAccount.BaseUrl).Port
    } else {
        Get-OrStartServicePort -Name "kind-api" -StablePort 8081 `
            -TargetNamespace $Namespace -Resource "svc/inner-cosmos-api" -RemotePort 8080
    }
    $serviceUrl = "http://127.0.0.1:$servicePort"
    Write-Host "H3_PRESENTER_READY api=$serviceUrl completion_gate=60s" -ForegroundColor Green
    $account = $ExistingAccount
    if ($null -eq $account) {
        $account = New-DemoConversation -BaseUrl $serviceUrl -CreateRichReply
        $headers = Get-CsrfHeader -BaseUrl $serviceUrl -Session $account.Session
        $null = Invoke-Api -BaseUrl $serviceUrl -Path "/api/dialog/session/$($account.SessionId)/finish" `
            -Method POST -Session $account.Session -Headers $headers -Body @{}
    } else {
        # H1 uses the real SSE path, whose generation deliberately crosses executor
        # boundaries. H3 creates a fresh synchronous business turn so one controlled
        # W3C trace deterministically contains HTTP + memory + provider latency.
        $headers = Get-CsrfHeader -BaseUrl $serviceUrl -Session $account.Session
        $dialog = Invoke-Api -BaseUrl $serviceUrl -Path "/api/dialog/session/create" -Method POST `
            -Session $account.Session -Headers $headers -Body @{ title = "Hero 3 Business Trace" }
        if (-not $dialog.success) {
            throw "Unable to create the H3 trace dialog."
        }
        $account.SessionId = [long]$dialog.data.id
        $headers = Get-CsrfHeader -BaseUrl $serviceUrl -Session $account.Session
        $headers["Idempotency-Key"] = "$runId-observability-message"
        $traceContext = New-W3cTraceContext
        $headers["traceparent"] = $traceContext.Traceparent
        $clientTimer = [Diagnostics.Stopwatch]::StartNew()
        $reply = Invoke-Api -BaseUrl $serviceUrl -Path "/api/v1/aurora/message-rich" -Method POST `
            -Session $account.Session -Headers $headers -Body @{
                sessionId = $account.SessionId
                message = "Please turn this demo pressure into one action I can begin in ten minutes."
                mode = "ACTION_SPLIT"
            }
        $clientTimer.Stop()
        if (-not $reply.success) {
            throw "Fresh H3 Aurora business trace failed."
        }
        $account | Add-Member -NotePropertyName AuroraTraceId -NotePropertyValue $traceContext.TraceId -Force
        $account | Add-Member -NotePropertyName AuroraClientLatencyMs `
            -NotePropertyValue ([long]$clientTimer.Elapsed.TotalMilliseconds) -Force
        $headers = Get-CsrfHeader -BaseUrl $serviceUrl -Session $account.Session
        $null = Invoke-Api -BaseUrl $serviceUrl -Path "/api/dialog/session/$($account.SessionId)/finish" `
            -Method POST -Session $account.Session -Headers $headers -Body @{}
    }

    $auroraTraceId = $account.AuroraTraceId
    if ([string]::IsNullOrWhiteSpace($auroraTraceId)) {
        throw "No controlled Aurora trace id was captured for the user action."
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

    $jaegerPort = Get-OrStartServicePort -Name "jaeger" -StablePort 16686 `
        -TargetNamespace $Namespace -Resource "svc/inner-cosmos-jaeger" -RemotePort 16686
    $prometheusPort = Get-OrStartServicePort -Name "prometheus" -StablePort 9090 `
        -TargetNamespace "observability" -Resource "svc/prometheus" -RemotePort 9090
    $grafanaPort = Get-OrStartServicePort -Name "grafana" -StablePort 3000 `
        -TargetNamespace "observability" -Resource "svc/grafana" -RemotePort 3000

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
            $candidateOperations = @($candidateTrace.spans.operationName)
            if ($candidateServices -contains "inner-cosmos-api" -and
                $candidateServices -contains "inner-cosmos-worker" -and
                $candidateOperations -contains "inner.cosmos.outbox.consume" -and
                $candidateOperations -contains "inner.cosmos.projection.memory" -and
                $candidateOperations -contains "inner.cosmos.projection.profile") {
                $traceReady = $true
                break
            }
        }
        Start-Sleep -Seconds 2
    } until ((Get-Date) -ge $deadline)
    if (-not $traceReady) {
        throw "Jaeger did not expose a complete API-to-worker trace $traceId in time."
    }

    $auroraTrace = $null
    $auroraTraceReady = $false
    $deadline = (Get-Date).AddSeconds(45)
    do {
        try {
            $auroraTrace = Invoke-RestMethod -Uri "http://127.0.0.1:$jaegerPort/api/traces/$auroraTraceId" -TimeoutSec 10
        } catch {
            $auroraTrace = $null
        }
        if ($null -ne $auroraTrace -and @($auroraTrace.data).Count -gt 0) {
            $auroraOperations = @(@($auroraTrace.data)[0].spans.operationName)
            if ($auroraOperations -contains "inner.cosmos.ai.provider") {
                $auroraTraceReady = $true
                break
            }
        }
        Start-Sleep -Seconds 2
    } until ((Get-Date) -ge $deadline)
    if (-not $auroraTraceReady) {
        throw "Jaeger did not expose the complete user-to-Aurora trace $auroraTraceId in time."
    }

    # Jaeger is eventually consistent. If the controlled user turn and the durable
    # continuation share a W3C trace, prefer the later, richer snapshot.
    if ($traceId -eq $auroraTraceId) {
        $trace = $auroraTrace
    } else {
        $trace = Invoke-RestMethod -Uri "http://127.0.0.1:$jaegerPort/api/traces/$traceId" -TimeoutSec 10
    }
    $firstTrace = @($trace.data)[0]
    $services = @($firstTrace.processes.PSObject.Properties.Value.serviceName | Sort-Object -Unique)
    $spans = @($firstTrace.spans)
    $forbiddenPattern = "user\.id|enduser\.id|message\.content|request\.body|response\.body|db\.statement|gen_ai\.prompt|gen_ai\.completion|url\.query|prompt|completion"
    $forbidden = @(
        $spans.tags |
            Where-Object { $_.key -match $forbiddenPattern }
    ).Count
    if ($services -notcontains "inner-cosmos-api" -or $services -notcontains "inner-cosmos-worker") {
        throw "Trace does not cross both API and worker: $($services -join ', ')."
    }
    if ($forbidden -ne 0) {
        throw "Trace privacy contract failed: $forbidden forbidden tags."
    }

    $firstAuroraTrace = @($auroraTrace.data)[0]
    $auroraSpans = @($firstAuroraTrace.spans)
    $auroraForbidden = @(
        $auroraSpans.tags |
            Where-Object { $_.key -match $forbiddenPattern }
    ).Count
    if ($auroraForbidden -ne 0) {
        throw "Aurora trace privacy contract failed: $auroraForbidden forbidden tags."
    }
    $traceStart = ($auroraSpans | Measure-Object -Property startTime -Minimum).Minimum
    $waterfall = @($auroraSpans | Sort-Object startTime | ForEach-Object {
        $serviceName = $firstAuroraTrace.processes.($_.processID).serviceName
        [pscustomobject]@{
            OffsetMs = [Math]::Round(($_.startTime - $traceStart) / 1000.0, 1)
            DurationMs = [Math]::Round($_.duration / 1000.0, 1)
            Service = $serviceName
            Operation = $_.operationName
        }
    })
    $providerSpan = $auroraSpans | Where-Object operationName -eq "inner.cosmos.ai.provider" |
        Sort-Object duration -Descending | Select-Object -First 1
    $memorySpan = $auroraSpans | Where-Object operationName -eq "inner.cosmos.memory.retrieve" |
        Sort-Object duration -Descending | Select-Object -First 1
    $httpSpan = $auroraSpans | Sort-Object duration -Descending | Select-Object -First 1
    $requestMs = $httpSpan.duration / 1000.0
    $providerMs = $providerSpan.duration / 1000.0
    $memoryMs = if ($null -ne $memorySpan) { $memorySpan.duration / 1000.0 } else { 0.0 }
    $platformMs = [Math]::Max(0.0, $requestMs - $providerMs - $memoryMs)
    $providerShare = if ($requestMs -gt 0) { 100.0 * $providerMs / $requestMs } else { 0.0 }
    $consumeSpan = $spans | Where-Object operationName -eq "inner.cosmos.outbox.consume" |
        Sort-Object duration -Descending | Select-Object -First 1
    $memoryProjection = $spans | Where-Object operationName -eq "inner.cosmos.projection.memory" |
        Sort-Object duration -Descending | Select-Object -First 1
    $profileProjection = $spans | Where-Object operationName -eq "inner.cosmos.projection.profile" |
        Sort-Object duration -Descending | Select-Object -First 1

    $metricUrl = "http://127.0.0.1:$prometheusPort/api/v1/query?query=" +
        [Uri]::EscapeDataString('max(kube_deployment_status_replicas_available{namespace="inner-cosmos-w3",deployment="inner-cosmos-api"})')
    $metric = Invoke-RestMethod -Uri $metricUrl -TimeoutSec 10
    $apiReplicas = $metric.data.result[0].value[1]

    Write-Host ""
    Write-Host "USER ACTION | message -> Aurora -> durable reply" -ForegroundColor Cyan
    Write-Host ("client_end_to_end_ms={0} traced_request_ms={1:N1} memory_ms={2:N1} provider_ms={3:N1} platform_overhead_ms={4:N1} provider_share_pct={5:N1}" -f
        $account.AuroraClientLatencyMs,
        $requestMs, $memoryMs, $providerMs, $platformMs, $providerShare)
    $waterfall | Format-Table OffsetMs, DurationMs, Service, Operation -AutoSize
    Write-Host "Aurora trace: http://127.0.0.1:$jaegerPort/trace/$auroraTraceId"
    Write-Host ""
    Write-Host "ASYNC CONTINUATION | finish dialog -> outbox -> worker projections" -ForegroundColor Cyan
    Write-Host "trace_id=$traceId"
    if ($null -ne $consumeSpan) {
        Write-Host ("worker_consume_ms={0:N1} memory_projection_ms={1:N1} profile_projection_ms={2:N1}" -f
            ($consumeSpan.duration / 1000.0),
            $(if ($null -ne $memoryProjection) { $memoryProjection.duration / 1000.0 } else { 0.0 }),
            $(if ($null -ne $profileProjection) { $profileProjection.duration / 1000.0 } else { 0.0 }))
    }
    Write-Host "services=$($services -join ',') spans=$($spans.Count) forbidden_tags=$forbidden"
    Write-Host "prometheus_api_available_replicas=$apiReplicas"
    Write-Host "Grafana KEDA: http://127.0.0.1:$grafanaPort/d/inner-cosmos-events/work-pressure-contract-c2b7-outbox-and-keda?orgId=1&refresh=5s&from=now-15m&to=now&viewPanel=6"
    Write-Host "Grafana recovery: http://127.0.0.1:$grafanaPort/d/inner-cosmos-recovery/continuity-contract-c2b7-pod-recovery-live?orgId=1&refresh=5s"
    Write-Host "Jaeger trace: http://127.0.0.1:$jaegerPort/trace/$traceId"
    $sceneTimer.Stop()
    if ($sceneTimer.Elapsed.TotalSeconds -gt 60) {
        throw "H3 exceeded the 60-second presenter gate: $([long]$sceneTimer.Elapsed.TotalMilliseconds) ms."
    }
    Write-Host ("HERO_3_PASS elapsed_ms={0} | actual Aurora request path + API-to-Worker continuation; privacy scan=0" -f
        [long]$sceneTimer.Elapsed.TotalMilliseconds) -ForegroundColor Green
}

$showcaseError = $null
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
Push-Location $root
try {
    Assert-Preflight -AllowShowcaseBacklog:($Scene -eq "Observability")
    switch ($Scene) {
        "Preflight" {
            Write-Host "No cluster writes were performed."
        }
        "Continuity" {
            $null = Invoke-ContinuityScene
        }
        "Keda" {
            Invoke-KedaScene
            if ($HoldViews) {
                Write-Host "H2 is holding the workload while H3 shows drain and scale-in."
                $null = Read-Host "After H3, press Enter to clean synthetic rows and restore worker=1"
            }
        }
        "Observability" {
            Invoke-ObservabilityScene
            if ($HoldViews) {
                Write-Host "H3 trace and dashboards are being held for the presenter."
                $null = Read-Host "Press Enter after the audience has inspected both traces"
            }
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
} catch {
    $showcaseError = $_
} finally {
    if ($kedaDirty) {
        try {
            Restore-KedaScene -Prefix "$runId-keda"
        } catch {
            if ($null -eq $showcaseError) {
                $showcaseError = $_
            } else {
                Write-Warning "Emergency KEDA cleanup also failed: $($_.Exception.Message)"
            }
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
if ($null -ne $showcaseError) {
    throw $showcaseError
}
