# CP-49/CP-41 release manifest contract (发布清单契约，CI 必跑).
#
# Fail-closed honesty checks over a generated release-manifest.json:
#   - schema + version tag + 40-hex git SHA + non-empty versions
#   - web/backend versions in the manifest match the LIVE files (drift guard)
#   - every artifact digest is 64-hex lowercase over an existing file
#   - every absent entry has NO digest anywhere (absent means absent)
param([string]$ManifestPath = "release-manifest.json")

$ErrorActionPreference = "Stop"
$failures = @()

$m = Get-Content $ManifestPath -Raw | ConvertFrom-Json
if ($m.schema -ne "inner-cosmos.release-manifest/v1") { $failures += "schema mismatch: $($m.schema)" }
if ($m.version -notmatch '^v\d+\.\d+\.\d+([.\-][0-9A-Za-z.\-]+)?$') { $failures += "bad version tag: $($m.version)" }
if ($m.gitSha -notmatch '^[0-9a-f]{40}$') { $failures += "gitSha not 40-hex: $($m.gitSha)" }
if (-not $m.backendVersion) { $failures += "backendVersion empty" }
if (-not $m.webVersion) { $failures += "webVersion empty" }

# Live drift guard: manifest versions must equal the repo's current files.
$livePom = ([xml](Get-Content pom.xml -Raw)).project.version.Trim()
$liveWeb = (Get-Content web/package.json -Raw | ConvertFrom-Json).version
if ($m.backendVersion -ne $livePom) { $failures += "backendVersion drift: manifest=$($m.backendVersion) pom=$livePom" }
if ($m.webVersion -ne $liveWeb) { $failures += "webVersion drift: manifest=$($m.webVersion) package.json=$liveWeb" }

if ($null -eq $m.artifacts -or $m.artifacts.PSObject.Properties.Count -eq 0) {
    $failures += "no artifacts digested"
}
foreach ($p in $m.artifacts.PSObject.Properties) {
    $entry = $p.Value
    if ($entry.sha256 -notmatch '^[0-9a-f]{64}$') { $failures += "$($p.Name): digest not 64-hex" }
    elseif (-not (Test-Path $entry.path)) { $failures += "$($p.Name): file missing at $($entry.path)" }
}
foreach ($name in @($m.absent)) {
    if ($m.artifacts.PSObject.Properties.Name -contains $name) {
        $failures += "$name is both present and absent"
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error "release-manifest: $_" }
    exit 1
}
Write-Host "release-manifest contract OK ($($m.version) @ $($m.gitSha.Substring(0,8)))"
