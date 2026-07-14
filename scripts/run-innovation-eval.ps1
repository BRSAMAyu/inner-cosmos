param(
    [string]$Output = "evidence/innovation/INNO-EVAL-001",
    [int]$Seed = 20260714
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $repo "ai-lab")
try {
    python -m unittest discover -s tests -v
    python -m evals.cli.main validate
    python -m evals.cli.main run --output (Join-Path $repo $Output) --seed $Seed
} finally {
    Pop-Location
}
