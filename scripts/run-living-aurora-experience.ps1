$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$maven = Join-Path $root '.tools\apache-maven-3.9.9\bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $maven)) {
    throw "Repository Maven not found: $maven"
}

& $maven -q -f (Join-Path $root 'pom.xml') -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "JAR build failed with exit code $LASTEXITCODE" }

Push-Location (Join-Path $root 'web')
try {
    & pnpm exec playwright test --config=playwright.living-aurora.config.ts
    if ($LASTEXITCODE -ne 0) { throw "Living Aurora Experience Contract failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}
