[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Origin,

    [ValidateRange(30, 100)]
    [int]$UserCount = 30,

    [ValidateRange(1, 100)]
    [int]$ThrottleLimit = 30,

    [ValidateRange(1000, 120000)]
    [int]$MaxRegistrationP95Ms = 15000,

    [ValidateRange(1000, 120000)]
    [int]$MaxCalibrationP95Ms = 10000,

    [ValidateRange(5000, 180000)]
    [int]$MaxAuroraP95Ms = 90000,

    [switch]$KeepAccounts,

    [string]$ReportPath = ""
)

if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "This burst verifier requires PowerShell 7 or newer."
}

$ErrorActionPreference = "Stop"
$originUri = [Uri]$Origin
if ($originUri.Scheme -ne "https") {
    throw "The classroom burst verifier requires an HTTPS origin."
}
$origin = $originUri.GetLeftPart([UriPartial]::Authority)
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $root ".demo-runtime\burst-30-report.json"
}

$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$password = "Burst-$runId-$([Guid]::NewGuid().ToString('N'))!"
$specs = 0..($UserCount - 1) | ForEach-Object {
    [pscustomobject]@{
        Index = $_
        Username = "demoburst$runId-$('{0:d2}' -f $_)"
        Nickname = "Classmate $($_ + 1)"
    }
}

$failureMessages = [System.Collections.Generic.List[string]]::new()
$results = @()
$socialSessions = [System.Collections.Generic.List[object]]::new()
$cleanupDeleted = 0
$cleanupSkipped = 0
$socialDiscoveryChecks = 0
$socialDiscoveryFailed = $false
$socialConnectionFailed = $false
$ringConnections = 0

