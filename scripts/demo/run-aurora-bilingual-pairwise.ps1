[CmdletBinding()]
param(
    [string]$Origin = "http://127.0.0.1:8080",
    [string]$Output = "evidence/innovation/INNO-EVAL-GEMINI-BILINGUAL-001",
    [string]$Model = "gemini-3.6-flash",
    [int]$Seed = 20260728,
    [ValidateRange(1, 6)]
    [int]$Workers = 3,
    [ValidateRange(30, 300)]
    [int]$TimeoutSeconds = 150,
    [string]$DemoContainer = "inner-cosmos-public-demo-app-1"
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$python = Join-Path $repo "scripts/demo/run-aurora-bilingual-pairwise.py"
$dataset = Join-Path $repo "scripts/demo/aurora-bilingual-pairwise-dataset.json"
$outputPath = if ([IO.Path]::IsPathRooted($Output)) { $Output } else { Join-Path $repo $Output }

$previousKey = $env:GEMINI_API_KEY
$keyWasInjected = $false
try {
    if ([string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY)) {
        $containerEnv = & docker inspect $DemoContainer --format '{{range .Config.Env}}{{println .}}{{end}}'
        if ($LASTEXITCODE -ne 0) {
            throw "Could not inspect $DemoContainer. Inject GEMINI_API_KEY in this process instead."
        }
        $line = @($containerEnv | Where-Object { $_ -like "GEMINI_API_KEY=*" }) | Select-Object -First 1
        if ([string]::IsNullOrWhiteSpace($line)) {
            throw "GEMINI_API_KEY is not present in the process or the selected Demo container."
        }
        $env:GEMINI_API_KEY = $line.Substring("GEMINI_API_KEY=".Length)
        $keyWasInjected = $true
    }
    & python $python `
        --origin $Origin `
        --dataset $dataset `
        --output $outputPath `
        --model $Model `
        --seed $Seed `
        --workers $Workers `
        --timeout $TimeoutSeconds
    exit $LASTEXITCODE
} finally {
    if ($keyWasInjected) {
        if ($null -eq $previousKey) {
            Remove-Item Env:GEMINI_API_KEY -ErrorAction SilentlyContinue
        } else {
            $env:GEMINI_API_KEY = $previousKey
        }
    }
}
