[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Push-Location (Join-Path $root "web")
try {
    & .\node_modules\.bin\tauri.cmd build
    if ($LASTEXITCODE -ne 0) { throw "Tauri installer build failed" }
} finally { Pop-Location }

$bundle = Join-Path $root "web\src-tauri\target\release\bundle"
if (-not (Test-Path $bundle)) { throw "Tauri bundle directory was not created" }
Get-ChildItem -Recurse -File $bundle | Where-Object Extension -In ".msi", ".exe" | ForEach-Object {
    $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName
    [pscustomobject]@{ Path = $_.FullName; SHA256 = $hash.Hash; Bytes = $_.Length }
}