function Invoke-Envelope {
    param(
        [Parameter(Mandatory = $true)]
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [Parameter(Mandatory = $true)]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [object]$Body = $null,
        [int]$TimeoutSec = 120
    )

    $headers = @{}
    if ($Method -notin @("GET", "HEAD")) {
        $csrf = Invoke-RestMethod -Uri "$origin/api/v1/auth/csrf" `
            -WebSession $Session -TimeoutSec 20
        if ($csrf.data -and
            -not [string]::IsNullOrWhiteSpace([string]$csrf.data.headerName) -and
            -not [string]::IsNullOrWhiteSpace([string]$csrf.data.token)) {
            $headers[[string]$csrf.data.headerName] = [string]$csrf.data.token
        }
        $headers["Idempotency-Key"] = [Guid]::NewGuid().ToString()
    }

    $params = @{
        Uri = "$origin$Path"
        Method = $Method
        WebSession = $Session
        Headers = $headers
        TimeoutSec = $TimeoutSec
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $params.Body = [Text.Encoding]::UTF8.GetBytes($json)
    }

    $web = Invoke-WebRequest @params
    $bytes = if ($web.RawContentStream) {
        $web.RawContentStream.ToArray()
    } else {
        [Text.Encoding]::UTF8.GetBytes($web.Content)
    }
    $response = ([Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json)
    if (-not $response.success) {
        throw "API envelope failed at $Path."
    }
    return $response.data
}

function Get-Percentile {
    param(
        [double[]]$Values,
        [double]$Percentile
    )
    if ($null -eq $Values -or $Values.Count -eq 0) { return 0 }
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling($sorted.Count * $Percentile) - 1
    $index = [Math]::Max(0, [Math]::Min($sorted.Count - 1, $index))
    return [Math]::Round([double]$sorted[$index], 2)
}

function New-AuthenticatedSession {
    param([pscustomobject]$Spec)
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $user = Invoke-Envelope $session "POST" "/api/v1/auth/login" @{
        username = $Spec.Username
        password = $password
        timezone = "Asia/Singapore"
    }
    return [pscustomobject]@{
        Index = [int]$Spec.Index
        Username = [string]$Spec.Username
        UserId = [long]$user.id
        Session = $session
    }
}

function Get-HttpStatusCode {
    param($Failure)
    try {
        if ($null -ne $Failure.Exception.Response.StatusCode) {
            return [int]$Failure.Exception.Response.StatusCode
        }
    } catch {
        return 0
    }
    return 0
}

try {
    $health = Invoke-RestMethod -Uri "$origin/actuator/health" -TimeoutSec 30
    if ($health.status -ne "UP") {
        throw "Public health is not UP."
    }

    # One runspace owns one WebRequestSession for its complete register -> calibrate ->
    # Aurora trajectory. No session object crosses runspaces. Results contain only timings,
    # bounded provider metadata and stage/error categories; no prompt or reply text is emitted.
    $results = @($specs | ForEach-Object -Parallel {
        $spec = $_
        $originLocal = $using:origin
        $passwordLocal = $using:password
        $stage = "REGISTER"
        $registered = $false
        $userId = 0L
        $registrationMs = 0.0
        $calibrationMs = 0.0
        $auroraMs = 0.0
        $totalWatch = [Diagnostics.Stopwatch]::StartNew()

        function Invoke-ParallelEnvelope {
            param(
                [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
                [string]$Method,
                [string]$Path,
                [object]$Body = $null
            )

            $headers = @{}
            if ($Method -notin @("GET", "HEAD")) {
                $csrf = Invoke-RestMethod -Uri "$originLocal/api/v1/auth/csrf" `
                    -WebSession $Session -TimeoutSec 20
                if ($csrf.data -and
                    -not [string]::IsNullOrWhiteSpace([string]$csrf.data.headerName) -and
                    -not [string]::IsNullOrWhiteSpace([string]$csrf.data.token)) {
                    $headers[[string]$csrf.data.headerName] = [string]$csrf.data.token
                }
                $headers["Idempotency-Key"] = [Guid]::NewGuid().ToString()
            }

            $params = @{
                Uri = "$originLocal$Path"
                Method = $Method
                WebSession = $Session
                Headers = $headers
                TimeoutSec = 150
                UseBasicParsing = $true
            }
            if ($null -ne $Body) {
                $params.ContentType = "application/json; charset=utf-8"
                $json = $Body | ConvertTo-Json -Depth 10 -Compress
                $params.Body = [Text.Encoding]::UTF8.GetBytes($json)
            }
            $web = Invoke-WebRequest @params
            $bytes = if ($web.RawContentStream) {
                $web.RawContentStream.ToArray()
            } else {
                [Text.Encoding]::UTF8.GetBytes($web.Content)
            }
            $response = ([Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json)
            if (-not $response.success) { throw "API envelope failed at $Path." }
            return $response.data
        }

        try {
            $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()

            $watch = [Diagnostics.Stopwatch]::StartNew()
            $registeredUser = Invoke-ParallelEnvelope $session "POST" "/api/v1/auth/register" @{
                username = [string]$spec.Username
                nickname = [string]$spec.Nickname
                password = $passwordLocal
            }
            $watch.Stop()
            $registrationMs = $watch.Elapsed.TotalMilliseconds
            $registered = $true
            $userId = [long]$registeredUser.id

            $stage = "CALIBRATION"
            $tones = @(
                "Warm, candid and specific",
                "Quiet, perceptive and unhurried",
                "Playful, intelligent and direct",
                "Clear-eyed, practical and caring",
                "Curious, spacious and emotionally precise"
            )
            $contexts = @(
                "Building something I care about while wondering whether it is ready",
                "Balancing coursework, friendships and the need for quiet",
                "Starting a new chapter and wanting conversations with substance",
                "Trying to make one difficult decision without rushing myself",
                "Looking for people who are thoughtful, curious and sincere"
            )
            $watch.Restart()
            $profile = Invoke-ParallelEnvelope $session "PUT" "/api/user/profile" @{
                auroraTone = $tones[[int]$spec.Index % $tones.Count]
                proactiveSensitivity = 2 + ([int]$spec.Index % 4)
                reflectionDepth = 2 + (([int]$spec.Index + 1) % 4)
                allowMemoryRecall = $true
                allowMultiMessage = $true
                currentEnvironmentLabel = $contexts[[int]$spec.Index % $contexts.Count]
                timezone = "Asia/Singapore"
            }
            $watch.Stop()
            $calibrationMs = $watch.Elapsed.TotalMilliseconds
            if ([string]::IsNullOrWhiteSpace([string]$profile.auroraTone) -or
                [string]::IsNullOrWhiteSpace([string]$profile.currentEnvironmentLabel)) {
                throw "Calibration did not persist."
            }

            $stage = "AURORA"
            $dialog = Invoke-ParallelEnvelope $session "POST" "/api/dialog/session/create" @{
                title = "Classroom burst"
                sessionType = "AURORA_CHAT"
            }
            $themes = @(
                "why a project can feel personal",
                "how I know when to trust someone",
                "the kind of future that feels genuinely mine",
                "what I notice when I finally slow down",
                "why some conversations make me feel more alive",
                "how ambition and tenderness can coexist"
            )
            $prompt = "You can call me $($spec.Nickname). Lately I keep returning to " +
                "$($themes[[int]$spec.Index % $themes.Count]). Start wherever you genuinely " +
                "find the most interesting thread; speak to me like a perceptive new friend, " +
                "not like a coach or a questionnaire."
            $watch.Restart()
            $reply = Invoke-ParallelEnvelope $session "POST" "/api/v1/aurora/message-rich" @{
                sessionId = [long]$dialog.id
                message = $prompt
                inputType = "TEXT"
                mode = "DAILY_TALK"
                timezone = "Asia/Singapore"
                clientMessageId = "burst-$($using:runId)-$($spec.Index)"
                foregroundAcknowledgementSent = $true
            }
            $watch.Stop()
            $auroraMs = $watch.Elapsed.TotalMilliseconds

            $messages = @($reply.messages)
            $replyLength = (($messages | ForEach-Object { [string]$_ }) -join "`n").Length
            if ($replyLength -lt 8) { throw "Aurora returned no substantive message." }
            $provider = [string]$reply.aiState.provider
            $model = [string]$reply.aiState.model
            if ([string]::IsNullOrWhiteSpace($provider) -or $provider.ToLowerInvariant() -eq "mock") {
                throw "Aurora did not identify a real provider."
            }
            if (-not [bool]$reply.aiState.apiKeyConfigured -or [bool]$reply.aiState.fallbackAllowed) {
                throw "Aurora real-provider fail-closed contract is not active."
            }
            if ([bool]$reply.agentLoop.speakerFallbackUsed) {
                throw "The authoritative Aurora speaker used fallback."
            }
            $orchestrationWarnings = [System.Collections.Generic.List[string]]::new()
            if ([string]$reply.agentLoop.runtime -ne "dual-kernel.pipeline.v2") {
                $orchestrationWarnings.Add("runtime=$([string]$reply.agentLoop.runtime)")
            }
            if (-not [bool]$reply.agentLoop.backgroundPlannerScheduled) {
                $orchestrationWarnings.Add("background-planner-not-observed")
            }
            if (@($reply.riskFlags) -contains "EMERGENCY_FALLBACK") {
                throw "Aurora returned the deterministic emergency fallback."
            }

            $totalWatch.Stop()
            [pscustomobject]@{
                Index = [int]$spec.Index
                Registered = $registered
                UserId = $userId
                Success = $true
                Stage = "COMPLETE"
                HttpStatus = 200
                Is429 = $false
                RegistrationMs = [Math]::Round($registrationMs, 2)
                CalibrationMs = [Math]::Round($calibrationMs, 2)
                AuroraMs = [Math]::Round($auroraMs, 2)
                TotalMs = [Math]::Round($totalWatch.Elapsed.TotalMilliseconds, 2)
                Provider = $provider
                Model = $model
                Runtime = [string]$reply.agentLoop.runtime
                CriticFallbackUsed = [bool]$reply.agentLoop.criticFallbackUsed
                ReplyLength = $replyLength
                OrchestrationWarnings = @($orchestrationWarnings) -join ","
            }
        } catch {
            $totalWatch.Stop()
            $statusCode = 0
            try {
                if ($null -ne $_.Exception.Response.StatusCode) {
                    $statusCode = [int]$_.Exception.Response.StatusCode
                }
            } catch {
                $statusCode = 0
            }
            [pscustomobject]@{
                Index = [int]$spec.Index
                Registered = $registered
                UserId = $userId
                Success = $false
                Stage = $stage
                HttpStatus = $statusCode
                Is429 = ($statusCode -eq 429)
                RegistrationMs = [Math]::Round($registrationMs, 2)
                CalibrationMs = [Math]::Round($calibrationMs, 2)
                AuroraMs = [Math]::Round($auroraMs, 2)
                TotalMs = [Math]::Round($totalWatch.Elapsed.TotalMilliseconds, 2)
                Provider = ""
                Model = ""
                Runtime = ""
                CriticFallbackUsed = $false
                ReplyLength = 0
                OrchestrationWarnings = ""
                ErrorCategory = "$stage`_HTTP_$statusCode"
            }
        }
    } -ThrottleLimit $ThrottleLimit)

    $results = @($results | Sort-Object Index)
    $successful = @($results | Where-Object Success)
    $http429 = @($results | Where-Object Is429)
    if ($successful.Count -ne $UserCount) {
        $failedStages = @($results | Where-Object { -not $_.Success } |
            Group-Object Stage | ForEach-Object { "$($_.Name)=$($_.Count)" })
        $failureMessages.Add(
            "Burst trajectory completed for $($successful.Count)/$UserCount users; " +
            "failed stages: $($failedStages -join ', ').")
    }
    if ($http429.Count -gt 0) {
        $failureMessages.Add("Provider/API returned HTTP 429 for $($http429.Count) users.")
    }

    # A failed earlier stage records zero for later timings. Exclude those structural zeroes so
    # they cannot make a degraded run's latency percentiles look better than the completed calls.
    $registrationSamples = @($results.RegistrationMs | Where-Object { $_ -gt 0 })
    $calibrationSamples = @($results.CalibrationMs | Where-Object { $_ -gt 0 })
    $auroraSamples = @($results.AuroraMs | Where-Object { $_ -gt 0 })
    $totalSamples = @($results.TotalMs | Where-Object { $_ -gt 0 })
    $registrationP95 = Get-Percentile $registrationSamples 0.95
    $calibrationP95 = Get-Percentile $calibrationSamples 0.95
    $auroraP95 = Get-Percentile $auroraSamples 0.95
    if ($registrationP95 -gt $MaxRegistrationP95Ms) {
        $failureMessages.Add("Registration p95 ${registrationP95}ms exceeds ${MaxRegistrationP95Ms}ms.")
    }
    if ($calibrationP95 -gt $MaxCalibrationP95Ms) {
        $failureMessages.Add("Calibration p95 ${calibrationP95}ms exceeds ${MaxCalibrationP95Ms}ms.")
    }
    if ($auroraP95 -gt $MaxAuroraP95Ms) {
        $failureMessages.Add("Aurora total-latency p95 ${auroraP95}ms exceeds ${MaxAuroraP95Ms}ms.")
    }

    # Social proof starts only when every burst actor completed the real-provider path.
    # Logging in all 30 immediately before discovery makes them the newest HUMAN accounts;
    # /api/social/people has a deliberate LIMIT 30, so each actor must see the other 29.
    if ($successful.Count -eq $UserCount) {
        foreach ($spec in $specs) {
            $socialSessions.Add((New-AuthenticatedSession $spec))
        }
        $allUserIds = @($socialSessions | ForEach-Object { [long]$_.UserId })
        foreach ($actor in $socialSessions) {
            $people = @(Invoke-Envelope $actor.Session "GET" "/api/social/people")
            $visibleIds = @($people | ForEach-Object { [long]$_.id })
            $expectedIds = @($allUserIds | Where-Object { $_ -ne [long]$actor.UserId })
            $missing = @($expectedIds | Where-Object { $_ -notin $visibleIds })
            if ($missing.Count -gt 0) {
                $socialDiscoveryFailed = $true
                $failureMessages.Add(
                    "Discovery actor $($actor.Index) cannot see $($missing.Count) of the other burst users.")
            }
            $socialDiscoveryChecks++
        }

        # Continue through the connection ring even if a latency gate already failed. This keeps
        # the report diagnostically complete: performance failure and social-function failure stay
        # distinguishable. Only an incomplete discovery set blocks the ring.
        if (-not $socialDiscoveryFailed) {
            $relations = [System.Collections.Generic.List[object]]::new()
            for ($i = 0; $i -lt $UserCount; $i++) {
                $requester = $socialSessions[$i]
                $target = $socialSessions[($i + 1) % $UserCount]
                $relation = Invoke-Envelope $requester.Session "POST" "/api/social/friends/request" @{
                    userId = [long]$target.UserId
                    source = "CLASSROOM_BURST_RING"
                }
                $relations.Add([pscustomobject]@{
                    RelationId = [long]$relation.id
                    RequesterIndex = $i
                    TargetIndex = (($i + 1) % $UserCount)
                })
            }
            foreach ($relation in $relations) {
                $target = $socialSessions[[int]$relation.TargetIndex]
                $accepted = Invoke-Envelope $target.Session "POST" `
                    "/api/social/friends/$($relation.RelationId)/accept" @{}
                if ([string]$accepted.status -ne "ACCEPTED") {
                    $socialConnectionFailed = $true
                    $failureMessages.Add(
                        "Ring connection $($relation.RequesterIndex)->$($relation.TargetIndex) was not accepted.")
                } else {
                    $ringConnections++
                }
            }

            foreach ($actor in $socialSessions) {
                $nextId = [long]$socialSessions[([int]$actor.Index + 1) % $UserCount].UserId
                $previousIndex = ([int]$actor.Index - 1 + $UserCount) % $UserCount
                $previousId = [long]$socialSessions[$previousIndex].UserId
                $friends = @(Invoke-Envelope $actor.Session "GET" "/api/social/friends")
                $friendIds = @($friends | ForEach-Object { [long]$_.userId })
                if ($nextId -notin $friendIds -or $previousId -notin $friendIds) {
                    $socialConnectionFailed = $true
                    $failureMessages.Add(
                        "Ring verification left actor $($actor.Index) without both neighbours.")
                }
            }
        }
    }
} catch {
    $failureMessages.Add("Fatal verifier error: $($_.Exception.Message)")
} finally {
    if ($KeepAccounts) {
        $cleanupSkipped = $UserCount
    } else {
        # Attempt every spec, not only returned results. This covers a runspace that registered
        # successfully but failed before it could return its result object.
        foreach ($spec in @($specs | Sort-Object Index -Descending)) {
            try {
                $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
                try {
                    $null = Invoke-Envelope $session "POST" "/api/v1/auth/login" @{
                        username = $spec.Username
                        password = $password
                        timezone = "Asia/Singapore"
                    }
                } catch {
                    if ((Get-HttpStatusCode $_) -eq 401) {
                        $cleanupSkipped++
                        continue
                    }
                    throw
                }
                $null = Invoke-Envelope $session "DELETE" "/api/user/account" @{ password = $password }
                $cleanupDeleted++
            } catch {
                $failureMessages.Add("Cleanup failed for actor index $($spec.Index).")
            }
        }
    }
}

$successResults = @($results | Where-Object Success)
$report = [ordered]@{
    status = if ($failureMessages.Count -eq 0) { "PASS" } else { "FAIL" }
    generatedAt = [DateTimeOffset]::UtcNow.ToString("o")
    originAuthority = $originUri.Authority
    requestedUsers = $UserCount
    throttleLimit = $ThrottleLimit
    successfulRealAuroraUsers = $successResults.Count
    http429Count = @($results | Where-Object Is429).Count
    providerSet = @($successResults.Provider | Where-Object { $_ } | Sort-Object -Unique)
    modelSet = @($successResults.Model | Where-Object { $_ } | Sort-Object -Unique)
    runtimeSet = @($successResults.Runtime | Where-Object { $_ } | Sort-Object -Unique)
    orchestrationWarnings = @($successResults.OrchestrationWarnings | Where-Object { $_ } | Group-Object | ForEach-Object { [ordered]@{ warning = $_.Name; count = $_.Count } })
    criticFallbackCount = @($successResults | Where-Object CriticFallbackUsed).Count
    latencyMs = [ordered]@{
        registrationP50 = Get-Percentile $registrationSamples 0.50
        registrationP95 = Get-Percentile $registrationSamples 0.95
        calibrationP50 = Get-Percentile $calibrationSamples 0.50
        calibrationP95 = Get-Percentile $calibrationSamples 0.95
        auroraTotalP50 = Get-Percentile $auroraSamples 0.50
        auroraTotalP95 = Get-Percentile $auroraSamples 0.95
        auroraTotalP99 = Get-Percentile $auroraSamples 0.99
        fullTrajectoryP95 = Get-Percentile $totalSamples 0.95
    }
    thresholdsMs = [ordered]@{
        registrationP95 = $MaxRegistrationP95Ms
        calibrationP95 = $MaxCalibrationP95Ms
        auroraTotalP95 = $MaxAuroraP95Ms
    }
    social = [ordered]@{
        discoveryChecks = $socialDiscoveryChecks
        expectedDiscoveryChecks = $UserCount
        acceptedRingConnections = $ringConnections
        expectedRingConnections = $UserCount
        nobodyLeftAlone = (
            $socialDiscoveryChecks -eq $UserCount -and
            -not $socialDiscoveryFailed -and
            $ringConnections -eq $UserCount -and
            -not $socialConnectionFailed
        )
    }
    cleanup = [ordered]@{
        keptByOperator = [bool]$KeepAccounts
        deletedAccounts = $cleanupDeleted
        absentOrNotRegistered = $cleanupSkipped
    }
    stageFailures = @($results | Where-Object { -not $_.Success } |
        Group-Object Stage | ForEach-Object {
            [ordered]@{ stage = $_.Name; count = $_.Count }
        })
    failures = @($failureMessages)
}

$reportDirectory = Split-Path -Parent $ReportPath
if (-not [string]::IsNullOrWhiteSpace($reportDirectory)) {
    New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
}
$report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $ReportPath -Encoding utf8
$report | ConvertTo-Json -Depth 10
Write-Host "report=$ReportPath"

if ($failureMessages.Count -gt 0) {
    throw "CLASSROOM_BURST_FAILED: $($failureMessages -join ' | ')"
}

Write-Host "CLASSROOM_BURST_PASS"
