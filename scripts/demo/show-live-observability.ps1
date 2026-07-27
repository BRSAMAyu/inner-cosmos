[CmdletBinding()]
param(
    [string]$AudienceService = "inner-cosmos-public-demo",
    [int]$Limit = 40
)

$ErrorActionPreference = "Stop"

foreach ($port in @(16686, 3000, 9090)) {
    if (-not (Test-NetConnection -ComputerName 127.0.0.1 -Port $port -InformationLevel Quiet)) {
        throw "Local observability port $port is not ready. Run start-live-showcase.ps1 first."
    }
}

$services = Invoke-RestMethod -Uri "http://127.0.0.1:16686/api/services" -TimeoutSec 10
$serviceNames = @($services.data)
if ($serviceNames -notcontains $AudienceService) {
    Write-Host "AUDIENCE_TRACE_STATUS=NO_TRACES_YET"
    Write-Host "Expected service: $AudienceService"
    Write-Host "Available services: $($serviceNames -join ', ')"
    Write-Host "The public demo must be started with -EnableLiveObservability before audience traffic."
    exit 2
}

$query = "http://127.0.0.1:16686/api/traces?service=" +
    [Uri]::EscapeDataString($AudienceService) + "&lookback=1h&limit=$Limit"
$response = Invoke-RestMethod -Uri $query -TimeoutSec 20
$traces = @($response.data)
if ($traces.Count -eq 0) {
    throw "Jaeger knows service $AudienceService but returned no traces from the last hour."
}

$spans = @($traces | ForEach-Object { $_.spans })
$operations = @($spans.operationName | Sort-Object -Unique)
$forbiddenKeys = "user.id|enduser.id|message.content|gen_ai.prompt|gen_ai.completion|db.statement|http.request.body|url.query"
$forbidden = @($spans.tags | Where-Object { $_.key -match $forbiddenKeys }).Count
if ($forbidden -ne 0) {
    throw "Privacy telemetry contract failed: found $forbidden forbidden trace tag(s)."
}
$latest = $traces | Sort-Object { ($_.spans | Measure-Object startTime -Maximum).Maximum } -Descending |
    Select-Object -First 1

Write-Host "AUDIENCE_TRACE_STATUS=READY" -ForegroundColor Green
Write-Host "service=$AudienceService traces_last_hour=$($traces.Count) spans=$($spans.Count)"
Write-Host "operations=$($operations -join ', ')"
Write-Host "forbidden_privacy_tags=$forbidden"
Write-Host "latest_trace_id=$($latest.traceID)"
Write-Host ""
Write-Host "Audience traces:"
Write-Host "  http://127.0.0.1:16686/search?service=$AudienceService"
Write-Host "Latest trace:"
Write-Host "  http://127.0.0.1:16686/trace/$($latest.traceID)"
Write-Host "Kubernetes overview:"
Write-Host "  http://127.0.0.1:3000/d/inner-cosmos-defense/cbaf604?orgId=1&refresh=5s"
