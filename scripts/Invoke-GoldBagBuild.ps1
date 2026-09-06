[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$MavenCommand = "mvn",
    [string]$JavaCommand = "java"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-Tool([string]$Name) {
    $tool = Get-Command -Name $Name -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $tool) {
        throw "Required tool '$Name' was not found on PATH."
    }
    return $tool
}

try {
    $root = (Resolve-Path -LiteralPath $RepositoryRoot -ErrorAction Stop).Path
    $pom = Join-Path $root "pom.xml"
    if (-not (Test-Path -LiteralPath $pom -PathType Leaf)) {
        throw "Repository root does not contain pom.xml: $root"
    }

    $java = Resolve-Tool $JavaCommand
    $javac = Resolve-Tool "javac"
    $maven = Resolve-Tool $MavenCommand

    Write-Host "Java executable: $($java.Source)"
    & $java.Source -version 2>&1
    if ($LASTEXITCODE -ne 0) {
        $javaExit = $LASTEXITCODE
        throw "Java version check failed with exit code $javaExit."
    }

    Write-Host "Running Maven verify in $root"
    Push-Location $root
    try {
        & $maven.Source -B verify
        $verifyExit = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    if ($verifyExit -ne 0) {
        [Console]::Error.WriteLine("Maven verify failed with exit code $verifyExit.")
        exit $verifyExit
    }

    $artifacts = @(Get-ChildItem -LiteralPath (Join-Path $root "goldbag-plugin\target") -Filter "*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -cmatch "^GoldBag-[^/\\]+\.jar$" -and $_.Name -cnotmatch "-original\.jar$" } |
        Sort-Object LastWriteTimeUtc -Descending)
    if ($artifacts.Count -ne 1) {
        Write-Error "Expected exactly one shaded GoldBag plugin JAR under goldbag-plugin\target; found $($artifacts.Count)."
        exit 1
    }

    $artifact = $artifacts[0]
    $verificationSource = Join-Path $root "scripts\verification\PackagedJarVerification.java"
    if (-not (Test-Path -LiteralPath $verificationSource -PathType Leaf)) {
        throw "Packaged verifier source is missing: $verificationSource"
    }
    $verificationClasses = Join-Path ([IO.Path]::GetTempPath()) ("goldbag-packaged-verifier-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $verificationClasses -Force | Out-Null
    try {
        Write-Host "Compiling packaged verifier: $verificationSource"
        & $javac.Source -encoding UTF-8 -cp $artifact.FullName -d $verificationClasses $verificationSource
        if ($LASTEXITCODE -ne 0) { throw "Packaged verifier compilation failed with exit code $LASTEXITCODE." }

        $classpath = $verificationClasses + [IO.Path]::PathSeparator + $artifact.FullName
        Write-Host "Running packaged verifier against: $($artifact.FullName)"
        & $java.Source -cp $classpath verification.PackagedJarVerification $artifact.FullName
        if ($LASTEXITCODE -ne 0) { throw "Packaged verifier failed with exit code $LASTEXITCODE." }
    }
    finally {
        Remove-Item -LiteralPath $verificationClasses -Recurse -Force -ErrorAction SilentlyContinue
    }

    $checksumPath = Join-Path $artifact.DirectoryName ($artifact.Name + ".sha256")
    $hash = (Get-FileHash -LiteralPath $artifact.FullName -Algorithm SHA256).Hash.ToUpperInvariant()
    "$hash  $($artifact.Name)" | Set-Content -LiteralPath $checksumPath -Encoding ASCII
    Write-Host "Verified shaded JAR: $($artifact.FullName)"
    Write-Host "SHA-256 checksum: $checksumPath"
    exit 0
}
catch {
    Write-Error $_
    exit 1
}
