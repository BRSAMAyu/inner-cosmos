[CmdletBinding()]
param(
    [string]$Image = "inner-cosmos:m1-supply",
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

$mysqlImage = "mysql:8.4@sha256:c831a0f11348d402b43d77453e17d770be2eef356615a2823fe0f5a0d6c8b9af"
$jdkImage = "eclipse-temurin:21-jdk-alpine@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76"
$runId = [Guid]::NewGuid().ToString("N").Substring(0, 12)
$network = "inner-cosmos-prod-smoke-$runId"
$mysqlName = "inner-cosmos-mysql-$runId"
$appName = "inner-cosmos-app-$runId"
$database = "inner_cosmos"
$databaseUser = "inner_cosmos"
$databasePassword = [Guid]::NewGuid().ToString("N")
$rootPassword = [Guid]::NewGuid().ToString("N")
$trustPassword = [Guid]::NewGuid().ToString("N")
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

function Invoke-KeytoolImport {
    param(
        [string]$CertificatePath,
        [string]$TruststorePath,
        [string]$Password
    )

    $command = Get-Command keytool -ErrorAction SilentlyContinue
    if ($command) {
        & $command.Source -importcert -noprompt -storetype PKCS12 -alias mysql-ca `
            -file $CertificatePath -keystore $TruststorePath -storepass $Password | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to create the MySQL truststore with host keytool."
        }
        return
    }
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin/keytool.exe"
        if (Test-Path -LiteralPath $candidate) {
            & $candidate -importcert -noprompt -storetype PKCS12 -alias mysql-ca `
                -file $CertificatePath -keystore $TruststorePath -storepass $Password | Out-Null
            if ($LASTEXITCODE -ne 0) {
                throw "Failed to create the MySQL truststore with JAVA_HOME keytool."
            }
            return
        }
    }

    Invoke-Docker -DockerArguments @(
        "run", "--rm", "-v", "${resolvedTempRoot}:/work", $jdkImage,
        "keytool", "-importcert", "-noprompt", "-storetype", "PKCS12",
        "-alias", "mysql-ca", "-file", "/work/mysql-ca.pem",
        "-keystore", "/work/mysql-truststore.p12", "-storepass", $Password
    ) | Out-Null
}

New-Item -ItemType Directory -Path $resolvedTempRoot | Out-Null

