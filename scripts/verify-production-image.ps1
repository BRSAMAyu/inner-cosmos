[CmdletBinding()]
param(
    [string]$Image = "inner-cosmos:m1-supply",
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

$postgresImage = "pgvector/pgvector:0.8.1-pg16@sha256:33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337"
$redisImage = "redis:7.4.2-alpine@sha256:02419de7eddf55aa5bcf49efb74e88fa8d931b4d77c07eff8a6b2144472b6952"
$runId = [Guid]::NewGuid().ToString("N").Substring(0, 12)
$network = "inner-cosmos-prod-smoke-$runId"
$postgresName = "inner-cosmos-postgres-$runId"
$redisName = "inner-cosmos-redis-$runId"
$appName = "inner-cosmos-app-$runId"
$database = "inner_cosmos"
$databaseUser = "inner_cosmos"
$databasePassword = [Guid]::NewGuid().ToString("N")
$redisPassword = [Guid]::NewGuid().ToString("N")
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) "inner-cosmos-prod-smoke-$runId"
$resolvedTempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$resolvedTempRoot = [IO.Path]::GetFullPath($tempRoot)

if (-not $resolvedTempRoot.StartsWith($resolvedTempBase, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to use a temporary path outside the system temp directory."
}

function Invoke-Docker {
    param([string[]]$DockerArguments)
    & docker @DockerArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed with exit code $LASTEXITCODE."
    }
}

function Wait-Postgres {
    param([DateTimeOffset]$Deadline)
    while ([DateTimeOffset]::UtcNow -lt $Deadline) {
        $savedPreference = $ErrorActionPreference
        $ErrorActionPreference = "SilentlyContinue"
        & docker exec $postgresName pg_isready -U $databaseUser -d $database 2>$null | Out-Null
        $ready = $LASTEXITCODE -eq 0
        $ErrorActionPreference = $savedPreference
        if ($ready) { return }
        Start-Sleep -Seconds 2
    }
    & docker logs $postgresName --tail 120
    throw "PostgreSQL did not become ready before the timeout."
}

