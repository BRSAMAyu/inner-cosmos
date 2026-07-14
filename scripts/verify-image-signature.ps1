[CmdletBinding()]
param(
    [string]$Image = "inner-cosmos:m1-supply"
)

$ErrorActionPreference = "Stop"

$registryImage = "registry:2.8.3@sha256:a3d8aaa63ed8681a604f1dea0aa03f100d5895b6a58ace528858a7b332415373"
$cosignImage = "ghcr.io/sigstore/cosign/cosign:v3.0.6@sha256:de9c65609e6bde17e6b48de485ee788407c9502fa08b8f4459f595b21f56cd00"
$runId = [Guid]::NewGuid().ToString("N").Substring(0, 12)
$registryName = "inner-cosmos-registry-$runId"
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) "inner-cosmos-signature-$runId"
$resolvedTempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$resolvedTempRoot = [IO.Path]::GetFullPath($tempRoot)
$password = [Guid]::NewGuid().ToString("N")
$curl = Get-Command curl.exe -ErrorAction SilentlyContinue
if (-not $curl) {
    $curl = Get-Command curl -ErrorAction SilentlyContinue
}
if (-not $curl) {
    throw "curl is required to probe the temporary registry."
}

if (-not $resolvedTempRoot.StartsWith($resolvedTempBase, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to use a temporary path outside the system temp directory."
}

function Invoke-Docker {
    param([string[]]$DockerArguments)
    & docker @DockerArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed with exit code $LASTEXITCODE."
    }
}

New-Item -ItemType Directory -Path $resolvedTempRoot | Out-Null

try {
    Invoke-Docker -DockerArguments @("image", "inspect", $Image) | Out-Null
    Invoke-Docker -DockerArguments @("run", "-d", "--name", $registryName, "-p", "5000:5000", $registryImage) | Out-Null
    $portLine = (& docker port $registryName "5000/tcp" | Select-Object -First 1)
    $hostPort = [int]($portLine -replace '^.*:', '')
    $reference = "localhost:5000/inner-cosmos:verification"

    $registryReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $probeErrorPreference = $ErrorActionPreference
        $ErrorActionPreference = "SilentlyContinue"
        & $curl.Source --fail --silent --max-time 2 "http://127.0.0.1:$hostPort/v2/" | Out-Null
        $probeExitCode = $LASTEXITCODE
        $ErrorActionPreference = $probeErrorPreference
        if ($probeExitCode -eq 0) {
            $registryReady = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $registryReady) {
        throw "The temporary OCI registry did not become ready."
    }

    Invoke-Docker -DockerArguments @("tag", $Image, $reference)
    Invoke-Docker -DockerArguments @("push", $reference) | Out-Null
    $repoDigests = (& docker image inspect $reference --format '{{json .RepoDigests}}' | ConvertFrom-Json)
    $digest = ($repoDigests | Where-Object { $_.StartsWith("localhost:5000/inner-cosmos@sha256:") } | Select-Object -First 1)
    if (-not $digest.Contains("@sha256:")) {
        throw "The pushed image did not resolve to an immutable digest."
    }

    $predicate = @{
        buildDefinition = @{
            buildType = "https://inner-cosmos.dev/build/docker/v1"
            externalParameters = @{ image = $Image }
            internalParameters = @{ hermetic = $false }
            resolvedDependencies = @(
                @{ uri = "git+https://github.com/inner-cosmos/inner-cosmos"; digest = @{ gitCommit = (& git rev-parse HEAD).Trim() } }
            )
        }
        runDetails = @{
            builder = @{ id = "https://github.com/sigstore/cosign/releases/tag/v3.0.6" }
            metadata = @{ invocationId = $runId }
        }
    } | ConvertTo-Json -Depth 8
    [IO.File]::WriteAllText((Join-Path $resolvedTempRoot "provenance.json"), $predicate, [Text.UTF8Encoding]::new($false))

    $common = @(
        "run", "--rm", "--network", "host",
        "-e", "COSIGN_PASSWORD=$password",
        "-v", "${resolvedTempRoot}:/keys",
        $cosignImage
    )
    Invoke-Docker -DockerArguments ($common + @("generate-key-pair", "--output-key-prefix", "/keys/cosign")) | Out-Null
    Invoke-Docker -DockerArguments ($common + @("sign", "--yes", "--allow-insecure-registry", "--key", "/keys/cosign.key", $digest)) | Out-Null
    Invoke-Docker -DockerArguments ($common + @(
        "attest", "--yes", "--allow-insecure-registry", "--key", "/keys/cosign.key",
        "--type", "https://slsa.dev/provenance/v1", "--predicate", "/keys/provenance.json", $digest
    )) | Out-Null
    Invoke-Docker -DockerArguments ($common + @("verify", "--allow-insecure-registry", "--key", "/keys/cosign.pub", $digest)) | Out-Null
    Invoke-Docker -DockerArguments ($common + @(
        "verify-attestation", "--allow-insecure-registry", "--key", "/keys/cosign.pub",
        "--type", "https://slsa.dev/provenance/v1", $digest
    )) | Out-Null

    [pscustomobject]@{
        Status = "PASS"
        SignedReference = $digest
        Signature = "VERIFIED"
        Provenance = "VERIFIED"
        Cosign = "3.0.6"
        PrivateKeyPersisted = $false
    } | Format-List
}
finally {
    $cleanupErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    & docker rm -f $registryName 2>$null | Out-Null
    $ErrorActionPreference = $cleanupErrorPreference
    if (Test-Path -LiteralPath $resolvedTempRoot) {
        Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force
    }
}
