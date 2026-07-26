[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Origin,
    [string[]]$Providers = @("DEEPSEEK"),
    [int]$Repetitions = 1
)

$ErrorActionPreference = "Stop"
$Origin = $Origin.TrimEnd("/")
Add-Type -AssemblyName System.Net.Http

$scenarios = @(
    [pscustomobject]@{
        id = "quiet-boundary"
        mode = "DAILY_TALK"
        prompt = "明天要展示这个项目，我很紧张。先别给建议，我只是想把这句话说出来。"
    },
    [pscustomobject]@{
        id = "relationship-ambiguity"
        mode = "RELATION_REVIEW"
        prompt = "朋友今天突然变得很冷淡。我不知道是不是我做错了，但也不想立刻给他下结论。"
    },
    [pscustomobject]@{
        id = "action-split"
        mode = "ACTION_SPLIT"
        prompt = "报告、答辩和代码修复全挤在一起，我现在不知道先动哪一个。帮我只拆出十分钟内能开始的一步。"
    }
)

function New-DemoHttpClient {
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.UseCookies = $true
    $handler.CookieContainer = [Net.CookieContainer]::new()
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(90)
    return [pscustomobject]@{ Client = $client; Handler = $handler }
}

function Invoke-DemoApi {
    param(
        [Net.Http.HttpClient]$Client,
        [string]$Method,
        [string]$Path,
        [object]$Body = $null
    )
    $request = [Net.Http.HttpRequestMessage]::new(
        [Net.Http.HttpMethod]::new($Method), "$Origin$Path")
    try {
        if ($Method -notin @("GET", "HEAD")) {
            $csrfResponse = $Client.GetAsync("$Origin/api/v1/auth/csrf").GetAwaiter().GetResult()
            $csrfText = $csrfResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $csrfResponse.EnsureSuccessStatusCode()
            $csrf = ($csrfText | ConvertFrom-Json).data
            if ($csrf -and
                -not [string]::IsNullOrWhiteSpace([string]$csrf.headerName) -and
                -not [string]::IsNullOrWhiteSpace([string]$csrf.token)) {
                [void]$request.Headers.TryAddWithoutValidation(
                    [string]$csrf.headerName, [string]$csrf.token)
            }
            [void]$request.Headers.TryAddWithoutValidation("Idempotency-Key", [Guid]::NewGuid().ToString())
        }
        if ($null -ne $Body) {
            $json = $Body | ConvertTo-Json -Depth 12 -Compress
            $request.Content = [Net.Http.StringContent]::new(
                $json, [Text.Encoding]::UTF8, "application/json")
        }
        $response = $Client.SendAsync($request).GetAwaiter().GetResult()
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $response.EnsureSuccessStatusCode()
        $envelope = $text | ConvertFrom-Json
        if (-not $envelope.success) { throw "$Method $Path failed: $($envelope.message)" }
        return $envelope.data
    } finally {
        $request.Dispose()
    }
}

