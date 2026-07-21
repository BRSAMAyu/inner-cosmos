param(
    [ValidateSet("Run", "Verify")]
    [string]$Action = "Run",
    [int]$Port = 8080,
    [switch]$SkipBuild,
    [int]$StartupTimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$maven = Join-Path $root "mvnw.cmd"
$jar = Join-Path $root "target\inner-cosmos-0.1.0.jar"
$runId = Get-Date -Format "yyyyMMdd-HHmmss-fff"
$logDir = Join-Path $root "target\teacher-demo"
$stdoutLog = Join-Path $logDir "$runId.stdout.log"
$stderrLog = Join-Path $logDir "$runId.stderr.log"
$baseUrl = "http://127.0.0.1:$Port"

function Assert-PortAvailable {
    param([int]$CandidatePort)
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $CandidatePort)
    try {
        $listener.Start()
    } catch {
        throw "Port $CandidatePort is already in use. Stop the existing process or select another -Port."
    } finally {
        $listener.Stop()
    }
}

function Wait-ForHealth {
    param([System.Diagnostics.Process]$Process)
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    do {
        if ($Process.HasExited) {
            $tail = if (Test-Path -LiteralPath $stderrLog) {
                (Get-Content -LiteralPath $stderrLog -Tail 40 -ErrorAction SilentlyContinue) -join [Environment]::NewLine
            } else { "No stderr log was created." }
            throw "Teacher demo exited before becoming healthy (exit $($Process.ExitCode)).`n$tail"
        }
        try {
            $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -TimeoutSec 3
            if ($health.status -eq "UP") { return }
        } catch {
            # Startup is still in progress. The bounded deadline below remains authoritative.
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Teacher demo did not become healthy within $StartupTimeoutSeconds seconds. See $stdoutLog and $stderrLog."
}

function Invoke-TeacherDemoSmoke {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $body = @{ username = "demo"; password = "demo123"; timezone = "Asia/Singapore" } | ConvertTo-Json -Compress
    $login = $null
    $loginDeadline = (Get-Date).AddSeconds(45)
    do {
        $csrf = Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/csrf" -WebSession $session -TimeoutSec 10
        if (-not $csrf.success -or -not $csrf.data.token -or -not $csrf.data.headerName) {
            throw "CSRF bootstrap did not return a usable synchronizer token."
        }
        $headers = @{}
        $headers[$csrf.data.headerName] = $csrf.data.token
        try {
            $login = Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" `
                -Headers $headers -Body $body -WebSession $session -TimeoutSec 15
        } catch {
            # Actuator can become UP while H2 compatibility initializers and the opt-in showcase
            # seed runner are still finishing. Retry only inside this bounded clean-demo window.
            Start-Sleep -Milliseconds 500
        }
    } while (-not $login -and (Get-Date) -lt $loginDeadline)
    if (-not $login.success -or $login.data.username -ne "demo") {
        throw "Seeded teacher-demo login smoke failed."
    }

    $current = Invoke-RestMethod -Uri "$baseUrl/api/v1/auth/current" -WebSession $session -TimeoutSec 10
    if (-not $current.success -or $current.data.username -ne "demo") {
        throw "Authenticated teacher-demo session smoke failed."
    }

    $shell = Invoke-WebRequest -Uri "$baseUrl/app/aurora/" -WebSession $session -TimeoutSec 10 -UseBasicParsing
    if ($shell.StatusCode -ne 200 -or $shell.Content -notmatch '<div id="root"></div>') {
        throw "React teacher-demo shell smoke failed."
    }
}

Assert-PortAvailable -CandidatePort $Port

$java = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    (Get-Command java -ErrorAction Stop).Source
}
if (-not $env:JAVA_HOME) {
    $priorErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        # java writes -XshowSettings to stderr even on success; collect it without letting
        # Windows PowerShell promote that diagnostic stream to a terminating error.
        $javaSettings = (& $java -XshowSettings:properties -version 2>&1 | Out-String)
    } finally {
        $ErrorActionPreference = $priorErrorAction
    }
    $javaHomeMatch = [regex]::Match($javaSettings, '(?m)^\s*java\.home\s*=\s*(.+?)\s*$')
    if (-not $javaHomeMatch.Success) {
        throw "JAVA_HOME is unset and the Java runtime home could not be discovered."
    }
    $env:JAVA_HOME = $javaHomeMatch.Groups[1].Value.Trim()
}

if (-not $SkipBuild) {
    & $maven -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "Teacher demo JAR build failed with exit code $LASTEXITCODE." }
}
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Teacher demo JAR not found: $jar. Run without -SkipBuild first."
}

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# Every launch uses its own in-memory database. It never opens ./data/innercosmos and vanishes
# with this process, so prior demo actions, password changes, or local developer data cannot leak
# into the presentation. Command-line properties outrank inherited environment variables and pin
# this path to Mock + seed; production configuration is not changed.
$databaseName = "teacher_demo_$($runId.Replace('-', '_'))"
$quotedJar = '"' + $jar + '"'
$arguments = @(
    "-Dfile.encoding=UTF-8",
    "-jar", $quotedJar,
    "--spring.profiles.active=demo",
    "--server.port=$Port",
    "--spring.datasource.url=jdbc:h2:mem:$databaseName;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "--spring.sql.init.mode=always",
    "--inner-cosmos.demo.seed-enabled=true",
    "--inner-cosmos.runtime.role=all",
    "--llm.mode=demo",
    "--llm.provider=mock",
    "--llm.allow-fallback=true",
    "--llm.asr-provider=mock",
    "--memory.embedding.enabled=false",
    # The isolated teacher path intentionally uses no Redis. Feature flags are already false by
    # default; disable only its generic Actuator contributor so /health reflects this profile.
    "--management.health.redis.enabled=false"
)

# Some agent/IDE launchers inject both `Path` and `PATH` into the Windows process block.
# Windows PowerShell's Start-Process copies that block into a case-insensitive dictionary and
# crashes on the duplicate before Java starts. Normalize only this script process; the caller's
# environment is untouched.
$processEnvironment = [System.Environment]::GetEnvironmentVariables()
if ($processEnvironment.Contains("Path") -and $processEnvironment.Contains("PATH")) {
    $canonicalPath = [string]$processEnvironment["Path"]
    [System.Environment]::SetEnvironmentVariable("PATH", $null, [System.EnvironmentVariableTarget]::Process)
    [System.Environment]::SetEnvironmentVariable("Path", $canonicalPath, [System.EnvironmentVariableTarget]::Process)
}

$process = Start-Process -FilePath $java -ArgumentList $arguments -PassThru -WindowStyle Hidden `
    -WorkingDirectory $root -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
try {
    Wait-ForHealth -Process $process
    Invoke-TeacherDemoSmoke
    Write-Host "Teacher demo verified: health UP, demo login/session PASS, React shell PASS."
    Write-Host "Open $baseUrl/app/aurora/ and sign in with demo / demo123."
    Write-Host "Isolated in-memory data id: $databaseName (removed automatically when this process exits)."
    Write-Host "Logs: $stdoutLog and $stderrLog"

    if ($Action -eq "Verify") {
        Write-Host "Verification mode complete; stopping the isolated server."
    } else {
        Write-Host "Press Ctrl+C to stop the isolated teacher demo."
        Wait-Process -Id $process.Id
    }
} finally {
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
        $process.WaitForExit(10000) | Out-Null
    }
}
