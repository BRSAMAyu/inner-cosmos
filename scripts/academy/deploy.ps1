[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Namespace = $env:K8S_NAMESPACE,
    [string]$StorageNode = $env:ACADEMY_STORAGE_NODE,
    [string]$Image = $env:INNER_COSMOS_IMAGE,
    [string]$GatewayClass = $env:ACADEMY_GATEWAY_CLASS,
    [string]$EdgeDnsName = $(if ($env:ACADEMY_EDGE_DNS) { $env:ACADEMY_EDGE_DNS } else { "inner-cosmos.local" }),
    [switch]$SkipPreflight
)

$ErrorActionPreference = "Stop"
$requiredEnvironment = @(
    "SPRING_DATASOURCE_PASSWORD", "REDIS_PASSWORD", "LLM_PROVIDER", "LLM_API_KEY",
    "OIDC_ISSUER_URI", "OIDC_JWK_SET_URI", "OIDC_AUDIENCE", "OIDC_AUTHORIZATION_URI",
    "OIDC_TOKEN_URI", "OIDC_MOBILE_CLIENT_ID", "OIDC_MOBILE_REDIRECT_URI",
    "CORS_ALLOWED_ORIGINS"
)

if ([string]::IsNullOrWhiteSpace($Namespace) -or $Namespace -notmatch '^[a-z0-9]([-a-z0-9]*[a-z0-9])?$') {
    throw "K8S_NAMESPACE must be a valid non-empty DNS label."
}
if ([string]::IsNullOrWhiteSpace($StorageNode)) { throw "ACADEMY_STORAGE_NODE must be discovered for this Lab session." }
if ([string]::IsNullOrWhiteSpace($GatewayClass)) { throw "ACADEMY_GATEWAY_CLASS must come from the current preflight." }
if ([string]::IsNullOrWhiteSpace($Image) -or $Image -notmatch '@sha256:[a-f0-9]{64}$') {
    throw "INNER_COSMOS_IMAGE must be an immutable registry image digest."
}
foreach ($name in $requiredEnvironment) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name must be injected into the current operator environment."
    }
}
if (-not $SkipPreflight) {
    & (Join-Path $PSScriptRoot "preflight.ps1") -Mode Live
    if ($LASTEXITCODE -ne 0) { throw "Academy capability preflight failed closed." }
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("inner-cosmos-academy-" + [Guid]::NewGuid().ToString("N"))
$tempBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$resolvedTempRoot = [System.IO.Path]::GetFullPath($tempRoot)
$originalOpenSslConf = $env:OPENSSL_CONF
if (-not $resolvedTempRoot.StartsWith($tempBase, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to create deployment material outside the system temporary directory."
}
New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null

if ([string]::IsNullOrWhiteSpace($env:OPENSSL_CONF) -or -not (Test-Path -LiteralPath $env:OPENSSL_CONF)) {
    $openSslCandidates = @(
        "C:\Program Files\Git\mingw64\etc\ssl\openssl.cnf",
        "C:\Program Files\Git\usr\ssl\openssl.cnf"
    )
    $detectedOpenSslConf = $openSslCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $detectedOpenSslConf) {
        throw "OpenSSL configuration could not be located; no certificate material was generated."
    }
    $env:OPENSSL_CONF = $detectedOpenSslConf
}

function Invoke-Checked {
    param([Parameter(Mandatory)][string]$Command, [string[]]$Arguments)
    & $Command @Arguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "$Command failed with exit code $LASTEXITCODE." }
}

function New-ServiceCertificate {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string[]]$DnsNames)
    $dir = Join-Path $tempRoot $Name
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $san = ($DnsNames | ForEach-Object { "DNS:$_" }) -join ','
    Invoke-Checked openssl @("req", "-newkey", "rsa:2048", "-nodes", "-keyout", (Join-Path $dir "server.key"),
        "-out", (Join-Path $dir "server.csr"), "-subj", "/CN=$($DnsNames[0])", "-addext", "subjectAltName=$san")
    Invoke-Checked openssl @("x509", "-req", "-in", (Join-Path $dir "server.csr"), "-CA", (Join-Path $tempRoot "ca.crt"),
        "-CAkey", (Join-Path $tempRoot "ca.key"), "-CAcreateserial", "-out", (Join-Path $dir "server.crt"),
        "-days", "2", "-sha256", "-copy_extensions", "copy")
    Copy-Item -LiteralPath (Join-Path $tempRoot "ca.crt") -Destination (Join-Path $dir "ca.crt")
    return $dir
}

function Apply-GeneratedSecret {
    param([Parameter(Mandatory)][string[]]$CreateArguments)
    $yaml = @(& kubectl @CreateArguments --dry-run=client -o yaml 2>$null)
    if ($LASTEXITCODE -ne 0) { throw "Unable to generate the short-lived Kubernetes Secret." }
    $yaml | & kubectl -n $Namespace apply -f - | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Unable to apply the short-lived Kubernetes Secret." }
}

