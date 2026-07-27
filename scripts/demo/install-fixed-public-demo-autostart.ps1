[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [ValidateSet("deepseek", "glm", "gemini")]
    [string]$Provider = "gemini",
    [string]$TaskName = "Inner Cosmos Fixed Public Demo",
    [switch]$Remove
)

$ErrorActionPreference = "Stop"

if ($Remove) {
    if ($PSCmdlet.ShouldProcess($TaskName, "Unregister current-user Demo startup task")) {
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
        Write-Output "FIXED_PUBLIC_DEMO_AUTOSTART_REMOVED"
    }
    return
}

$starter = (Resolve-Path (Join-Path $PSScriptRoot "start-fixed-public-demo.ps1")).Path
$configPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path ".demo-runtime\fixed-tunnel.json"
if (-not (Test-Path -LiteralPath $configPath)) {
    throw "Fixed tunnel configuration is missing. Run set-fixed-public-demo.ps1 first."
}

$powerShell = Join-Path $PSHOME "powershell.exe"
$actionArguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$starter`" -Provider $Provider -SkipApkBuild"
$action = New-ScheduledTaskAction -Execute $powerShell -Argument $actionArguments
$trigger = New-ScheduledTaskTrigger -AtLogOn -User ([Security.Principal.WindowsIdentity]::GetCurrent().Name)
$trigger.Delay = "PT1M"
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 2) -ExecutionTimeLimit (New-TimeSpan -Hours 1)
$principal = New-ScheduledTaskPrincipal `
    -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) `
    -LogonType Interactive -RunLevel Limited

if ($PSCmdlet.ShouldProcess($TaskName, "Register current-user Demo startup task")) {
    Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger `
        -Settings $settings -Principal $principal -Force | Out-Null
    Write-Output "FIXED_PUBLIC_DEMO_AUTOSTART_INSTALLED"
    Write-Output "task=$TaskName"
    Write-Output "delay=60s"
}
