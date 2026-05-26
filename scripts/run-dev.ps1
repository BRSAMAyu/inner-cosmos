param(
    [string]$Profile = "",
    [switch]$DownloadOnly
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $root ".tools"
$mavenDir = Join-Path $tools "apache-maven-3.9.9"
$mavenZip = Join-Path $tools "apache-maven-3.9.9-bin.zip"
$mavenCmd = Join-Path $mavenDir "bin\mvn.cmd"

New-Item -ItemType Directory -Force -Path $tools | Out-Null

if (!(Test-Path $mavenCmd)) {
    if (!(Test-Path $mavenZip)) {
        Invoke-WebRequest -Uri "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip" -OutFile $mavenZip
    }
    Expand-Archive -LiteralPath $mavenZip -DestinationPath $tools -Force
}

if ($DownloadOnly) {
    exit 0
}

$args = @("spring-boot:run")
if ($Profile) {
    $args += "-Dspring-boot.run.profiles=$Profile"
}

& $mavenCmd @args
