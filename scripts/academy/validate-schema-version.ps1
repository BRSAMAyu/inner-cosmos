[CmdletBinding()]
param(
    [string]$MigrationDirectory = "",
    [string]$ConfigMapPath = "",
    [string[]]$WorkloadPaths = @()
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
if ([string]::IsNullOrWhiteSpace($MigrationDirectory)) {
    $MigrationDirectory = Join-Path $root "src/main/resources/db/migration/postgresql"
}
if ([string]::IsNullOrWhiteSpace($ConfigMapPath)) {
    $ConfigMapPath = Join-Path $root "deploy/k8s/base/app-config.yml"
}
if ($WorkloadPaths.Count -eq 0) {
    $WorkloadPaths = @(
        (Join-Path $root "deploy/k8s/base/app-deployment.yml"),
        (Join-Path $root "deploy/k8s/overlays/academy-eks/worker-deployment.yml"),
        (Join-Path $root "deploy/k8s/overlays/academy-eks/scheduler-deployment.yml")
    )
}

$migrationFiles = @(Get-ChildItem -LiteralPath $MigrationDirectory -File -Filter "V*__*.sql")
if ($migrationFiles.Count -eq 0) {
    throw "No versioned PostgreSQL Flyway migrations found at $MigrationDirectory"
}

$versions = @()
foreach ($migration in $migrationFiles) {
    if ($migration.Name -notmatch '^V(?<version>[0-9]+)__.+\.sql$') {
        throw "Schema gate supports integer Flyway versions; unsupported migration name: $($migration.Name)"
    }
    $versions += [int]$Matches.version
}
$duplicateVersions = @($versions | Group-Object | Where-Object Count -gt 1)
if ($duplicateVersions.Count -gt 0) {
    throw "Duplicate Flyway version(s): $($duplicateVersions.Name -join ', ')"
}
$highestMigration = ($versions | Measure-Object -Maximum).Maximum

$config = Get-Content -LiteralPath $ConfigMapPath -Raw -Encoding UTF8
$authorityMatch = [regex]::Match(
    $config,
    '(?m)^\s{2}INNER_COSMOS_EXPECTED_SCHEMA_VERSION:\s*["'']?(?<version>[0-9]+)["'']?\s*$')
if (-not $authorityMatch.Success) {
    throw "ConfigMap has no INNER_COSMOS_EXPECTED_SCHEMA_VERSION authority."
}
$manifestVersion = [int]$authorityMatch.Groups['version'].Value
if ($manifestVersion -ne $highestMigration) {
    throw "Schema gate drift: manifest expects V$manifestVersion but highest Flyway migration is V$highestMigration."
}

foreach ($workloadPath in $WorkloadPaths) {
    $workload = Get-Content -LiteralPath $workloadPath -Raw -Encoding UTF8
    $name = Split-Path -Leaf $workloadPath
    if ($workload -match 'wait-for-schema-v[0-9]+') {
        throw "$name still contains a hard-coded schema-version init-container name."
    }
    if ($workload -notmatch 'name:\s*wait-for-schema-version') {
        throw "$name has no schema-version gate init container."
    }
    if ($workload -notmatch '\-eq\s+\\?"\$INNER_COSMOS_EXPECTED_SCHEMA_VERSION\\?"') {
        throw "$name does not require the exact authoritative schema version."
    }
    if ($workload -notmatch 'flyway_schema_history WHERE NOT success') {
        throw "$name does not fail closed when Flyway records a failed migration."
    }
    if ($workload -notmatch 'configMapKeyRef:\s*\{name:\s*inner-cosmos-config,\s*key:\s*INNER_COSMOS_EXPECTED_SCHEMA_VERSION\}') {
        throw "$name does not source the schema version from inner-cosmos-config."
    }
}

[pscustomobject]@{
    Status = "PASS"
    HighestFlywayMigration = "V$highestMigration"
    ManifestExpectedVersion = $manifestVersion
    GatedWorkloads = $WorkloadPaths.Count
    FailedMigrationPolicy = "FAIL_CLOSED"
}
