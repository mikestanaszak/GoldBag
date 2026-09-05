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

    $artifacts = @(Get-ChildItem -LiteralPath (Join-Path $root "goldbag-plugin\target") -Filter "GoldBag-*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "(^|-)original\.jar$" } |
        Sort-Object LastWriteTimeUtc -Descending)
    if ($artifacts.Count -eq 0) {
        Write-Error "Maven verify succeeded but no GoldBag plugin JAR was found under goldbag-plugin\target."
        exit 1
    }

    Write-Host "Plugin artifact: $($artifacts[0].FullName)"
    exit 0
}
catch {
    Write-Error $_
    exit 1
}
