# CP-49/CP-41 release manifest generator (版本化发布清单).
#
# Produces release-manifest.json binding the release identity together:
#   version tag, git SHA, backend (pom.xml) + web (package.json) versions, and the
#   SHA-256 digests of the release artifacts and CycloneDX SBOMs that EXIST at run time.
# Honesty rule: anything absent is listed in "absent" with NO digest — the manifest
# never carries a placeholder or fabricated value. Run from the repo root.
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [string]$BackendSbom = "target/bom.json",
    [string]$WebSbom = "web/cyclonedx.json",
    [string]$WebDist = "web/dist",
    [string]$OutFile = "release-manifest.json"
)

$ErrorActionPreference = "Stop"

if ($Version -notmatch '^v\d+\.\d+\.\d+([.\-][0-9A-Za-z.\-]+)?$') {
    throw "Version must be a v-prefixed semantic version (got '$Version')"
}

function Get-Sha256([string]$Path) {
    (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

# XML DOM access (works on both Windows PowerShell 5.1 and pwsh 7; Select-Xml XPath
# does not match elements under the default Maven namespace).
$pomVersion = ([xml](Get-Content pom.xml -Raw)).project.version.Trim()
$webVersion = (Get-Content web/package.json -Raw | ConvertFrom-Json).version
$gitSha = (git rev-parse HEAD).Trim()
if ($gitSha -notmatch '^[0-9a-f]{40}$') { throw "git SHA not resolvable: '$gitSha'" }

$artifacts = [ordered]@{}
$absent = @()

# --- SBOMs (CycloneDX): see scripts/supply-chain/README.md for generation commands ---
foreach ($pair in @(@("backend-sbom", $BackendSbom), @("web-sbom", $WebSbom))) {
    $name = $pair[0]; $path = $pair[1]
    if (Test-Path $path) { $artifacts[$name] = [ordered]@{ path = $path; sha256 = Get-Sha256 $path } }
    else { $absent += $name }
}

# --- Web bundle digests (entry points only: index.html + js/css assets root) ---
if (Test-Path "$WebDist/index.html") {
    $artifacts["web-dist-index"] = [ordered]@{
        path = "$WebDist/index.html"; sha256 = Get-Sha256 "$WebDist/index.html"
    }
    Get-ChildItem "$WebDist/assets" -File -ErrorAction SilentlyContinue |
        Sort-Object Name | ForEach-Object {
            $artifacts["web-dist-assets/$($_.Name)"] = [ordered]@{
                path = $_.FullName; sha256 = Get-Sha256 $_.FullName
            }
        }
} else { $absent += "web-dist" }

$manifest = [ordered]@{
    schema        = "inner-cosmos.release-manifest/v1"
    version       = $Version
    gitSha        = $gitSha
    generatedAt   = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    backendVersion = $pomVersion
    webVersion    = $webVersion
    artifacts     = $artifacts
    absent        = $absent
}

$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutFile -Encoding utf8
Write-Host "wrote $OutFile with $($artifacts.Count) digested artifact(s), $($absent.Count) absent entr(ies)"
