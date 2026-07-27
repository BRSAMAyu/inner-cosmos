[CmdletBinding()]
param(
    [string]$ExpectedContext = "kind-kubedeploy",
    [string]$Namespace = "inner-cosmos-w3"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$stateDir = Join-Path $root ".demo-runtime"
$stateFile = Join-Path $stateDir "live-showcase.json"

function Test-LocalPort {
    param([int]$Port)
    return Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -InformationLevel Quiet
}

function Start-Forward {
    param(
        [string]$Name,
        [string]$TargetNamespace,
        [string]$Resource,
        [int]$LocalPort,
        [int]$RemotePort
    )
    if (Test-LocalPort $LocalPort) {
        throw "Port $LocalPort for $Name is already in use. Run stop-live-showcase.ps1 or free the port."
    }
    $stdout = Join-Path $stateDir "$Name.stdout.log"
    $stderr = Join-Path $stateDir "$Name.stderr.log"
    $process = Start-Process -FilePath "kubectl.exe" -ArgumentList @(
        "-n", $TargetNamespace, "port-forward", $Resource,
        "$LocalPort`:$RemotePort", "--address=127.0.0.1"
    ) -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
      -WindowStyle Hidden -PassThru

    $deadline = (Get-Date).AddSeconds(15)
    do {
        if ($process.HasExited) {
            $detail = if (Test-Path -LiteralPath $stderr) {
                Get-Content -LiteralPath $stderr -Raw
            } else { "no stderr" }
            throw "$Name port-forward exited early: $detail"
        }
        Start-Sleep -Milliseconds 150
    } until ((Test-LocalPort $LocalPort) -or (Get-Date) -ge $deadline)
    if (-not (Test-LocalPort $LocalPort)) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw "$Name did not become reachable on 127.0.0.1:$LocalPort."
    }
    return [pscustomobject]@{
        name = $Name
        pid = $process.Id
        namespace = $TargetNamespace
        resource = $Resource
        localPort = $LocalPort
        remotePort = $RemotePort
    }
}

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl is required."
}
New-Item -ItemType Directory -Path $stateDir -Force | Out-Null
if (Test-Path -LiteralPath $stateFile) {
    throw "A live-showcase state file already exists. Run stop-live-showcase.ps1 first."
}

$context = (& kubectl config current-context).Trim()
if ($LASTEXITCODE -ne 0 -or $context -ne $ExpectedContext) {
    throw "Current context '$context' is not the expected disposable showcase '$ExpectedContext'."
}

& kubectl -n $Namespace rollout status deployment/inner-cosmos-api --timeout=60s | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Inner Cosmos API is not ready." }
& kubectl -n $Namespace rollout status deployment/inner-cosmos-otel-collector --timeout=60s | Out-Null
if ($LASTEXITCODE -ne 0) { throw "OTel Collector is not ready." }
& kubectl -n $Namespace rollout status deployment/inner-cosmos-jaeger --timeout=60s | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Jaeger is not ready." }
& kubectl -n observability rollout status deployment/prometheus --timeout=60s | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Prometheus is not ready." }
& kubectl -n observability rollout status deployment/grafana --timeout=60s | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Grafana is not ready." }

$forwards = [Collections.Generic.List[object]]::new()
try {
    $forwards.Add((Start-Forward "otel" $Namespace "svc/inner-cosmos-otel-collector" 4318 4318))
    $forwards.Add((Start-Forward "grafana" "observability" "svc/grafana" 3000 3000))
    $forwards.Add((Start-Forward "prometheus" "observability" "svc/prometheus" 9090 9090))
    $forwards.Add((Start-Forward "jaeger" $Namespace "svc/inner-cosmos-jaeger" 16686 16686))
    $forwards.Add((Start-Forward "kind-api" $Namespace "svc/inner-cosmos-api" 8081 8080))
    [pscustomobject]@{
        startedAt = (Get-Date).ToString("o")
        context = $context
        forwards = @($forwards)
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $stateFile -Encoding utf8
} catch {
    foreach ($forward in $forwards) {
        Stop-Process -Id $forward.pid -Force -ErrorAction SilentlyContinue
    }
    throw
}

Write-Host ""
Write-Host "LIVE_SHOWCASE_READY" -ForegroundColor Green
Write-Host "OTLP bridge:    http://127.0.0.1:4318/v1/traces"
Write-Host "H1 client:      http://127.0.0.1:8081/app/aurora/"
Write-Host "H1 dashboard:   http://127.0.0.1:3000/d/inner-cosmos-recovery/continuity-contract-c2b7-pod-recovery-live?orgId=1&refresh=2s"
Write-Host "KEDA dashboard: http://127.0.0.1:3000/d/inner-cosmos-events/work-pressure-contract-c2b7-outbox-and-keda?orgId=1&refresh=5s"
Write-Host "Jaeger:         http://127.0.0.1:16686/search"
Write-Host ""
Write-Host "Start the audience demo with:"
Write-Host "  .\scripts\demo\run-public-demo.ps1 -EnableLiveObservability -SkipApkBuild"
Write-Host "Stop all fixed forwards with:"
Write-Host "  .\scripts\demo\stop-live-showcase.ps1"
