[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Origin
)

$ErrorActionPreference = "Stop"
$Origin = $Origin.TrimEnd("/")
$password = "InnerCosmos!2026"
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

function ConvertFrom-Utf8Base64([string]$Value) {
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Value))
}

# Keep this script parseable under the Windows PowerShell 5.1 ANSI-default loader. User-visible
# Chinese fixtures are UTF-8 Base64 rather than raw non-ASCII source text.
$targets = @(
    (ConvertFrom-Utf8Base64 "5p6X5r6I55qE5Zue5aOw5YiG6Lqr"),
    (ConvertFrom-Utf8Base64 "5rK/5rKz57yT5oWi55Sf5rS755qE5Lq6"),
    (ConvertFrom-Utf8Base64 "5oqK6Ieq5bex5pS+5Zue54Wn5oqk6YeM55qE5Lq6")
)

$scenarios = @(
    [pscustomobject]@{
        id = "work-before-showing"
        prompt = ConvertFrom-Utf8Base64 "5oiR6Iqx5LqG5b6I5LmF5YGa5LiA5Liq5L2c5ZOB77yM5Y+v5piO5aSp6KaB5bGV56S65pe256qB54S26KeJ5b6X5a6D5b6I5beu44CC5LiN6KaB5rOb5rOb5a6J5oWw5oiR77yM5L2g5Lya5oCO5LmI5Zue5bqU77yf"
    },
    [pscustomobject]@{
        id = "one-honest-line"
        prompt = ConvertFrom-Utf8Base64 "5YWI5Yir57uZ5LiA5Liy5bu66K6u77yM5Lmf5LiN6KaB5pu/5oiR5YiG5p6Q44CC5Y+q55So5L2g6Ieq5bex55qE5pa55byP77yM6Lef5oiR6K+05LiA5Y+l546w5Zyo5YC85b6X5ZCs55qE55yf6K+d44CC"
    }
)

function Get-Csrf([Microsoft.PowerShell.Commands.WebRequestSession]$Session) {
    $web = Invoke-WebRequest -UseBasicParsing -Uri "$Origin/api/v1/auth/csrf" -Method Get -WebSession $Session
    $bytes = if ($web.RawContentStream) { $web.RawContentStream.ToArray() } else { [Text.Encoding]::UTF8.GetBytes($web.Content) }
    $response = ([Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json)
    if (-not $response.success) { throw "CSRF bootstrap failed: $($response.message)" }
    return $response.data
}

function Invoke-Envelope(
    [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
    [string]$Method,
    [string]$Path,
    [object]$Body
) {
    $headers = @{}
    if ($Method -notin @("GET", "HEAD", "OPTIONS")) {
        $csrf = Get-Csrf $Session
        $headers[$csrf.headerName] = $csrf.token
        if ($Path.StartsWith("/api/v1/")) {
            $headers["Idempotency-Key"] = [Guid]::NewGuid().ToString()
        }
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

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$username = "capsuleeval$($suffix.ToString().Substring(5))"
[void](Invoke-Envelope $session "POST" "/api/v1/auth/register" @{
    username = $username
    nickname = "Capsule Eval"
    password = $password
})

$rows = [System.Collections.Generic.List[object]]::new()
try {
    $plaza = @(Invoke-Envelope $session "GET" "/api/plaza/capsules")
    foreach ($target in $targets) {
        $capsule = @($plaza | Where-Object { $_.pseudonym -eq $target })
        if ($capsule.Count -ne 1) {
            throw "Expected exactly one public capsule named '$target', found $($capsule.Count)."
        }
        foreach ($scenario in $scenarios) {
            try {
                $persona = Invoke-Envelope $session "POST" "/api/v1/persona-chat/session/create" @{
                    capsuleId = [long]$capsule[0].id
                }
                $watch = [Diagnostics.Stopwatch]::StartNew()
                $reply = Invoke-Envelope $session "POST" "/api/v1/persona-chat/message" @{
                    sessionId = [long]$persona.id
                    message = $scenario.prompt
                }
                $watch.Stop()
                $health = Invoke-Envelope $session "GET" "/api/ai/health" $null
                $rows.Add([pscustomobject]@{
                    capsule = $target
                    scenario = $scenario.id
                    elapsedMs = $watch.ElapsedMilliseconds
                    reply = [string]$reply.textContent
                    boundaryNotice = [string]$reply.boundaryNotice
                    providerCallSucceeded = [bool]$health.lastSuccess
                    providerCallFallback = [bool]$health.lastFallbackUsed
                    providerCallError = $health.lastError
                    journeyError = $null
                })
            } catch {
                $rows.Add([pscustomobject]@{
                    capsule = $target
                    scenario = $scenario.id
                    elapsedMs = 0
                    reply = ""
                    boundaryNotice = ""
                    providerCallSucceeded = $false
                    providerCallFallback = $false
                    providerCallError = $null
                    journeyError = $_.Exception.Message
                })
            }
        }
    }
} finally {
    [void](Invoke-Envelope $session "DELETE" "/api/user/account" @{ password = $password })
}

$rows | ConvertTo-Json -Depth 6
