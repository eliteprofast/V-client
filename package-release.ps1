$ErrorActionPreference = "Stop"

$repoRoot = $PSScriptRoot
$sourceDirectory = Join-Path $repoRoot "src"
$iconPath = Join-Path $sourceDirectory "main\resources\vclient.ico"
$releaseDirectory = Join-Path $repoRoot "Release"
$zipPath = Join-Path $repoRoot "AzyrClient.zip"
$appImageParent = Join-Path $repoRoot "target\release-app-image"
$releaseStaging = Join-Path $repoRoot "target\release-staging"
$jpackageInput = Join-Path $repoRoot "target\release-input"
$javafxModules = Join-Path $repoRoot "target\javafx-modules"
$zuluArchive = Join-Path $repoRoot "target\zulu21-win-x64.zip"
$zuluExtracted = Join-Path $repoRoot "target\zulu21-extracted"
$zuluRuntime = Join-Path $repoRoot "target\zulu21-runtime"
$zipStaging = Join-Path $repoRoot "target\AzyrClient.zip.tmp"
$javaHome = $env:JAVA_HOME

if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome "bin\jpackage.exe"))) {
    throw "Set JAVA_HOME to a 64-bit JDK 21 installation before building the release."
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven must be installed and available on PATH."
}
if (-not (Test-Path $iconPath)) {
    throw "The custom Windows launcher icon is missing: $iconPath"
}