function Wait-Redis {
    param([DateTimeOffset]$Deadline)
    while ([DateTimeOffset]::UtcNow -lt $Deadline) {
        $savedPreference = $ErrorActionPreference
        $ErrorActionPreference = "SilentlyContinue"
        $reply = & docker exec $redisName redis-cli --tls --cacert /certs/redis-ca.crt `
            --no-auth-warning -a $redisPassword ping 2>$null
        $ready = $LASTEXITCODE -eq 0 -and $reply -eq "PONG"
        $ErrorActionPreference = $savedPreference
        if ($ready) { return }
        Start-Sleep -Seconds 2
    }
    & docker logs $redisName --tail 120
    throw "Redis did not become ready before the timeout."
}

New-Item -ItemType Directory -Path $resolvedTempRoot | Out-Null

try {
    Invoke-Docker -DockerArguments @("network", "create", $network) | Out-Null
    Invoke-Docker -DockerArguments @(
        "run", "-d", "--name", $postgresName, "--network", $network,
        "-e", "POSTGRES_DB=$database",
        "-e", "POSTGRES_USER=$databaseUser",
        "-e", "POSTGRES_PASSWORD=$databasePassword",
        $postgresImage
    ) | Out-Null

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    Wait-Postgres -Deadline $deadline

    $certificateScript = @"
set -eu
cd /var/lib/postgresql/data
openssl req -x509 -newkey rsa:2048 -sha256 -days 1 -nodes -subj '/CN=inner-cosmos-smoke-ca' -keyout ca.key -out ca.crt >/dev/null 2>&1
openssl req -newkey rsa:2048 -sha256 -nodes -subj '/CN=$postgresName' -addext 'subjectAltName=DNS:$postgresName' -keyout server.key -out server.csr >/dev/null 2>&1
openssl x509 -req -sha256 -days 1 -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -copy_extensions copy -out server.crt >/dev/null 2>&1
openssl req -newkey rsa:2048 -sha256 -nodes -subj '/CN=$redisName' -addext 'subjectAltName=DNS:$redisName' -keyout redis-server.key -out redis-server.csr >/dev/null 2>&1
openssl x509 -req -sha256 -days 1 -in redis-server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -copy_extensions copy -out redis-server.crt >/dev/null 2>&1
chmod 600 server.key ca.key
chmod 644 redis-server.key redis-server.crt ca.crt
"@
    Invoke-Docker -DockerArguments @("exec", "-u", "postgres", $postgresName, "sh", "-c", $certificateScript)
    Invoke-Docker -DockerArguments @("exec", "-u", "postgres", $postgresName, "psql", "-U", $databaseUser, "-d", $database, "-v", "ON_ERROR_STOP=1", "-c", "ALTER SYSTEM SET ssl = 'on'")
    Invoke-Docker -DockerArguments @("exec", "-u", "postgres", $postgresName, "psql", "-U", $databaseUser, "-d", $database, "-v", "ON_ERROR_STOP=1", "-c", "ALTER SYSTEM SET ssl_cert_file = 'server.crt'")
    Invoke-Docker -DockerArguments @("exec", "-u", "postgres", $postgresName, "psql", "-U", $databaseUser, "-d", $database, "-v", "ON_ERROR_STOP=1", "-c", "ALTER SYSTEM SET ssl_key_file = 'server.key'")
    Invoke-Docker -DockerArguments @("cp", "${postgresName}:/var/lib/postgresql/data/ca.crt", (Join-Path $resolvedTempRoot "postgres-ca.crt"))
    Invoke-Docker -DockerArguments @("cp", "${postgresName}:/var/lib/postgresql/data/ca.crt", (Join-Path $resolvedTempRoot "redis-ca.crt"))
    Invoke-Docker -DockerArguments @("cp", "${postgresName}:/var/lib/postgresql/data/redis-server.crt", (Join-Path $resolvedTempRoot "redis-server.crt"))
    Invoke-Docker -DockerArguments @("cp", "${postgresName}:/var/lib/postgresql/data/redis-server.key", (Join-Path $resolvedTempRoot "redis-server.key"))
    Invoke-Docker -DockerArguments @("restart", $postgresName) | Out-Null
    Wait-Postgres -Deadline $deadline

    Invoke-Docker -DockerArguments @(
        "run", "-d", "--name", $redisName, "--network", $network,
        "-v", "${resolvedTempRoot}:/certs:ro",
        $redisImage,
        "redis-server",
        "--port", "0",
        "--tls-port", "6379",
        "--tls-cert-file", "/certs/redis-server.crt",
        "--tls-key-file", "/certs/redis-server.key",
        "--tls-ca-cert-file", "/certs/redis-ca.crt",
        "--tls-auth-clients", "no",
        "--requirepass", $redisPassword
    ) | Out-Null
    Wait-Redis -Deadline $deadline

    $caPath = Join-Path $resolvedTempRoot "postgres-ca.crt"
    $redisCaPath = Join-Path $resolvedTempRoot "redis-ca.crt"
    $jdbcUrl = "jdbc:postgresql://${postgresName}:5432/${database}?sslmode=verify-full&sslrootcert=/run/secrets/postgres-ca.crt"
    Invoke-Docker -DockerArguments @(
        "run", "-d", "--name", $appName, "--network", $network,
        "-p", "127.0.0.1::8080",
        "-v", "${caPath}:/run/secrets/postgres-ca.crt:ro",
        "-v", "${redisCaPath}:/run/secrets/redis-ca.crt:ro",
        "-e", "SPRING_PROFILES_ACTIVE=prod",
        "-e", "SPRING_DATASOURCE_URL=$jdbcUrl",
        "-e", "SPRING_DATASOURCE_USERNAME=$databaseUser",
        "-e", "SPRING_DATASOURCE_PASSWORD=$databasePassword",
        "-e", "REDIS_SESSION_ENABLED=true",
        "-e", "REDIS_HOST=$redisName",
        "-e", "REDIS_PORT=6379",
        "-e", "REDIS_PASSWORD=$redisPassword",
        "-e", "REDIS_SSL_ENABLED=true",
        "-e", "SPRING_DATA_REDIS_SSL_BUNDLE=redis",
        "-e", "SPRING_SSL_BUNDLE_PEM_REDIS_TRUSTSTORE_CERTIFICATE=file:/run/secrets/redis-ca.crt",
        "-e", "LLM_MODE=prod",
        "-e", "LLM_PROVIDER=glm",
        "-e", "LLM_API_KEY=prod-smoke-$runId",
        "-e", "LLM_ALLOW_FALLBACK=false",
        "-e", "SEED_ENABLED=false",
        "-e", "COOKIE_SECURE=true",
        "-e", "CORS_ALLOWED_ORIGINS=https://smoke.inner-cosmos.invalid",
        "-e", "OIDC_ENABLED=true",
        "-e", "OIDC_ISSUER_URI=https://identity.inner-cosmos.invalid/",
        "-e", "OIDC_JWK_SET_URI=https://identity.inner-cosmos.invalid/jwks",
        "-e", "OIDC_AUDIENCE=inner-cosmos-api",
        "-e", "OIDC_AUTHORIZATION_URI=https://identity.inner-cosmos.invalid/authorize",
        "-e", "OIDC_TOKEN_URI=https://identity.inner-cosmos.invalid/token",
        "-e", "OIDC_MOBILE_CLIENT_ID=inner-cosmos-mobile",
        "-e", "OIDC_MOBILE_REDIRECT_URI=innercosmos://auth/callback",
        $Image
    ) | Out-Null

    $portLine = (& docker port $appName "8080/tcp" | Select-Object -First 1)
    if (-not $portLine) { throw "Docker did not publish the application port." }
    $hostPort = [int]($portLine -replace '^.*:', '')
    $health = $null
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$hostPort/actuator/health" -TimeoutSec 3
            if ($health.status -eq "UP") { break }
        } catch {
            # Expected while Flyway and the application are starting.
        }
        Start-Sleep -Seconds 2
    }
    if ($null -eq $health -or $health.status -ne "UP") {
        & docker logs $appName --tail 180
        throw "Production-profile application health did not become UP before the timeout."
    }

    # The public CSRF bootstrap materializes an HttpSession. Its key must be in
    # external Redis, proving the production request path is not using pod memory.
    $csrfResponse = Invoke-WebRequest -UseBasicParsing `
        -Uri "http://127.0.0.1:$hostPort/api/auth/csrf" -TimeoutSec 5
    $csrfPayload = $csrfResponse.Content | ConvertFrom-Json
    $csrfToken = $csrfPayload.data.token
    $csrfHeaderName = $csrfPayload.data.headerName
    $setCookie = [string]$csrfResponse.Headers["Set-Cookie"]
    if (-not $csrfToken -or $setCookie -notmatch '(?:^|,\s*)SESSION=([^;]+)') {
        throw "CSRF bootstrap did not return a token and Redis session cookie."
    }
    $sessionCookie = $Matches[1]
    $sessionKeys = @(& docker exec $redisName redis-cli --tls --cacert /certs/redis-ca.crt `
        --no-auth-warning -a $redisPassword --scan --pattern 'inner-cosmos:session:sessions:*')
    if ($LASTEXITCODE -ne 0) { throw "Unable to inspect Redis session keys." }
    $sessionKeyCount = @($sessionKeys | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
    if ($sessionKeyCount -lt 1) { throw "Production request did not materialize a Redis-backed session." }

    # A valid session-bound CSRF token lets the login request reach the security-chain
    # limiter. The failed credential check is intentional; the Redis key proves the
    # production filter path is shared and atomic rather than pod-local.
    $loginStatus = & curl.exe --silent --show-error --output NUL --write-out "%{http_code}" `
        --request POST "http://127.0.0.1:$hostPort/api/auth/login" `
        --header "${csrfHeaderName}: $csrfToken" `
        --header "Cookie: SESSION=$sessionCookie" `
        --header "Content-Type: application/json" `
        --data '{"username":"missing-user","password":"missing-password"}'
    if ($LASTEXITCODE -ne 0) { throw "Unable to execute production login rate-limit probe." }
    $rateLimitKeys = @(& docker exec $redisName redis-cli --tls --cacert /certs/redis-ca.crt `
        --no-auth-warning -a $redisPassword --scan --pattern 'inner-cosmos:rate-limit:v1:login:*')
    if ($LASTEXITCODE -ne 0) { throw "Unable to inspect Redis rate-limit keys." }
    $rateLimitKeyCount = @($rateLimitKeys | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
    if ($rateLimitKeyCount -lt 1) {
        $observedKeys = @(& docker exec $redisName redis-cli --tls --cacert /certs/redis-ca.crt `
            --no-auth-warning -a $redisPassword --scan)
        throw "Production request (HTTP $loginStatus) did not materialize a Redis-backed rate-limit bucket. Observed Redis keys: $($observedKeys -join ', ')"
    }

    # Fixed-delay jobs execute once after startup and retain short minimum leases.
    # Observe a real key to prove the production advisor uses the authenticated,
    # TLS-only Redis connection rather than merely loading configuration classes.
    $schedulerLeaseKeys = @()
    $leaseDeadline = [DateTimeOffset]::UtcNow.AddSeconds(20)
    while ([DateTimeOffset]::UtcNow -lt $leaseDeadline) {
        $schedulerLeaseKeys = @(& docker exec $redisName redis-cli --tls --cacert /certs/redis-ca.crt `
            --no-auth-warning -a $redisPassword --scan --pattern '*inner-cosmos-scheduler-v1*')
        if ($LASTEXITCODE -ne 0) { throw "Unable to inspect Redis scheduler lease keys." }
        if (@($schedulerLeaseKeys | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) { break }
        Start-Sleep -Seconds 1
    }
    $schedulerLeaseKeyCount = @($schedulerLeaseKeys |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
    if ($schedulerLeaseKeyCount -lt 1) {
        throw "Production startup did not materialize a Redis-backed scheduler lease."
    }

    $tableCount = (& docker exec -e "PGPASSWORD=$databasePassword" $postgresName psql `
        -U $databaseUser -d $database -Atc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'tb_%'").Trim()
    $seededUsers = (& docker exec -e "PGPASSWORD=$databasePassword" $postgresName psql `
        -U $databaseUser -d $database -Atc "SELECT COUNT(*) FROM tb_user").Trim()
    $flywayVersion = (& docker exec -e "PGPASSWORD=$databasePassword" $postgresName psql `
        -U $databaseUser -d $database -Atc 'SELECT MAX(version) FROM flyway_schema_history WHERE success').Trim()
    $runtimeUser = (& docker inspect $appName --format '{{.Config.User}}').Trim()

    if ([int]$tableCount -ne 61) { throw "Production Flyway schema did not create exactly 61 application tables." }
    if ([int]$seededUsers -ne 0) { throw "Production smoke unexpectedly seeded demo users." }
    if ($flywayVersion -ne "2") { throw "Production Flyway schema version is not 2." }
    if ($runtimeUser -ne "appuser") { throw "Production image is not running as appuser." }

    [pscustomobject]@{
        Status = "PASS"
        Profile = "prod"
        Health = $health.status
        Database = "PostgreSQL 16 + pgvector"
        DatabaseTls = "VERIFY_FULL"
        SessionStore = "Redis 7.4.2"
        RedisTls = "VERIFIED_CA"
        RedisSessionKeys = $sessionKeyCount
        RedisRateLimitKeys = $rateLimitKeyCount
        RedisSchedulerLeaseKeys = $schedulerLeaseKeyCount
        FlywayVersion = $flywayVersion
        SchemaTables = [int]$tableCount
        DemoUsers = [int]$seededUsers
        RuntimeUser = $runtimeUser
        Image = $Image
    } | Format-List
}
finally {
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    & docker rm -f $appName $redisName $postgresName 2>$null | Out-Null
    & docker network rm $network 2>$null | Out-Null
    $ErrorActionPreference = $savedPreference
    if (Test-Path -LiteralPath $resolvedTempRoot) {
        Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force
    }
}
