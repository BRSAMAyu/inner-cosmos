[CmdletBinding()]
param(
    [ValidateSet("deepseek", "glm", "gemini")]
    [string]$Provider = "deepseek",
    [int]$Port = 8080,
    [int]$MaxBuildWorkers = 1,
    [ValidateSet("quick", "named")]
    [string]$TunnelMode = "quick",
    [string]$PublicOrigin,
    [switch]$ReuseTunnel,
    [switch]$SkipVerification,
    [switch]$StrictVerification,
    [switch]$NoWatchdog,
    [switch]$EnableLiveObservability,
    [switch]$SkipApkBuild
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$web = Join-Path $root "web"
$stateDir = Join-Path $root ".demo-runtime"
$compose = Join-Path $root "deploy\compose\public-demo.yml"
$cloudflared = Join-Path $root "scripts\demo\bin\cloudflared.exe"
$tunnelPidFile = Join-Path $stateDir "cloudflared.pid"
$demoInfoFile = Join-Path $stateDir "demo-info.txt"
. (Join-Path $PSScriptRoot "public-demo-common.ps1")
$origin = $null
$tunnel = $null
$keyFiles = @(Get-ChildItem -LiteralPath $root -File |
    Where-Object { $_.Name.StartsWith("API", [StringComparison]::OrdinalIgnoreCase) -and $_.Extension -eq ".txt" } |
    Sort-Object Name |
    Select-Object -ExpandProperty FullName)

if ($TunnelMode -eq "named") {
    if ([string]::IsNullOrWhiteSpace($PublicOrigin) -or -not (Test-CleanHttpsOrigin $PublicOrigin)) {
        throw "Named Tunnel requires -PublicOrigin with a clean fixed HTTPS origin."
    }
    $originUri = [Uri]$PublicOrigin
    if ($originUri.Host.EndsWith(".trycloudflare.com", [StringComparison]::OrdinalIgnoreCase)) {
        throw "Named Tunnel cannot use a temporary trycloudflare.com hostname."
    }
    $origin = $originUri.GetLeftPart([UriPartial]::Authority)
} elseif (-not [string]::IsNullOrWhiteSpace($PublicOrigin)) {
    throw "-PublicOrigin is only valid with -TunnelMode named."
}

if (Test-Path $tunnelPidFile) {
    $existingEvidence = Get-TunnelProcessEvidence -PidFile $tunnelPidFile `
        -ExpectedExecutable $cloudflared -Mode $TunnelMode -Port $Port
    if ($existingEvidence.Valid) {
        if (-not $ReuseTunnel) {
            throw "A public demo tunnel is already running. Use -ReuseTunnel for an in-place rebuild, or stop-public-demo.ps1 first."
        }
        if (-not (Test-Path $demoInfoFile)) {
            throw "Cannot reuse the tunnel because demo-info.txt is missing."
        }
        $savedInfo = Get-Content -LiteralPath $demoInfoFile -Encoding utf8 |
            Where-Object { $_ -match "^origin=https://" } | Select-Object -First 1
        if (-not $savedInfo) { throw "Cannot reuse the tunnel because its public origin is unknown." }
        $savedOrigin = ($savedInfo -replace "^origin=", "").Trim()
        if ($TunnelMode -eq "named" -and $savedOrigin -ne $origin) {
            throw "The running Named Tunnel origin does not match -PublicOrigin."
        }
        $origin = $savedOrigin
    } elseif ($ReuseTunnel) {
        throw "Cannot reuse the tunnel: $($existingEvidence.Reason)."
    } else {
        Remove-Item -LiteralPath $tunnelPidFile -Force -ErrorAction SilentlyContinue
    }
} elseif ($ReuseTunnel) {
    throw "Cannot reuse the tunnel because cloudflared.pid is missing."
}

if (-not $ReuseTunnel -and -not (Test-Path $cloudflared)) {
    $cloudflaredDirectory = Split-Path -Parent $cloudflared
    New-Item -ItemType Directory -Force -Path $cloudflaredDirectory | Out-Null
    $download = "$cloudflared.download"
    try {
        Write-Host "Downloading cloudflared from the official Cloudflare GitHub release..."
        Invoke-WebRequest -UseBasicParsing `
            -Uri "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe" `
            -OutFile $download -TimeoutSec 180
        Move-Item -LiteralPath $download -Destination $cloudflared -Force
    } finally {
        Remove-Item -LiteralPath $download -Force -ErrorAction SilentlyContinue
    }
}
if ($keyFiles.Count -eq 0) {
    throw "Operator key file is missing (expected an API*.txt file at the repository root)."
}
if ($Port -lt 1024 -or $Port -gt 65535) { throw "Port must be between 1024 and 65535." }
& docker info --format "{{.ServerVersion}}" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Docker Desktop is not reachable." }

New-Item -ItemType Directory -Force -Path $stateDir | Out-Null
$stdout = Join-Path $stateDir "cloudflared.stdout.log"
$stderr = Join-Path $stateDir "cloudflared.stderr.log"
if (-not $ReuseTunnel) {
    # Invalidate shareable metadata before replacing a tunnel. The status command
    # must never present yesterday's URL or APK as live during a rebuild.
    Remove-Item -LiteralPath $demoInfoFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $tunnelPidFile -Force -ErrorAction SilentlyContinue
    Remove-Item $stdout, $stderr -Force -ErrorAction SilentlyContinue
    $tunnel = Start-DemoTunnel -Executable $cloudflared -Mode $TunnelMode -Port $Port `
        -Stdout $stdout -Stderr $stderr
    $tunnel.Id | Set-Content -Encoding ascii $tunnelPidFile

    if ($TunnelMode -eq "quick") {
        $deadline = (Get-Date).AddMinutes(2)
        do {
            Start-Sleep -Milliseconds 750
            $log = ((Get-Content $stdout, $stderr -Raw -ErrorAction SilentlyContinue) -join "`n")
            $match = [regex]::Match($log, "https://[a-z0-9-]+\.trycloudflare\.com")
            if ($match.Success) { $origin = $match.Value }
            if ($tunnel.HasExited) { throw "cloudflared exited before assigning a public URL. See $stderr" }
        } while (-not $origin -and (Get-Date) -lt $deadline)
        if (-not $origin) { throw "Timed out waiting for a Cloudflare Quick Tunnel URL." }
    } else {
        Start-Sleep -Seconds 2
        if ($tunnel.HasExited) { throw "Named Tunnel exited during startup. See $stderr" }
    }
}

try {
    if (-not $SkipApkBuild) {
        & (Join-Path $PSScriptRoot "build-demo-apk.ps1") -ServerOrigin $origin -MaxWorkers $MaxBuildWorkers
        if ($LASTEXITCODE -ne 0) { throw "Demo APK build failed." }
    } else {
        Write-Host "WEB_ONLY_MODE=APK_BUILD_SKIPPED"
    }

    # build-demo-apk.ps1 intentionally emits a native-shell bundle (basename "/")
    # before syncing it into Android. Do not put that bundle into the server image:
    # a browser visit would navigate to /aurora instead of /app/aurora/aurora and
    # any full reload (including switching Demo personas) would fall through to a
    # backend 404. The APK has already copied its assets, so restore the real web
    # bundle before Docker snapshots src/main/resources/static.
    Push-Location $web
    try {
        $previousDemoMode = $env:VITE_DEMO_MODE
        $env:VITE_DEMO_MODE = "true"
        & npm.cmd run build:classroom
        if ($LASTEXITCODE -ne 0) { throw "Public Demo web bundle failed." }
    } finally {
        $env:VITE_DEMO_MODE = $previousDemoMode
        Pop-Location
    }

    # Merge all ignored operator files so a small API.local.txt can safely override or extend the
    # historical documentation-heavy credential file without editing that large artifact.
    $keys = @($keyFiles | ForEach-Object { Get-Content -LiteralPath $_ -Encoding utf8 })
    $dashscopeLine = $keys | Where-Object { $_ -match "(?i)^\s*(dashscope|qwen)\s*:" } | Select-Object -First 1
    $glmLine = $keys | Where-Object { $_ -match "(?i)^\s*glm" } | Select-Object -First 1
    $minimaxLine = $keys | Where-Object { $_ -match "(?i)^\s*minimax" } | Select-Object -First 1
    $mimoLine = $keys | Where-Object { $_ -match "(?i)^\s*mimo" } | Select-Object -First 1
    $geminiLine = $keys | Where-Object { $_ -match "^\s*gemini\s*:" } | Select-Object -First 1
    $deepseekLine = $keys | Where-Object { $_ -match "(?i)deepseek.*apikey\s*:" } | Select-Object -First 1
    $dashscopeKey = if ($dashscopeLine) {
        ($dashscopeLine -replace "(?i)^\s*(dashscope|qwen)\s*:\s*", "").Trim().TrimEnd([char]0x3001)
    } else { "" }
    $glmKey = if ($glmLine -and $glmLine -match "([0-9a-fA-F]{32}\.[A-Za-z0-9_-]+)") {
        $Matches[1]
    } elseif ($glmLine) { ($glmLine -replace "(?i)^\s*glm\s*:\s*", "").Trim() } else { "" }
    $minimaxKey = if ($minimaxLine -and $minimaxLine -match "(sk-[A-Za-z0-9._-]+)") {
        $Matches[1]
    } else { "" }
    $mimoKey = if ($mimoLine -and $mimoLine -match "(tp-[A-Za-z0-9._-]+)") {
        $Matches[1]
    } else { "" }
    $geminiKey = if ($geminiLine) { ($geminiLine -replace "^\s*gemini\s*:\s*", "").Trim() } else { "" }
    $deepseekKey = if ($deepseekLine) { ($deepseekLine -replace "(?i)^.*apikey\s*:\s*", "").Trim() } else { "" }
    $chatKey = switch ($Provider) {
        "deepseek" { $deepseekKey }
        "glm" { $glmKey }
        "gemini" { $geminiKey }
    }
    if ([string]::IsNullOrWhiteSpace($chatKey)) { throw "No $Provider key was found in the local operator file." }
    if ([string]::IsNullOrWhiteSpace($dashscopeKey)) { throw "No DashScope/Qwen key was found for TTS." }

    function New-Secret {
        $bytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        return [Convert]::ToBase64String($bytes)
    }
    $runtimeEnvFile = Join-Path $stateDir "infrastructure.env"
    $runtimeEnv = if (Test-Path $runtimeEnvFile) {
        Get-Content -LiteralPath $runtimeEnvFile -Raw | ConvertFrom-StringData
    } else { @{} }
    $env:COMPOSE_PARALLEL_LIMIT = "1"
    $env:DEMO_PORT = "$Port"
    $env:SPRING_DATASOURCE_PASSWORD = if ($runtimeEnv.SPRING_DATASOURCE_PASSWORD) {
        $runtimeEnv.SPRING_DATASOURCE_PASSWORD
    } else { New-Secret }
    $env:REDIS_PASSWORD = if ($runtimeEnv.REDIS_PASSWORD) { $runtimeEnv.REDIS_PASSWORD } else { New-Secret }
    if (-not (Test-Path $runtimeEnvFile)) {
        @(
            "SPRING_DATASOURCE_PASSWORD=$($env:SPRING_DATASOURCE_PASSWORD)"
            "REDIS_PASSWORD=$($env:REDIS_PASSWORD)"
        ) | Set-Content -Encoding ascii $runtimeEnvFile
    }
    $env:LLM_PROVIDER = $Provider
    $env:LLM_API_KEY = $chatKey
    # The classroom Gemini profile keeps every Aurora temporal layer in the same provider family:
    # minimal-thinking Flash for visible latency, and high-thinking Flash for planning/review.
    # Missing optional credentials degrade to the selected primary provider without Mock.
    $env:DEEPSEEK_API_KEY = $deepseekKey
    $env:GEMINI_API_KEY = $geminiKey
    $env:GLM_API_KEY = $glmKey
    $env:MINIMAX_API_KEY = $minimaxKey
    $env:MIMO_API_KEY = $mimoKey
    $env:ASR_PROVIDER = "mimo"
    $env:LLM_REAL_PROVIDER_FAILOVER_ENABLED = "true"
    $env:LLM_FAILOVER_PROVIDERS = "gemini,deepseek,minimax,glm,mimo"
    $env:GEMINI_MODEL = "gemini-3.6-flash"
    $env:AURORA_FAST_MODEL = "gemini-3.6-flash"
    $env:AURORA_SPEAKER_MODEL = "gemini-3.6-flash"
    $env:AURORA_THINKER_MODEL = "gemini-3.6-flash"
    $env:AURORA_SPEAKER_THINKING_LEVEL = "minimal"
    $env:AURORA_SPEAKER_MAX_TOKENS = "2048"
    # Classroom closure intentionally disables provider embeddings. Lexical/theme retrieval is
    # deterministic and sufficient for the curated journey; vector rebuild calls added latency
    # and an unrelated external failure mode without a verified positive demo effect.
    $env:MEMORY_EMBEDDING_ENABLED = "false"
    $env:TTS_API_KEY = $dashscopeKey
    $env:TTS_WS_URL = "wss://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference"
    if ($EnableLiveObservability) {
        if (-not (Test-NetConnection -ComputerName 127.0.0.1 -Port 4318 -InformationLevel Quiet)) {
            throw "Live observability requires the loopback OTLP bridge on port 4318. Run start-live-showcase.ps1 first."
        }
        $env:OTLP_TRACING_ENABLED = "true"
        $env:OTLP_TRACING_ENDPOINT = "http://host.docker.internal:4318/v1/traces"
        $env:TRACING_SAMPLING_PROBABILITY = "1.0"
        $env:OTEL_SERVICE_NAME = "inner-cosmos-public-demo"
        $env:DEPLOYMENT_ENVIRONMENT = "classroom-public-demo"
    } else {
        $env:OTLP_TRACING_ENABLED = "false"
        $env:TRACING_SAMPLING_PROBABILITY = "0.10"
        $env:OTEL_SERVICE_NAME = "inner-cosmos-public-demo"
        $env:DEPLOYMENT_ENVIRONMENT = "classroom-public-demo"
    }

    & docker compose -p inner-cosmos-public-demo -f $compose up -d --build --wait
    if ($LASTEXITCODE -ne 0) { throw "Public demo compose startup failed." }

    $publicDeadline = (Get-Date).AddMinutes(2)
    do {
        $publicHealth = Test-PublicDemoHttp -Origin $origin -TimeoutSec 10
        if (-not $publicHealth.Healthy) { Start-Sleep -Seconds 2 }
    } while (-not $publicHealth.Healthy -and (Get-Date) -lt $publicDeadline)
    if (-not $publicHealth.Healthy) {
        throw "Tunnel origin did not reach an UP application: $($publicHealth.Reason)"
    }

    # The container image (and the APK, when requested) now owns the tunnel-specific bundle. Restore the
    # checked-in web output to the normal same-origin build so a random URL never
    # leaks into a later commit.
    Push-Location (Join-Path $root "web")
    try {
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw "Default web bundle restore failed." }
    } finally { Pop-Location }

    $verificationStatus = if ($SkipVerification) { "SKIPPED" } else { "PASS" }
    if (-not $SkipVerification) {
        try {
            & (Join-Path $PSScriptRoot "verify-public-demo.ps1") -Origin $origin
            if ($LASTEXITCODE -ne 0) { throw "Public demo journey verification failed." }
        } catch {
            $verificationStatus = "WARN"
            if ($StrictVerification) { throw }
            Write-Warning ("Public demo remains available, but automated verification reported: " + $_.Exception.Message)
        }
    }

    $demoInfo = @(
        "origin=$origin"
        "app=$origin/app/aurora/"
        "provider=$Provider"
        "tunnel_mode=$TunnelMode"
        "port=$Port"
        "verification=$verificationStatus"
        "live_observability=$([bool]$EnableLiveObservability)"
        "started_at=$((Get-Date).ToString("o"))"
    )
    if (-not $SkipApkBuild) {
        $apk = Join-Path $root "src\main\resources\static\downloads\inner-cosmos-demo.apk"
        $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
        $demoInfo += "apk=$origin/downloads/inner-cosmos-demo.apk"
        $demoInfo += "apk_sha256=$hash"
    }
    $demoInfo | Set-Content -Encoding utf8 (Join-Path $stateDir "demo-info.txt")

    if (-not $NoWatchdog) {
        $watchdogPidFile = Join-Path $stateDir "watchdog.pid"
        if (Test-Path -LiteralPath $watchdogPidFile) {
            $oldWatchdogPid = (Get-Content -LiteralPath $watchdogPidFile -Raw).Trim()
            if ($oldWatchdogPid -match "^\d+$") {
                Stop-Process -Id ([int]$oldWatchdogPid) -Force -ErrorAction SilentlyContinue
            }
        }
        $watchdogOut = Join-Path $stateDir "watchdog.stdout.log"
        $watchdogErr = Join-Path $stateDir "watchdog.stderr.log"
        $watchdogArgs = @(
            "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
            ('"' + (Join-Path $PSScriptRoot "watch-public-demo.ps1") + '"')
        )
        if ($TunnelMode -eq "named") { $watchdogArgs += "-RestartNamedTunnel" }
        $watchdog = Start-Process -FilePath "powershell.exe" -ArgumentList $watchdogArgs `
            -RedirectStandardOutput $watchdogOut -RedirectStandardError $watchdogErr `
            -WindowStyle Hidden -PassThru
        $watchdog.Id | Set-Content -Encoding ascii $watchdogPidFile
    }

    Write-Host ""
    Write-Host $(if ($verificationStatus -eq "PASS") { "PUBLIC_DEMO_READY" } else { "PUBLIC_DEMO_READY_WITH_$verificationStatus" })
    Write-Host "Landing: $origin/"
    Write-Host "Web App: $origin/app/aurora/"
    if (-not $SkipApkBuild) {
        Write-Host "Android: $origin/downloads/inner-cosmos-demo.apk"
    }
    Write-Host "Stop:    .\scripts\demo\stop-public-demo.ps1"
} catch {
    if ($tunnel) { Stop-Process -Id $tunnel.Id -Force -ErrorAction SilentlyContinue }
    Remove-Item -LiteralPath $tunnelPidFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $demoInfoFile -Force -ErrorAction SilentlyContinue
    throw
}
