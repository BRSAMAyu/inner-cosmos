[CmdletBinding()]
param(
    [ValidateSet("deepseek", "glm")]
    [string]$Provider = "deepseek",
    [int]$Port = 8080,
    [int]$MaxBuildWorkers = 1,
    [switch]$SkipVerification
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$stateDir = Join-Path $root ".demo-runtime"
$compose = Join-Path $root "deploy\compose\public-demo.yml"
$cloudflared = Join-Path $root "scripts\demo\bin\cloudflared.exe"
$tunnelPidFile = Join-Path $stateDir "cloudflared.pid"
$keyFile = Get-ChildItem -LiteralPath $root -File |
    Where-Object { $_.Name.StartsWith("API", [StringComparison]::OrdinalIgnoreCase) -and $_.Extension -eq ".txt" } |
    Select-Object -First 1 -ExpandProperty FullName

if (Test-Path $tunnelPidFile) {
    $existingPid = (Get-Content -LiteralPath $tunnelPidFile -Raw).Trim()
    if ($existingPid -match "^\d+$" -and (Get-Process -Id ([int]$existingPid) -ErrorAction SilentlyContinue)) {
        throw "A public demo tunnel is already running. Use status-public-demo.ps1 or stop-public-demo.ps1 first."
    }
    Remove-Item -LiteralPath $tunnelPidFile -Force -ErrorAction SilentlyContinue
}

if (-not (Test-Path $cloudflared)) {
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
if (-not $keyFile -or -not (Test-Path $keyFile)) {
    throw "Operator key file is missing (expected an API*.txt file at the repository root)."
}
if ($Port -lt 1024 -or $Port -gt 65535) { throw "Port must be between 1024 and 65535." }
& docker info --format "{{.ServerVersion}}" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Docker Desktop is not reachable." }

New-Item -ItemType Directory -Force -Path $stateDir | Out-Null
$stdout = Join-Path $stateDir "cloudflared.stdout.log"
$stderr = Join-Path $stateDir "cloudflared.stderr.log"
Remove-Item $stdout, $stderr -Force -ErrorAction SilentlyContinue
$tunnel = Start-Process -FilePath $cloudflared -ArgumentList @(
    "tunnel", "--no-autoupdate", "--url", "http://127.0.0.1:$Port"
) -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
$tunnel.Id | Set-Content -Encoding ascii $tunnelPidFile

$origin = $null
$deadline = (Get-Date).AddMinutes(2)
do {
    Start-Sleep -Milliseconds 750
    $log = ((Get-Content $stdout, $stderr -Raw -ErrorAction SilentlyContinue) -join "`n")
    $match = [regex]::Match($log, "https://[a-z0-9-]+\.trycloudflare\.com")
    if ($match.Success) { $origin = $match.Value }
    if ($tunnel.HasExited) { throw "cloudflared exited before assigning a public URL. See $stderr" }
} while (-not $origin -and (Get-Date) -lt $deadline)
if (-not $origin) { throw "Timed out waiting for a Cloudflare quick-tunnel URL." }

try {
    & (Join-Path $PSScriptRoot "build-demo-apk.ps1") -ServerOrigin $origin -MaxWorkers $MaxBuildWorkers
    if ($LASTEXITCODE -ne 0) { throw "Demo APK build failed." }

    $keys = Get-Content -LiteralPath $keyFile -Encoding utf8
    $qwenLine = $keys | Where-Object { $_ -match "^\s*qwen\s*:" } | Select-Object -First 1
    $glmLine = $keys | Where-Object { $_ -match "^\s*glm\s*:" } | Select-Object -First 1
    $deepseekLine = $keys | Where-Object { $_ -match "(?i)deepseek.*apikey\s*:" } | Select-Object -First 1
    $qwenKey = if ($qwenLine) { ($qwenLine -replace "^\s*qwen\s*:\s*", "").Trim() } else { "" }
    $glmKey = if ($glmLine) { ($glmLine -replace "^\s*glm\s*:\s*", "").Trim() } else { "" }
    $deepseekKey = if ($deepseekLine) { ($deepseekLine -replace "(?i)^.*apikey\s*:\s*", "").Trim() } else { "" }
    $chatKey = if ($Provider -eq "deepseek") { $deepseekKey } else { $glmKey }
    if ([string]::IsNullOrWhiteSpace($chatKey)) { throw "No $Provider key was found in the local operator file." }
    if ([string]::IsNullOrWhiteSpace($qwenKey)) { throw "No Qwen key was found for embedding/TTS." }

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
    $env:DEEPSEEK_API_KEY = $deepseekKey
    $env:GLM_API_KEY = $glmKey
    $env:MEMORY_EMBEDDING_API_KEY = $qwenKey
    $env:MEMORY_EMBEDDING_BASE_URL = "https://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"
    $env:MEMORY_EMBEDDING_MODEL = "text-embedding-v4"
    $env:TTS_API_KEY = $qwenKey
    $env:TTS_WS_URL = "wss://llm-errus8cw2pf66bx9.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference"

    & docker compose -p inner-cosmos-public-demo -f $compose up -d --build --wait
    if ($LASTEXITCODE -ne 0) { throw "Public demo compose startup failed." }

    # The container image and APK now own the tunnel-specific bundle. Restore the
    # checked-in web output to the normal same-origin build so a random URL never
    # leaks into a later commit.
    Push-Location (Join-Path $root "web")
    try {
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw "Default web bundle restore failed." }
    } finally { Pop-Location }

    if (-not $SkipVerification) {
        & (Join-Path $PSScriptRoot "verify-public-demo.ps1") -Origin $origin
        if ($LASTEXITCODE -ne 0) { throw "Public demo journey verification failed." }
    }

    $apk = Join-Path $root "src\main\resources\static\downloads\inner-cosmos-demo.apk"
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    @(
        "origin=$origin"
        "app=$origin/app/aurora/"
        "apk=$origin/downloads/inner-cosmos-demo.apk"
        "apk_sha256=$hash"
        "provider=$Provider"
        "started_at=$((Get-Date).ToString("o"))"
    ) | Set-Content -Encoding utf8 (Join-Path $stateDir "demo-info.txt")

    Write-Host ""
    Write-Host "PUBLIC_DEMO_READY"
    Write-Host "Landing: $origin/"
    Write-Host "Web App: $origin/app/aurora/"
    Write-Host "Android: $origin/downloads/inner-cosmos-demo.apk"
    Write-Host "Stop:    .\scripts\demo\stop-public-demo.ps1"
} catch {
    Stop-Process -Id $tunnel.Id -Force -ErrorAction SilentlyContinue
    throw
}