try {
    if (-not $PSCmdlet.ShouldProcess("namespace $Namespace", "Deploy Academy profile")) { return }

    Invoke-Checked kubectl @("create", "namespace", $Namespace, "--dry-run=client", "-o", "yaml")
    $namespaceYaml = @(& kubectl create namespace $Namespace --dry-run=client -o yaml)
    $namespaceYaml | & kubectl apply -f - | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Unable to create or reconcile the Academy namespace." }

    Invoke-Checked kubectl @("label", "node", $StorageNode, "inner-cosmos.academy/storage=true", "--overwrite")

    Invoke-Checked openssl @("req", "-x509", "-newkey", "rsa:2048", "-sha256", "-days", "2", "-nodes",
        "-subj", "/CN=inner-cosmos-academy-ca", "-keyout", (Join-Path $tempRoot "ca.key"), "-out", (Join-Path $tempRoot "ca.crt"))
    $postgresDir = New-ServiceCertificate "postgres" @("inner-cosmos-postgres", "inner-cosmos-postgres.$Namespace", "inner-cosmos-postgres.$Namespace.svc")
    $redisDir = New-ServiceCertificate "redis" @("inner-cosmos-redis", "inner-cosmos-redis.$Namespace", "inner-cosmos-redis.$Namespace.svc")
    $edgeDir = New-ServiceCertificate "edge" @($EdgeDnsName)

    $envFile = Join-Path $tempRoot "runtime.env"
    @(
        "SPRING_DATASOURCE_PASSWORD=$env:SPRING_DATASOURCE_PASSWORD"
        "REDIS_PASSWORD=$env:REDIS_PASSWORD"
        "LLM_PROVIDER=$env:LLM_PROVIDER"
        "LLM_API_KEY=$env:LLM_API_KEY"
        "OIDC_ISSUER_URI=$env:OIDC_ISSUER_URI"
        "OIDC_JWK_SET_URI=$env:OIDC_JWK_SET_URI"
        "OIDC_AUDIENCE=$env:OIDC_AUDIENCE"
        "OIDC_AUTHORIZATION_URI=$env:OIDC_AUTHORIZATION_URI"
        "OIDC_TOKEN_URI=$env:OIDC_TOKEN_URI"
        "OIDC_MOBILE_CLIENT_ID=$env:OIDC_MOBILE_CLIENT_ID"
        "OIDC_MOBILE_REDIRECT_URI=$env:OIDC_MOBILE_REDIRECT_URI"
        "CORS_ALLOWED_ORIGINS=$env:CORS_ALLOWED_ORIGINS"
    ) | Set-Content -Encoding utf8 -Path $envFile

    Apply-GeneratedSecret @("create", "secret", "generic", "inner-cosmos-runtime", "--from-env-file=$envFile")
    Apply-GeneratedSecret @("create", "secret", "generic", "inner-cosmos-postgres-server-tls",
        "--from-file=server.key=$(Join-Path $postgresDir 'server.key')", "--from-file=server.crt=$(Join-Path $postgresDir 'server.crt')", "--from-file=ca.crt=$(Join-Path $postgresDir 'ca.crt')")
    Apply-GeneratedSecret @("create", "secret", "generic", "inner-cosmos-postgres-client-ca", "--from-file=ca.crt=$(Join-Path $postgresDir 'ca.crt')")
    Apply-GeneratedSecret @("create", "secret", "generic", "inner-cosmos-redis-server-tls",
        "--from-file=server.key=$(Join-Path $redisDir 'server.key')", "--from-file=server.crt=$(Join-Path $redisDir 'server.crt')", "--from-file=ca.crt=$(Join-Path $redisDir 'ca.crt')")
    Apply-GeneratedSecret @("create", "secret", "generic", "inner-cosmos-redis-client-ca", "--from-file=ca.crt=$(Join-Path $redisDir 'ca.crt')")
    Apply-GeneratedSecret @("create", "secret", "tls", "inner-cosmos-edge-tls", "--cert=$(Join-Path $edgeDir 'server.crt')", "--key=$(Join-Path $edgeDir 'server.key')")

    $rendered = @(& kubectl kustomize (Join-Path $PSScriptRoot "../../deploy/k8s/overlays/academy-eks") 2>$null) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Kustomize render failed." }
    $rendered = $rendered.Replace("image: inner-cosmos:academy-placeholder", "image: $Image")
    $rendered = $rendered.Replace("gatewayClassName: academy-runtime-discovery-required", "gatewayClassName: $GatewayClass")
    if ($rendered -match 'academy-placeholder|academy-runtime-discovery-required') { throw "Runtime Kustomize placeholders were not fully resolved." }
    $rendered | & kubectl -n $Namespace apply -f - | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Academy manifest apply failed." }

    Invoke-Checked kubectl @("-n", $Namespace, "rollout", "status", "statefulset/inner-cosmos-postgres", "--timeout=240s")
    Invoke-Checked kubectl @("-n", $Namespace, "rollout", "status", "deployment/inner-cosmos-redis", "--timeout=180s")
    Invoke-Checked kubectl @("-n", $Namespace, "wait", "--for=condition=complete", "job/inner-cosmos-migration", "--timeout=300s")
    Invoke-Checked kubectl @("-n", $Namespace, "rollout", "status", "deployment/inner-cosmos-api", "--timeout=300s")
    Invoke-Checked kubectl @("-n", $Namespace, "rollout", "status", "deployment/inner-cosmos-worker", "--timeout=300s")
    Invoke-Checked kubectl @("-n", $Namespace, "rollout", "status", "deployment/inner-cosmos-scheduler", "--timeout=300s")

    [pscustomobject]@{
        Status = "PASS"
        Profile = "academy-eks"
        Namespace = $Namespace
        ImageDigestSupplied = $true
        HumanAwsCredentialsInjectedIntoPods = $false
        SqsDependency = $false
        DynamicStorageDependency = $false
        Note = "Check Gateway and HTTPRoute Accepted/ResolvedRefs status before claiming external reachability."
    }
} finally {
    if ($resolvedTempRoot.StartsWith($tempBase, [System.StringComparison]::OrdinalIgnoreCase) `
            -and (Test-Path -LiteralPath $resolvedTempRoot)) {
        Remove-Item -Recurse -Force -LiteralPath $resolvedTempRoot
    }
    if ([string]::IsNullOrWhiteSpace($originalOpenSslConf)) {
        Remove-Item Env:OPENSSL_CONF -ErrorAction SilentlyContinue
    } else {
        $env:OPENSSL_CONF = $originalOpenSslConf
    }
}