try {
    Invoke-Docker -DockerArguments @("network", "create", $network) | Out-Null
    $mysqlArguments = @(
        "run", "-d", "--name", $mysqlName, "--network", $network,
        "-e", "MYSQL_ROOT_PASSWORD=$rootPassword",
        "-e", "MYSQL_DATABASE=$database",
        "-e", "MYSQL_USER=$databaseUser",
        "-e", "MYSQL_PASSWORD=$databasePassword",
        $mysqlImage,
        "--character-set-server=utf8mb4",
        "--collation-server=utf8mb4_unicode_ci"
    )
    Invoke-Docker -DockerArguments $mysqlArguments | Out-Null

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    $mysqlReady = $false
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        $probeErrorPreference = $ErrorActionPreference
        $ErrorActionPreference = "SilentlyContinue"
        & docker exec -e "MYSQL_PWD=$rootPassword" $mysqlName `
            mysql -uroot -Nse "SELECT 1" 2>$null | Out-Null
        $probeExitCode = $LASTEXITCODE
        $ErrorActionPreference = $probeErrorPreference
        if ($probeExitCode -eq 0) {
            $mysqlReady = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $mysqlReady) {
        & docker logs $mysqlName --tail 120
        throw "MySQL did not become ready before the timeout."
    }

    $caPath = Join-Path $resolvedTempRoot "mysql-ca.pem"
    $truststorePath = Join-Path $resolvedTempRoot "mysql-truststore.p12"
    Invoke-Docker -DockerArguments @("cp", "${mysqlName}:/var/lib/mysql/ca.pem", $caPath)
    Invoke-KeytoolImport -CertificatePath $caPath -TruststorePath $truststorePath `
        -Password $trustPassword

    Invoke-Docker -DockerArguments @("cp", "src/main/resources/schema.sql", "${mysqlName}:/tmp/schema.sql")
    Invoke-Docker -DockerArguments @(
        "exec", "-e", "MYSQL_PWD=$rootPassword", $mysqlName, "sh", "-c",
        'mysql -uroot "$MYSQL_DATABASE" < /tmp/schema.sql'
    )

    $jdbcUrl = "jdbc:mysql://${mysqlName}:3306/${database}?sslMode=VERIFY_CA&trustCertificateKeyStoreUrl=file:/run/secrets/mysql-truststore.p12&trustCertificateKeyStorePassword=$trustPassword&serverTimezone=Asia/Shanghai"
    $runArguments = @(
        "run", "-d", "--name", $appName, "--network", $network,
        "-p", "127.0.0.1::8080",
        "-v", "${truststorePath}:/run/secrets/mysql-truststore.p12:ro",
        "-e", "SPRING_PROFILES_ACTIVE=prod",
        "-e", "SPRING_DATASOURCE_URL=$jdbcUrl",
        "-e", "SPRING_DATASOURCE_USERNAME=$databaseUser",
        "-e", "SPRING_DATASOURCE_PASSWORD=$databasePassword",
        "-e", "LLM_MODE=prod",
        "-e", "LLM_PROVIDER=glm",
        "-e", "LLM_API_KEY=prod-smoke-$runId",
        "-e", "LLM_ALLOW_FALLBACK=false",
        "-e", "SEED_ENABLED=false",
        "-e", "COOKIE_SECURE=true",
        "-e", "CORS_ALLOWED_ORIGINS=https://smoke.inner-cosmos.invalid",
        $Image
    )
    Invoke-Docker -DockerArguments $runArguments | Out-Null

    $portLine = (& docker port $appName "8080/tcp" | Select-Object -First 1)
    if (-not $portLine) {
        throw "Docker did not publish the application port."
    }
    $hostPort = [int]($portLine -replace '^.*:', '')
    $health = $null
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$hostPort/actuator/health" -TimeoutSec 3
            if ($health.status -eq "UP") {
                break
            }
        } catch {
            # Expected while the application is starting.
        }
        Start-Sleep -Seconds 2
    }
    if ($null -eq $health -or $health.status -ne "UP") {
        & docker logs $appName --tail 160
        throw "Production-profile application health did not become UP before the timeout."
    }

    $tableCount = (& docker exec -e "MYSQL_PWD=$rootPassword" $mysqlName mysql `
        -uroot $database -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()").Trim()
    $seededUsers = (& docker exec -e "MYSQL_PWD=$rootPassword" $mysqlName mysql `
        -uroot $database -Nse "SELECT COUNT(*) FROM tb_user").Trim()
    $runtimeUser = (& docker inspect $appName --format '{{.Config.User}}').Trim()

    if ([int]$tableCount -lt 1) {
        throw "Production schema was not loaded."
    }
    if ([int]$seededUsers -ne 0) {
        throw "Production smoke unexpectedly seeded demo users."
    }
    if ($runtimeUser -ne "appuser") {
        throw "Production image is not running as appuser."
    }

    [pscustomobject]@{
        Status = "PASS"
        Profile = "prod"
        Health = $health.status
        DatabaseTls = "VERIFY_CA"
        SchemaTables = [int]$tableCount
        DemoUsers = [int]$seededUsers
        RuntimeUser = $runtimeUser
        Image = $Image
    } | Format-List
}
finally {
    $cleanupErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    & docker rm -f $appName $mysqlName 2>$null | Out-Null
    & docker network rm $network 2>$null | Out-Null
    $ErrorActionPreference = $cleanupErrorPreference
    if (Test-Path -LiteralPath $resolvedTempRoot) {
        Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force
    }
}