function Invoke-StreamTurn {
    param(
        [Net.Http.HttpClient]$Client,
        [long]$SessionId,
        [pscustomobject]$Scenario
    )
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $foreground = Invoke-DemoApi $Client "POST" "/api/v1/aurora/foreground" @{
        sessionId = $SessionId
        message = $Scenario.prompt
        mode = $Scenario.mode
    }
    $foregroundVisible = -not $foreground.safetyBlocked -and
        -not [string]::IsNullOrWhiteSpace([string]$foreground.text)
    $firstAckMs = if ($foregroundVisible) { $watch.ElapsedMilliseconds } else { $null }
    $ackSource = [string]$foreground.source
    $ackKernelLatencyMs = $foreground.latencyMs
    $ack = [Text.StringBuilder]::new()
    if ($foregroundVisible) { [void]$ack.Append([string]$foreground.text) }
    $foregroundText = if ($foregroundVisible) { [string]$foreground.text } else { "" }
    $staged = Invoke-DemoApi $Client "POST" "/api/v1/aurora/stream-stage" @{
        sessionId = $SessionId
        message = $Scenario.prompt
        mode = $Scenario.mode
        foregroundAcknowledgementSent = $foregroundVisible
        foregroundAcknowledgementText = $foregroundText
        foregroundAcknowledgementSource = [string]$foreground.source
    }
    $request = [Net.Http.HttpRequestMessage]::new(
        [Net.Http.HttpMethod]::Get, "$Origin/api/v1/aurora/stream?token=$([Uri]::EscapeDataString($staged.token))")
    [void]$request.Headers.TryAddWithoutValidation("Accept", "text/event-stream")
    $response = $Client.SendAsync(
        $request, [Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    $response.EnsureSuccessStatusCode()

    $firstEventMs = $null
    $terminalMs = $null
    $inAck = $false
    $eventName = ""
    $dataLines = [System.Collections.Generic.List[string]]::new()
    $reader = [IO.StreamReader]::new(
        $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult(),
        [Text.Encoding]::UTF8)
    try {
        while (-not $reader.EndOfStream) {
            $line = $reader.ReadLine()
            if ($line -eq "") {
                $dataText = [string]::Join("`n", $dataLines)
                $payload = if ($dataText) {
                    try { $dataText | ConvertFrom-Json } catch { $null }
                } else { $null }
                if ($eventName -and $null -eq $firstEventMs) {
                    $firstEventMs = $watch.ElapsedMilliseconds
                }
                if ($eventName -eq "bubble.started" -and
                    ($payload.kind -eq "foreground-acknowledgement" -or $payload.order -eq 0)) {
                    if ($null -eq $firstAckMs) { $firstAckMs = $watch.ElapsedMilliseconds }
                    $ackSource = [string]$payload.source
                    $ackKernelLatencyMs = $payload.latencyMs
                    $inAck = $true
                } elseif ($eventName -eq "token" -and $inAck -and $null -ne $payload) {
                    [void]$ack.Append([string]$payload.content)
                } elseif ($eventName -eq "bubble.completed" -and $inAck -and $payload.order -eq 0) {
                    $inAck = $false
                } elseif ($eventName -in @("turn.completed", "turn.interrupted", "safety", "error", "done")) {
                    $terminalMs = $watch.ElapsedMilliseconds
                }
                $eventName = ""
                $dataLines.Clear()
                continue
            }
            if ($line.StartsWith("event:")) {
                $eventName = $line.Substring(6).Trim()
            } elseif ($line.StartsWith("data:")) {
                $dataLines.Add($line.Substring(5).TrimStart())
            }
        }
    } finally {
        $watch.Stop()
        $reader.Dispose()
        $response.Dispose()
        $request.Dispose()
    }

    return [pscustomobject]@{
        firstEventMs = $firstEventMs
        firstAcknowledgementMs = $firstAckMs
        acknowledgementSource = $ackSource
        acknowledgementKernelLatencyMs = $ackKernelLatencyMs
        terminalMs = if ($null -eq $terminalMs) { $watch.ElapsedMilliseconds } else { $terminalMs }
        acknowledgement = $ack.ToString()
        terminalObserved = $null -ne $terminalMs
    }
}

$results = [System.Collections.Generic.List[object]]::new()
foreach ($provider in $Providers) {
    for ($repeat = 1; $repeat -le $Repetitions; $repeat++) {
        $transport = New-DemoHttpClient
        $client = $transport.Client
        $password = "Aurora-stream-$([Guid]::NewGuid().ToString('N'))!"
        try {
            $suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            [void](Invoke-DemoApi $client "POST" "/api/v1/auth/register" @{
                username = "streambench$($provider.ToLower())$suffix"
                nickname = "Aurora Stream Eval"
                password = $password
            })
            foreach ($scenario in $scenarios) {
                $dialog = Invoke-DemoApi $client "POST" "/api/dialog/session/create" @{
                    title = "Aurora stream benchmark $($scenario.id)"
                    sessionType = "AURORA_CHAT"
                }
                [void](Invoke-DemoApi $client "PUT" "/api/aurora/session/$($dialog.id)/model" @{
                    provider = $provider
                })
                $turn = Invoke-StreamTurn $client $dialog.id $scenario
                $results.Add([pscustomobject]@{
                    provider = $provider
                    repetition = $repeat
                    scenario = $scenario.id
                    firstEventMs = $turn.firstEventMs
                    firstAcknowledgementMs = $turn.firstAcknowledgementMs
                    acknowledgementSource = $turn.acknowledgementSource
                    acknowledgementKernelLatencyMs = $turn.acknowledgementKernelLatencyMs
                    terminalMs = $turn.terminalMs
                    acknowledgement = $turn.acknowledgement
                    terminalObserved = $turn.terminalObserved
                })
            }
        } finally {
            try {
                [void](Invoke-DemoApi $client "DELETE" "/api/user/account" @{ password = $password })
            } catch {
                Write-Warning "Could not delete the temporary stream benchmark identity."
            }
            $client.Dispose()
            $transport.Handler.Dispose()
        }
    }
}

$results | ConvertTo-Json -Depth 5
