[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("gemini", "deepseek", "glm", "minimax", "mimo", "qwen")]
    [string]$Provider
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$target = Join-Path $root "API.local.txt"
$secure = Read-Host "Paste the $Provider API key (input is hidden)" -AsSecureString
$pointer = [IntPtr]::Zero

try {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ([string]::IsNullOrWhiteSpace($plain)) {
        throw "The supplied key was empty."
    }

    $lines = if (Test-Path -LiteralPath $target) {
        [Collections.Generic.List[string]]::new(
                [string[]](Get-Content -LiteralPath $target -Encoding utf8))
    } else {
        [Collections.Generic.List[string]]::new()
    }
    $pattern = "^\s*" + [regex]::Escape($Provider) + "\s*:"
    for ($index = $lines.Count - 1; $index -ge 0; $index--) {
        if ($lines[$index] -match $pattern) {
            $lines.RemoveAt($index)
        }
    }
    $lines.Add("$Provider`:$plain")
    [IO.File]::WriteAllLines($target, $lines, [Text.UTF8Encoding]::new($false))
    Write-Output "LOCAL_PROVIDER_KEY_SAVED provider=$Provider file=API.local.txt"
} finally {
    if ($pointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
    $plain = $null
    $secure = $null
}
