[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ServerJar,

    [Parameter(Mandatory = $true)]
    [string]$TargetDirectory,

    [Parameter(Mandatory = $true)]
    [string]$PluginArtifact,

    [switch]$AllowExisting
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-FullPath([string]$Path, [string]$Description) {
    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
    if ($null -eq $resolved) {
        throw "$Description does not exist: $Path"
    }
    return $resolved.Path
}

function Test-PathInside([string]$Child, [string]$Parent) {
    $childFull = [System.IO.Path]::GetFullPath($Child).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $parentFull = [System.IO.Path]::GetFullPath($Parent).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    return $childFull.Equals($parentFull, [System.StringComparison]::OrdinalIgnoreCase) -or
        $childFull.StartsWith($parentFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)
}

function Assert-NoReparseAncestors([string]$Path, [string]$Description) {
    $current = [System.IO.Path]::GetFullPath($Path)
    while ($true) {
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            if ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
                throw "Refusing $Description through a reparse-point path component: $current"
            }
        }

        $parent = [System.IO.Directory]::GetParent($current)
        if ($null -eq $parent -or $parent.FullName -eq $current) {
            break
        }
        $current = $parent.FullName
    }
}

function Copy-IfMissingOrIdentical([string]$Source, [string]$Destination) {
    if (Test-Path -LiteralPath $Destination -PathType Leaf) {
        $sourceHash = (Get-FileHash -LiteralPath $Source -Algorithm SHA256).Hash
        $destinationHash = (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash
        if ($sourceHash -ne $destinationHash) {
            throw "Refusing to overwrite an existing file with different contents: $Destination"
        }
        Write-Host "Already prepared: $Destination"
        return
    }
    if (Test-Path -LiteralPath $Destination) {
        throw "Expected a file destination, but a different item exists: $Destination"
    }
    Copy-Item -LiteralPath $Source -Destination $Destination -ErrorAction Stop
    Write-Host "Copied $Source to $Destination"
}

try {
    Assert-NoReparseAncestors $ServerJar "server JAR source"
    Assert-NoReparseAncestors $PluginArtifact "plugin artifact source"
    $server = Get-FullPath $ServerJar "Server JAR"
    $plugin = Get-FullPath $PluginArtifact "Plugin artifact"
    Assert-NoReparseAncestors $server "resolved server JAR source"
    Assert-NoReparseAncestors $plugin "resolved plugin artifact source"
    if (-not (Test-Path -LiteralPath $server -PathType Leaf)) {
        throw "Server JAR is not a file: $server"
    }
    if (-not (Test-Path -LiteralPath $plugin -PathType Leaf)) {
        throw "Plugin artifact is not a file: $plugin"
    }

    $target = [System.IO.Path]::GetFullPath($TargetDirectory).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    Assert-NoReparseAncestors $target "target directory"
    $repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
    if (Test-PathInside $target $repositoryRoot) {
        throw "Refusing a target inside the GoldBag workspace. Choose an isolated directory outside $repositoryRoot"
    }
    if (Test-Path -LiteralPath $target) {
        $targetItem = Get-Item -LiteralPath $target -Force
        if (-not $targetItem.PSIsContainer) {
            throw "Target directory is not a directory: $target"
        }
        if ($targetItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
            throw "Refusing a reparse-point target directory: $target"
        }
    }

    if ((Test-PathInside $server $target) -or (Test-PathInside $plugin $target)) {
        throw "Refusing a source/destination collision: the source file is inside the target directory. Choose an isolated target."
    }

    $marker = Join-Path $target ".goldbag-prepared.json"
    $targetExists = Test-Path -LiteralPath $target -PathType Container
    $existingItems = @()
    if ($targetExists) {
        $existingItems = @(Get-ChildItem -LiteralPath $target -Force)
    }

    if ($existingItems.Count -gt 0) {
        if (-not $AllowExisting) {
            throw "Target directory is nonempty. Re-run with -AllowExisting only for a previously prepared, marked target. No files were changed."
        }
        if (-not (Test-Path -LiteralPath $marker -PathType Leaf)) {
            throw "-AllowExisting requires the target's .goldbag-prepared.json marker. No files were changed."
        }
        try {
            $existingMarker = Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json
        }
        catch {
            throw "The existing preparation marker is not valid JSON. No files were changed."
        }
        if ($existingMarker.schema -ne 1 -or $existingMarker.serverDestination -ne "server.jar" -or $existingMarker.pluginDestination -ne "plugins/GoldBag.jar") {
            throw "The existing preparation marker is not a GoldBag preparation marker. No files were changed."
        }
    }

    if (-not $targetExists) {
        New-Item -ItemType Directory -Path $target -Force | Out-Null
    }
    $pluginsDirectory = Join-Path $target "plugins"
    if (Test-Path -LiteralPath $pluginsDirectory) {
        $pluginsItem = Get-Item -LiteralPath $pluginsDirectory -Force
        if (-not $pluginsItem.PSIsContainer -or ($pluginsItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
            throw "Refusing an unsafe plugins path: $pluginsDirectory"
        }
    }
    else {
        New-Item -ItemType Directory -Path $pluginsDirectory -Force | Out-Null
    }

    $serverDestination = Join-Path $target "server.jar"
    $pluginDestination = Join-Path $pluginsDirectory "GoldBag.jar"
    Copy-IfMissingOrIdentical $server $serverDestination
    Copy-IfMissingOrIdentical $plugin $pluginDestination

    if (-not (Test-Path -LiteralPath $marker -PathType Leaf)) {
        $markerObject = [ordered]@{
            schema = 1
            serverDestination = "server.jar"
            pluginDestination = "plugins/GoldBag.jar"
            serverSha256 = (Get-FileHash -LiteralPath $serverDestination -Algorithm SHA256).Hash
            pluginSha256 = (Get-FileHash -LiteralPath $pluginDestination -Algorithm SHA256).Hash
        }
        $markerJson = $markerObject | ConvertTo-Json -Depth 3
        Set-Content -LiteralPath $marker -Value $markerJson -Encoding UTF8 -NoNewline
    }

    Write-Host "Prepared isolated server directory: $target"
    Write-Host "Server JAR: $serverDestination"
    Write-Host "Plugin JAR: $pluginDestination"
    Write-Host "No eula.txt was created or accepted. Start the server manually after reviewing its terms."
    exit 0
}
catch {
    Write-Error $_
    exit 1
}
