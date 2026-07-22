[CmdletBinding()]
param(
    [string]$Bundle = "",
    [string]$EvidenceDirectory = ""
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if (-not $Bundle) { $Bundle = Join-Path $root "web\android\app\build\outputs\bundle\release\app-release.aab" }
if (-not (Test-Path -LiteralPath $Bundle)) { throw "Release AAB missing: $Bundle" }
if (-not $EvidenceDirectory) { $EvidenceDirectory = Join-Path $root "evidence\mobile\release-audit" }
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null

$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$extract = Join-Path $tempRoot ("inner-cosmos-aab-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $extract | Out-Null
try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory((Resolve-Path $Bundle).Path, $extract)
    $forbidden = @(
        "10.0.2.2",
        # React Router contains a non-network URL-construction fallback named
        # http://localhost. Gate actual development endpoints by requiring a port.
        "http://localhost:",
        "http://127.0.0.1",
        "mobile-local",
        "mobile-demo",
        "demo123",
        "local-admin-only"
    )
    $findings = [Collections.Generic.List[string]]::new()
    foreach ($file in Get-ChildItem -LiteralPath $extract -Recurse -File) {
        $bytes = [IO.File]::ReadAllBytes($file.FullName)
        $ascii = [Text.Encoding]::UTF8.GetString($bytes)
        foreach ($pattern in $forbidden) {
            if ($ascii.IndexOf($pattern, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
                $relative = $file.FullName.Substring($extract.TrimEnd('\').Length).TrimStart('\')
                $findings.Add("$relative :: $pattern")
            }
        }
    }
    $mergedManifest = Join-Path $root "web\android\app\build\intermediates\merged_manifests\release\processReleaseManifest\AndroidManifest.xml"
    if (Test-Path $mergedManifest) {
        $manifestText = Get-Content -LiteralPath $mergedManifest -Raw
        foreach ($pattern in @('networkSecurityConfig', 'usesCleartextTraffic="true"', 'android:debuggable="true"', 'android:allowBackup="true"')) {
            if ($manifestText.IndexOf($pattern, [StringComparison]::OrdinalIgnoreCase) -ge 0) { $findings.Add("merged AndroidManifest.xml :: $pattern") }
        }
    }
    if ($findings.Count -gt 0) { throw "Release leakage gate failed: $($findings -join '; ')" }

    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Bundle).Hash.ToLowerInvariant()
    @(
        "status=PASS"
        "aab_sha256=$hash"
        "forbidden_pattern_count=0"
        "debug_network_policy_present=false"
        "verified_at=$((Get-Date).ToString('o'))"
    ) | Set-Content -Encoding utf8 (Join-Path $EvidenceDirectory "release-audit.txt")
    Write-Host "ANDROID_RELEASE_AUDIT_PASS aab_sha256=$hash"
} finally {
    $resolved = [IO.Path]::GetFullPath($extract)
    if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) { throw "Refusing to clean unexpected path: $resolved" }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
