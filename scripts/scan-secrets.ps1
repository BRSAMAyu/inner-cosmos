param(
    [switch]$History
)

$ErrorActionPreference = 'Stop'
$rules = @(
    @{ Name = 'known-token-prefix'; Pattern = '(?i)\b(sk-[A-Za-z0-9_-]{16,}|AIza[0-9A-Za-z_-]{20,}|gh[pousr]_[0-9A-Za-z]{20,}|AKIA[0-9A-Z]{16})\b' },
    @{ Name = 'literal-sensitive-assignment'; Pattern = '(?im)(api[-_]?key|client[-_]?secret|password)\s*[:=]\s*["'']?([A-Za-z0-9+/_=-]{16,})' }
)
$allowed = '(?i)^(test-only|placeholder|example|configured|not-configured)'
$findings = [System.Collections.Generic.List[string]]::new()

function Test-Content([string]$PathLabel, [string]$Content) {
    foreach ($rule in $rules) {
        if ($rule.Name -eq 'literal-sensitive-assignment' -and $PathLabel -match 'src[\\/]test[\\/]java') {
            continue
        }
        foreach ($match in [regex]::Matches($Content, $rule.Pattern)) {
            $candidate = if ($match.Groups.Count -gt 2) { $match.Groups[2].Value } else { $match.Value }
            if ($candidate -match $allowed) { continue }
            $findings.Add("$PathLabel [$($rule.Name)]")
        }
    }
}

if ($History) {
    foreach ($commit in (git rev-list --all)) {
        foreach ($path in (git ls-tree -r --name-only $commit)) {
            if ($path -match '(^|/)(target|\.git)/') { continue }
            $content = git show "${commit}:$path" 2>$null
            if ($LASTEXITCODE -eq 0) { Test-Content "$commit`:$path" ($content -join "`n") }
        }
    }
} else {
    foreach ($path in (rg --files -g '!target/**' -g '!.git/**')) {
        try {
            $content = Get-Content -LiteralPath $path -Raw -Encoding utf8
            Test-Content $path $content
        } catch {
            # Binary or unreadable files are ignored by this text scanner and must be
            # covered by the image/container scanner in the release gate.
        }
    }
}

if ($findings.Count -gt 0) {
    Write-Output "Secret scan FAILED: $($findings.Count) redacted finding(s)."
    $findings | Sort-Object -Unique | ForEach-Object { Write-Output "  $_" }
    exit 1
}

Write-Output 'Secret scan PASS: 0 findings (values are never printed).'
