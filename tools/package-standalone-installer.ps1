$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$compiler = Join-Path $env:WINDIR "Microsoft.NET\Framework64\v4.0.30319\csc.exe"
$source = Join-Path $PSScriptRoot "StandaloneInstaller.cs"
$payload = Join-Path $repoRoot "AzyrClient.zip"
$icon = Join-Path $repoRoot "src\main\resources\vclient.ico"
$output = Join-Path $repoRoot "website\VSMPClientSetup.exe"

foreach ($path in @($compiler, $source, $payload, $icon)) {
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
    /out:$output `
    $source
if ($LASTEXITCODE -ne 0) {
    throw "Could not compile the standalone Windows setup executable."
}

$verification = Start-Process -FilePath $output -ArgumentList "--verify" -Wait -PassThru
if ($verification.ExitCode -ne 0) {
    throw "The generated setup executable failed its embedded bundle verification."
}

Write-Host "Standalone installer ready: $output"
