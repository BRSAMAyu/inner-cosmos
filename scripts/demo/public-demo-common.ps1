function Read-DemoInfo {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return @{} }
    $result = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding utf8) {
        if ($line -match "^(?<key>[a-z0-9_]+)=(?<value>.*)$") {
            $result[$Matches.key] = $Matches.value.Trim()
        }
    }
    return $result
}

function Test-CleanHttpsOrigin {
    param([Parameter(Mandatory = $true)][string]$Origin)
    try { $uri = [Uri]$Origin } catch { return $false }
    return $uri.IsAbsoluteUri -and $uri.Scheme -eq "https" -and $uri.IsDefaultPort -and
        $uri.AbsolutePath -eq "/" -and -not $uri.Query -and
        -not $uri.Fragment -and -not $uri.UserInfo
}

function Get-TunnelProcessEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$PidFile,
        [Parameter(Mandatory = $true)][string]$ExpectedExecutable,
        [ValidateSet("quick", "named")][string]$Mode,
        [int]$Port = 0
    )
    $evidence = [ordered]@{
        Valid = $false
        Pid = $null
        Reason = "pid-file-missing"
    }
    if (-not (Test-Path -LiteralPath $PidFile)) { return [pscustomobject]$evidence }
    $pidText = (Get-Content -LiteralPath $PidFile -Raw).Trim()
    if ($pidText -notmatch "^\d+$") {
        $evidence.Reason = "pid-invalid"
        return [pscustomobject]$evidence
    }
    $evidence.Pid = [int]$pidText
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$pidText" -ErrorAction SilentlyContinue
    if (-not $process) {
        $evidence.Reason = "process-not-running"
        return [pscustomobject]$evidence
    }
    $expectedPath = [IO.Path]::GetFullPath($ExpectedExecutable)
    $actualPath = if ($process.ExecutablePath) { [IO.Path]::GetFullPath($process.ExecutablePath) } else { "" }
    if (-not $actualPath.Equals($expectedPath, [StringComparison]::OrdinalIgnoreCase)) {
        $evidence.Reason = "pid-owned-by-different-executable"
        return [pscustomobject]$evidence
    }
    $command = [string]$process.CommandLine
    if ($command -notmatch "(?i)\btunnel\b") {
        $evidence.Reason = "unexpected-command"
        return [pscustomobject]$evidence
    }
    if ($Mode -eq "quick") {
        $quickMatch = [regex]::Match($command, "(?i)--url\s+[`"']?http://127\.0\.0\.1:(?<port>\d+)")
        if (-not $quickMatch.Success -or ($Port -gt 0 -and [int]$quickMatch.Groups["port"].Value -ne $Port)) {
            $evidence.Reason = "not-quick-tunnel-command"
            return [pscustomobject]$evidence
        }
    }
    if ($Mode -eq "named" -and $command -notmatch "(?i)\brun\b") {
        $evidence.Reason = "not-named-tunnel-command"
        return [pscustomobject]$evidence
    }
    $evidence.Valid = $true
    $evidence.Reason = "ok"
    return [pscustomobject]$evidence
}

function Test-PublicDemoHttp {
    param(
        [Parameter(Mandatory = $true)][string]$Origin,
        [int]$TimeoutSec = 12
    )
    if (-not (Test-CleanHttpsOrigin $Origin)) {
        return [pscustomobject]@{ Healthy = $false; StatusCode = 0; Reason = "origin-invalid" }
    }
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$($Origin.TrimEnd('/'))/actuator/health" `
            -TimeoutSec $TimeoutSec -MaximumRedirection 2
        $healthy = $response.StatusCode -eq 200 -and $response.Content -match '"status"\s*:\s*"UP"'
        return [pscustomobject]@{
            Healthy = $healthy
            StatusCode = [int]$response.StatusCode
            Reason = if ($healthy) { "ok" } else { "health-payload-not-up" }
        }
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        return [pscustomobject]@{ Healthy = $false; StatusCode = $status; Reason = $_.Exception.Message }
    }
}

function Start-DemoTunnel {
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][ValidateSet("quick", "named")][string]$Mode,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Stdout,
        [Parameter(Mandatory = $true)][string]$Stderr
    )
    if ($Mode -eq "quick") {
        return Start-Process -FilePath $Executable -ArgumentList @(
            "tunnel", "--no-autoupdate", "--url", "http://127.0.0.1:$Port"
        ) -RedirectStandardOutput $Stdout -RedirectStandardError $Stderr -WindowStyle Hidden -PassThru
    }
    if ([string]::IsNullOrWhiteSpace($env:CLOUDFLARED_TUNNEL_TOKEN)) {
        throw "Named Tunnel requires CLOUDFLARED_TUNNEL_TOKEN in the operator environment."
    }
    $previousToken = $env:TUNNEL_TOKEN
    try {
        # cloudflared reads TUNNEL_TOKEN. Keeping it out of ArgumentList prevents the
        # remotely-managed token from appearing in process listings and state files.
        $env:TUNNEL_TOKEN = $env:CLOUDFLARED_TUNNEL_TOKEN
        return Start-Process -FilePath $Executable -ArgumentList @(
            "tunnel", "--no-autoupdate", "run"
        ) -RedirectStandardOutput $Stdout -RedirectStandardError $Stderr -WindowStyle Hidden -PassThru
    } finally {
        $env:TUNNEL_TOKEN = $previousToken
    }
}
