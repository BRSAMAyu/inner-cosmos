param(
    [switch]$SkipTests = $true
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$runner = Join-Path $PSScriptRoot "run-dev.ps1"

if (!(Test-Path (Join-Path $root ".tools\apache-maven-3.9.9\bin\mvn.cmd"))) {
    & $runner -DownloadOnly
}

$mavenCmd = Join-Path $root ".tools\apache-maven-3.9.9\bin\mvn.cmd"
$args = @("compile")
if ($SkipTests) {
    $args = @("-DskipTests") + $args
}
& $mavenCmd @args
