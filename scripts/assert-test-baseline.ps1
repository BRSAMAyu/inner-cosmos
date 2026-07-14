param(
    [int]$MinimumTests = 613,
    [string]$ReportsDirectory = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ReportsDirectory)) {
    $ReportsDirectory = Join-Path $root "target\surefire-reports"
}

$reports = @(Get-ChildItem -LiteralPath $ReportsDirectory -Filter "TEST-*.xml" -File)
if ($reports.Count -eq 0) {
    throw "No Surefire XML reports found at $ReportsDirectory"
}

$totals = @{ Tests = 0; Failures = 0; Errors = 0; Skipped = 0 }
foreach ($report in $reports) {
    [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
    $suite = $xml.testsuite
    $totals.Tests += [int]$suite.tests
    $totals.Failures += [int]$suite.failures
    $totals.Errors += [int]$suite.errors
    $totals.Skipped += [int]$suite.skipped
}

Write-Output ("Surefire baseline: suites={0} tests={1} failures={2} errors={3} skipped={4}" -f `
        $reports.Count, $totals.Tests, $totals.Failures, $totals.Errors, $totals.Skipped)

if ($totals.Tests -lt $MinimumTests) {
    throw "Test count $($totals.Tests) is below required baseline $MinimumTests"
}
if ($totals.Failures -ne 0 -or $totals.Errors -ne 0 -or $totals.Skipped -ne 0) {
    throw "Surefire baseline requires zero failures, errors, and skipped tests"
}