Push-Location $repoRoot
try {
    & mvn -B -ntp package
    if ($LASTEXITCODE -ne 0) {
        throw "The Maven build failed with exit code $LASTEXITCODE."
    }

    $runtimeDependencies = Join-Path $repoRoot "target\app"
    if (-not (Test-Path (Join-Path $runtimeDependencies "V-Client.jar"))) {
        throw "The Maven build did not produce the launcher JAR."
    }

    $zuluJava = Join-Path $zuluRuntime "bin\java.exe"
    $zuluDetails = ""
    if (Test-Path $zuluJava) {
        $zuluDetails = (& $env:ComSpec /c "`"$zuluJava`" -XshowSettings:properties -version 2>&1" | Out-String)
        if (($LASTEXITCODE -ne 0) -or ($zuluDetails -notmatch "os\.arch\s*=\s*amd64") -or ($zuluDetails -notmatch "java\.specification\.version\s*=\s*21") -or ($zuluDetails -notmatch "(?i)azul")) {
            throw "The existing Azul Zulu runtime failed its Java 21, x64, or vendor verification; it was left untouched."
        }
        Write-Host "Reusing the verified Azul Zulu Java 21 x64 runtime in target."
    } else {
        $zuluMetadataUrl = "https://api.azul.com/metadata/v1/zulu/packages/?java_version=21&os=windows&arch=x86&hw_bitness=64&java_package_type=jre&archive_type=zip&release_status=ga&availability_types=CA&latest=true&bundle_type=jre"
        Write-Host "Resolving the official 64-bit Azul Zulu Java 21 runtime..."
        $zuluPackages = Invoke-RestMethod -Uri $zuluMetadataUrl -TimeoutSec 30
        $zuluPackage = $zuluPackages |
            Where-Object { $_.java_version[0] -eq 21 -and $_.name -like "*-ca-jre*-win_x64.zip" -and $_.name -notlike "*-fx-*" } |
            Select-Object -First 1
        if (-not $zuluPackage -or ([Uri]$zuluPackage.download_url).Host -ne "cdn.azul.com") {
            throw "Azul did not return an official Windows x64 Zulu Java 21 JRE package."
        }
        if (Test-Path $zuluExtracted) {
            Remove-Item -LiteralPath $zuluExtracted -Recurse -Force
        }
        Write-Host "Downloading $($zuluPackage.name)..."
        Invoke-WebRequest -Uri $zuluPackage.download_url -OutFile $zuluArchive -TimeoutSec 300
        Expand-Archive -LiteralPath $zuluArchive -DestinationPath $zuluExtracted -Force
        $downloadedZuluJava = Get-ChildItem -LiteralPath $zuluExtracted -Filter "java.exe" -File -Recurse |
            Select-Object -First 1
        if (-not $downloadedZuluJava) {
            throw "The official Azul Zulu archive did not contain java.exe."
        }
        $zuluDetails = (& $env:ComSpec /c "`"$($downloadedZuluJava.FullName)`" -XshowSettings:properties -version 2>&1" | Out-String)
        if (($LASTEXITCODE -ne 0) -or ($zuluDetails -notmatch "os\.arch\s*=\s*amd64") -or ($zuluDetails -notmatch "java\.specification\.version\s*=\s*21") -or ($zuluDetails -notmatch "(?i)azul")) {
            throw "The Azul Zulu runtime failed its Java 21, x64, or vendor verification."
        }
        New-Item -ItemType Directory -Path $zuluRuntime -Force | Out-Null
        Copy-Item -Path (Join-Path $downloadedZuluJava.Directory.Parent.FullName "*") -Destination $zuluRuntime -Recurse -Force
        Remove-Item -LiteralPath $zuluArchive -Force
        Remove-Item -LiteralPath $zuluExtracted -Recurse -Force
    }

    if (Test-Path $appImageParent) {
        Remove-Item -LiteralPath $appImageParent -Recurse -Force
    }
    if (Test-Path $releaseStaging) {
        Remove-Item -LiteralPath $releaseStaging -Recurse -Force
    }
    if (Test-Path $zipStaging) {
        Remove-Item -LiteralPath $zipStaging -Force
    }
    New-Item -ItemType Directory -Path $jpackageInput, $javafxModules -Force | Out-Null

    Get-ChildItem -LiteralPath $runtimeDependencies -Filter "*.jar" -File |
        Where-Object { $_.Name -notlike "javafx-*" } |
        Copy-Item -Destination $jpackageInput -Force
    $platformJavaFx = Get-ChildItem -LiteralPath $runtimeDependencies -Filter "javafx-*-win.jar" -File
    if (-not $platformJavaFx) {
        throw "The Maven build did not produce Windows JavaFX modules."
    }
    Copy-Item -LiteralPath $platformJavaFx.FullName -Destination $javafxModules -Force

    $modulePath = "$javafxModules;$javaHome\jmods"
    & (Join-Path $javaHome "bin\jpackage.exe") `
        --type app-image `
        --name V-Client `
        --app-version 1.0.0 `
        --icon $iconPath `
        --input $jpackageInput `
        --main-jar "V-Client.jar" `
        --main-class "com.vclient.LauncherApp" `
        --module-path $modulePath `
        --add-modules "javafx.controls,java.desktop,java.logging,java.net.http,jdk.crypto.mscapi,jdk.crypto.ec" `
        --java-options "--add-modules=javafx.controls,java.desktop,java.logging,java.net.http,jdk.crypto.mscapi,jdk.crypto.ec" `
        --dest $appImageParent
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }

    $packagedApplication = Join-Path $appImageParent "V-Client"
    if (-not (Test-Path (Join-Path $packagedApplication "V-Client.exe"))) {
        throw "jpackage did not produce a runnable V-Client.exe."
    }

    New-Item -ItemType Directory -Path $releaseStaging | Out-Null
    Copy-Item -Path (Join-Path $packagedApplication "*") -Destination $releaseStaging -Recurse
    $releaseRuntimes = Join-Path $releaseStaging "runtimes\zulu21"
    New-Item -ItemType Directory -Path $releaseRuntimes -Force | Out-Null
    Copy-Item -Path (Join-Path $zuluRuntime "*") -Destination $releaseRuntimes -Recurse -Force
    Copy-Item -LiteralPath $iconPath -Destination $releaseStaging
    Copy-Item -LiteralPath (Join-Path $repoRoot "README.md") -Destination $releaseStaging
    $releaseSource = Join-Path $releaseStaging "Source"
    New-Item -ItemType Directory -Path $releaseSource | Out-Null
    Copy-Item -LiteralPath (Join-Path $repoRoot "pom.xml"), (Join-Path $repoRoot "README.md"), `
        (Join-Path $repoRoot ".gitignore"), (Join-Path $repoRoot "package-release.ps1") `
        -Destination $releaseSource
    Copy-Item -LiteralPath $sourceDirectory -Destination $releaseSource -Recurse
    Copy-Item -LiteralPath (Join-Path $repoRoot "V-Client") -Destination $releaseSource

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $output = [System.IO.File]::Open($zipStaging, [System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    try {
        $archive = [System.IO.Compression.ZipArchive]::new($output,
            [System.IO.Compression.ZipArchiveMode]::Create, $true)
        try {
            $files = Get-ChildItem -LiteralPath $releaseStaging -File -Recurse -Force
            foreach ($file in $files) {
                $relativePath = $file.FullName.Substring($releaseStaging.Length).TrimStart('\').Replace('\', '/')
                $entry = $archive.CreateEntry("Release/$relativePath",
                    [System.IO.Compression.CompressionLevel]::Optimal)
                $entryStream = $entry.Open()
                $input = [System.IO.File]::Open($file.FullName, [System.IO.FileMode]::Open,
                    [System.IO.FileAccess]::Read,
                    [System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete)
                try {
                    $input.CopyTo($entryStream)
                }
                finally {
                    $input.Dispose()
                    $entryStream.Dispose()
                }
            }
        }
        finally {
            $archive.Dispose()
        }
    }
    finally {
        $output.Dispose()
    }

    $check = [System.IO.Compression.ZipFile]::OpenRead($zipStaging)
    try {
        if ($check.Entries.Count -ne $files.Count) {
            throw "Release ZIP is incomplete: expected $($files.Count) files but found $($check.Entries.Count)."
        }
        foreach ($entry in $check.Entries) {
            $input = $entry.Open()
            try {
                $input.CopyTo([System.IO.Stream]::Null)
            }
            finally {
                $input.Dispose()
            }
        }
    }
    finally {
        $check.Dispose()
    }

    New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
    Get-ChildItem -LiteralPath $releaseStaging -File -Recurse -Force | ForEach-Object {
        $relativePath = $_.FullName.Substring($releaseStaging.Length).TrimStart('\')
        $destination = Join-Path $releaseDirectory $relativePath
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        Copy-Item -LiteralPath $_.FullName -Destination $destination -Force
    }
    if (Test-Path $zipPath) {
        Move-Item -LiteralPath $zipStaging -Destination $zipPath -Force
    } else {
        Move-Item -LiteralPath $zipStaging -Destination $zipPath
    }

    Write-Host ""
    Write-Host "Release ready:"
    Write-Host "  Launcher: $releaseDirectory\V-Client.exe"
    Write-Host "  ZIP:      $zipPath"
    Write-Host "Existing files in Release that are not part of this build were preserved."
}
finally {
    Pop-Location
}
