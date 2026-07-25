[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Origin,
    [string[]]$Providers = @("DEEPSEEK", "GLM"),
    [int]$Repetitions = 1
)

$ErrorActionPreference = "Stop"
$Origin = $Origin.TrimEnd("/")

$scenarios = @(
    [pscustomobject]@{
        id = "quiet-boundary"
        mode = "DAILY_TALK"
        prompt = "明天要展示这个项目，我很紧张。先别给建议，我只是想把这句话说出来。"
        rejectedAdvice = $true
    },
    [pscustomobject]@{
        id = "relationship-ambiguity"
        mode = "RELATION_REVIEW"
        prompt = "朋友今天突然变得很冷淡。我不知道是不是我做错了，但也不想立刻给他下结论。"
        rejectedAdvice = $false
    },
    [pscustomobject]@{
        id = "action-split"
        mode = "ACTION_SPLIT"
        prompt = "报告、答辩和代码修复全挤在一起，我现在不知道先动哪一个。帮我只拆出十分钟内能开始的一步。"
        rejectedAdvice = $false
    }
)

$bannedPhrases = @(
    "我听到了", "听见了", "这很正常", "是正常的", "我陪着你",
    "我在这里", "我都在", "我知道了", "我听着", "是自然的", "很自然", "说明你在乎",
    "说明这件事对你很重要", "不是坏事"
)

function Get-Csrf([Microsoft.PowerShell.Commands.WebRequestSession]$Session) {
    $web = Invoke-WebRequest -UseBasicParsing -Uri "$Origin/api/v1/auth/csrf" -Method Get -WebSession $Session
    $bytes = if ($web.RawContentStream) { $web.RawContentStream.ToArray() } else { [Text.Encoding]::UTF8.GetBytes($web.Content) }
    $response = ([Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json)
    if (-not $response.success) { throw "CSRF bootstrap failed: $($response.message)" }
    return $response.data
}

function Invoke-Json(
    [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
    [string]$Method,
    [string]$Path,
    [object]$Body
) {
    $headers = @{}
    if ($Method -notin @("GET", "HEAD", "OPTIONS")) {
        $csrf = Get-Csrf $Session
        $headers[$csrf.headerName] = $csrf.token
    }
    $args = @{
        Uri = "$Origin$Path"
        Method = $Method
        WebSession = $Session
        Headers = $headers
        ContentType = "application/json; charset=utf-8"
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 12 -Compress
        $args.Body = [Text.Encoding]::UTF8.GetBytes($json)
    }
    $web = Invoke-WebRequest @args
    $bytes = if ($web.RawContentStream) { $web.RawContentStream.ToArray() } else { [Text.Encoding]::UTF8.GetBytes($web.Content) }
    $response = ([Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json)
    if (-not $response.success) { throw "$Method $Path failed: $($response.message)" }
    return $response.data
}

function New-BenchmarkIdentity([string]$Provider, [int]$Index) {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
    $username = "bench$($Provider.ToLower())$Index$($suffix.Substring([Math]::Max(0, $suffix.Length - 8)))"
    [void](Invoke-Json $session "POST" "/api/v1/auth/register" @{
        username = $username
        nickname = "Aurora Eval"
        password = "InnerCosmos!2026"
    })
    return $session
}

$results = [System.Collections.Generic.List[object]]::new()
foreach ($provider in $Providers) {
    for ($repeat = 1; $repeat -le $Repetitions; $repeat++) {
        $session = New-BenchmarkIdentity $provider $repeat
        try {
          foreach ($scenario in $scenarios) {
            $dialog = Invoke-Json $session "POST" "/api/dialog/session/create" @{
                title = "Aurora benchmark $($scenario.id)"
                sessionType = "AURORA_CHAT"
            }
            [void](Invoke-Json $session "PUT" "/api/aurora/session/$($dialog.id)/model" @{
                provider = $provider
            })

            $watch = [Diagnostics.Stopwatch]::StartNew()
            $reply = Invoke-Json $session "POST" "/api/aurora/message-rich" @{
                sessionId = $dialog.id
                message = $scenario.prompt
                mode = $scenario.mode
                foregroundAcknowledgementSent = $true
            }
            $watch.Stop()
            $health = Invoke-Json $session "GET" "/api/ai/health" $null

            $text = @($reply.messages) -join "`n"
            $hits = @($bannedPhrases | Where-Object { $text.Contains($_) })
            $adviceBoundaryBroken = $scenario.rejectedAdvice -and (
                $text.Contains("你可以") -or $text.Contains("不妨") -or
                -not [string]::IsNullOrWhiteSpace([string]$reply.smallStep) -or
                -not [string]::IsNullOrWhiteSpace([string]$reply.featureSuggestion)
            )
            $quietBoundaryBroken = $scenario.id -eq "quiet-boundary" -and (
                $text.Contains("?") -or $text.Contains("？") -or
                -not [string]::IsNullOrWhiteSpace([string]$reply.nextQuestion)
            )
            $actionBoundaryBroken = $scenario.id -eq "action-split" -and (
                [string]::IsNullOrWhiteSpace([string]$reply.smallStep) -or
                ($text.Contains("报告") -and $text.Contains("答辩") -and $text.Contains("代码"))
            )
            $results.Add([pscustomobject]@{
                provider = $provider
                repetition = $repeat
                scenario = $scenario.id
                elapsedMs = $watch.ElapsedMilliseconds
                reply = $text
                messageCount = @($reply.messages).Count
                bannedHits = $hits
                adviceBoundaryBroken = $adviceBoundaryBroken
                quietBoundaryBroken = $quietBoundaryBroken
                actionBoundaryBroken = $actionBoundaryBroken
                nextQuestion = $reply.nextQuestion
                smallStep = $reply.smallStep
                runtime = $reply.agentLoop.runtime
                criticRepaired = $reply.agentLoop.criticRepaired
                criticIssues = @($reply.agentLoop.criticIssues)
                stageLatenciesMs = $reply.agentLoop.stageLatenciesMs
                plannerFallbackUsed = $reply.agentLoop.plannerFallbackUsed
                speakerFallbackUsed = $reply.agentLoop.speakerFallbackUsed
                criticFallbackUsed = $reply.agentLoop.criticFallbackUsed
                providerCallSucceeded = $health.lastSuccess
                providerCallFallback = $health.lastFallbackUsed
                providerCallError = $health.lastError
            })
          }
        } finally {
            # Evaluation identities must never leak into the classroom People surface.
            # The result rows are already held locally, so remove the synthetic actor
            # and all of its dependent records even when one scenario fails.
            [void](Invoke-Json $session "DELETE" "/api/user/account" @{
                password = "InnerCosmos!2026"
            })
        }
    }
}

$results | ConvertTo-Json -Depth 8
