$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$compiler = Join-Path $env:WINDIR "Microsoft.NET\Framework64\v4.0.30319\csc.exe"
$source = Join-Path $PSScriptRoot "StandaloneInstaller.cs"
$payload = Join-Path $repoRoot "AzyrClient.zip"
$uninstallScript = Join-Path $PSScriptRoot "uninstall.ps1"
$icon = Join-Path $repoRoot "src\main\resources\vclient.ico"
$output = Join-Path $repoRoot "website\VSMPClientSetup.exe"

foreach ($path in @($compiler, $source, $payload, $icon, $uninstallScript)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required installer input is missing: $path"
    }
}

& $compiler `
    /nologo `
    /target:winexe `
    /platform:anycpu `
    /win32icon:$icon `
    /reference:System.IO.Compression.dll `
    /reference:System.IO.Compression.FileSystem.dll `
    /reference:System.Windows.Forms.dll `
    /reference:System.Drawing.dll `
    /resource:$payload,VSMPClient.Payload.zip `
    /resource:$uninstallScript,VSMPClient.Uninstall.ps1 `
    /out:$output `
    $source
if ($LASTEXITCODE -ne 0) {
    throw "Could not compile the standalone Windows setup executable."
}

$verification = Start-Process -FilePath $output -ArgumentList "--verify" -Wait -PassThru
if ($verification.ExitCode -ne 0) {
    throw "The generated setup executable failed its embedded bundle verification."
}

# Tell running launchers a new version exists: the backend serves this file at /api/launcher/update.
$appInfo = Get-Content -Raw (Join-Path $repoRoot "src/main/java/com/vclient/AppInfo.java")
$version = [regex]::Match($appInfo, 'VERSION = "([^"]+)"').Groups[1].Value
$notes = if ($env:RELEASE_NOTES) { $env:RELEASE_NOTES } else { "Bug fixes and improvements." }
$update = [ordered]@{
    version      = $version
    url          = "/VSMPClientSetup.exe"
    sha256       = (Get-FileHash -Algorithm SHA256 $output).Hash.ToLowerInvariant()
    size         = (Get-Item $output).Length
    notes        = $notes
    mandatory    = $false
    minSupported = "0.0.0"
    publishedAt  = (Get-Date).ToUniversalTime().ToString("o")
}
$updateJson = ($update | ConvertTo-Json) + [Environment]::NewLine
[System.IO.File]::WriteAllText((Join-Path $repoRoot "website/update.json"), $updateJson, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "Standalone installer ready: $output ($version)"
