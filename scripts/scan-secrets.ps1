param(
    [switch]$History,
    [switch]$AllRefs
)

$ErrorActionPreference = 'Stop'
$rules = @(
    @{
        Name = 'known-token-prefix'
        Pattern = '(?i)\b(sk-[A-Za-z0-9_-]{16,}|AIza[0-9A-Za-z_-]{20,}|gh[pousr]_[0-9A-Za-z]{20,}|AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16})\b'
        GitPattern = '(sk-[A-Za-z0-9_-]{16,}|AIza[0-9A-Za-z_-]{20,}|gh[pousr]_[0-9A-Za-z]{20,}|AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16})'
    },
    @{
        Name = 'literal-sensitive-assignment'
        Pattern = '(?im)(api[-_]?key|client[-_]?secret|secret[-_]?access[-_]?key|session[-_]?token|password)[ \t]*[:=][ \t]*["'']?([A-Za-z0-9+/_=-]{16,})'
        GitPattern = '(api[-_]?key|client[-_]?secret|secret[-_]?access[-_]?key|session[-_]?token|password)[[:blank:]]*[:=][[:blank:]]*["'']?[A-Za-z0-9+/_=-]{16,}'
    }
)
$allowed = '(?i)^(test-only|placeholder|example|configured|not-configured|your_|change_me|redacted|removed_from_history|\$\{|<)'
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
    $commits = if ($AllRefs) { @(git rev-list --all) } else { @(git rev-list HEAD) }
    if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate Git history.' }

    # `git grep --name-only` narrows the history to candidate blobs without ever
    # printing a matched credential. The former commit x file nested loop was
    # safe but prohibitively slow on the long-running Goal branch.
    $candidateRefs = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal)
    $chunkSize = 40
    foreach ($rule in $rules) {
        for ($offset = 0; $offset -lt $commits.Count; $offset += $chunkSize) {
            $last = [Math]::Min($offset + $chunkSize - 1, $commits.Count - 1)
            $chunk = @($commits[$offset..$last])
            $matches = @(& git grep --name-only --full-name -I -E -e $rule.GitPattern @chunk 2>$null)
            if ($LASTEXITCODE -notin @(0, 1)) { throw "Git history scan failed for $($rule.Name)." }
            foreach ($candidate in $matches) { [void]$candidateRefs.Add($candidate) }
        }
    }

    foreach ($candidateRef in $candidateRefs) {
        $separator = $candidateRef.IndexOf(':')
        if ($separator -le 0) { continue }
        $commit = $candidateRef.Substring(0, $separator)
        $path = $candidateRef.Substring($separator + 1)
        if ($path -match '(^|/)(target|\.git)/') { continue }
        $content = git show "${commit}:$path" 2>$null
        if ($LASTEXITCODE -eq 0) {
            Test-Content "$commit`:$path" ($content -join "`n")
        }
    }
} else {
    # Enumerate both tracked files and untracked, non-ignored files with git (always available)
    # instead of ripgrep, which is not installed on the CI runner (or many dev machines).
    # Scanning tracked files alone would miss a newly-created credential before it is staged.
    foreach ($path in (git ls-files --cached --others --exclude-standard)) {
        if ($path -match '(^|/)(target|\.git)/') { continue }
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
