[CmdletBinding()]
param(
    [string]$ExpectedContext = "kind-kubedeploy",
    [string]$Namespace = "inner-cosmos-w3"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$context = (& kubectl config current-context).Trim()
if ($LASTEXITCODE -ne 0 -or $context -ne $ExpectedContext) {
    throw "Current context '$context' is not the expected disposable showcase '$ExpectedContext'."
}

$keyFiles = @(Get-ChildItem -LiteralPath $root -File |
    Where-Object { $_.Name.StartsWith("API", [StringComparison]::OrdinalIgnoreCase) -and $_.Extension -eq ".txt" } |
    Sort-Object Name |
    Select-Object -ExpandProperty FullName)
if ($keyFiles.Count -eq 0) { throw "No gitignored API*.txt operator file was found." }
$lines = @($keyFiles | ForEach-Object { Get-Content -LiteralPath $_ -Encoding utf8 })

function Match-Key([string]$linePattern, [string]$valuePattern, [string]$label) {
    $line = $lines | Where-Object { $_ -match $linePattern } | Select-Object -First 1
    if (-not $line -or $line -notmatch $valuePattern) { throw "No valid $label key was found." }
    return $Matches[1].Trim().TrimEnd([char]0x3001)
}

$gemini = Match-Key "^\s*gemini\s*:" "^\s*gemini\s*:\s*(\S+)" "Gemini"
$deepseek = Match-Key "(?i)deepseek.*apikey\s*:" "(?i)^.*apikey\s*:\s*(\S+)" "DeepSeek"
$minimax = Match-Key "(?i)^\s*minimax" "(sk-[A-Za-z0-9._-]+)" "MiniMax"
$glm = Match-Key "(?i)^\s*glm" "([0-9a-fA-F]{32}\.[A-Za-z0-9_-]+)" "GLM"
$mimo = Match-Key "(?i)^\s*mimo" "(tp-[A-Za-z0-9._-]+)" "MiMo"
$dashscope = Match-Key "(?i)^\s*(dashscope|qwen)\s*:" "(?i)^\s*(?:dashscope|qwen)\s*:\s*(\S+)" "DashScope"

$patchFile = Join-Path ([IO.Path]::GetTempPath()) ("inner-cosmos-provider-secret-" + [Guid]::NewGuid() + ".json")
try {
    $patchJson = @{
        stringData = @{
            LLM_API_KEY = $gemini
            GEMINI_API_KEY = $gemini
            DEEPSEEK_API_KEY = $deepseek
            MINIMAX_API_KEY = $minimax
            GLM_API_KEY = $glm
            MIMO_API_KEY = $mimo
            TTS_API_KEY = $dashscope
        }
    } | ConvertTo-Json -Depth 4 -Compress
    [IO.File]::WriteAllText($patchFile, $patchJson, [Text.UTF8Encoding]::new($false))
    & kubectl -n $Namespace patch secret inner-cosmos-runtime --type merge --patch-file $patchFile | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Kubernetes provider secret patch failed." }
} finally {
    Remove-Item -LiteralPath $patchFile -Force -ErrorAction SilentlyContinue
    $patchJson = $null
    $gemini = $deepseek = $minimax = $glm = $mimo = $dashscope = $null
}

& kubectl apply -f (Join-Path $root "deploy\k8s\overlays\kind-full\app-config.yaml") | Out-Null
if ($LASTEXITCODE -ne 0) { throw "kind provider ConfigMap apply failed." }
Write-Output "KIND_PROVIDER_CONFIGURATION_SYNCED context=$context namespace=$Namespace provider=gemini embedding=false asr=mimo tts=true"
