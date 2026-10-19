# Removes V-Client. Windows runs this from Settings > Apps (or Start > right-click > Uninstall).
# Your worlds, accounts and settings in %APPDATA%\V-Client are kept so a reinstall picks up where you left off.
param(
    [string]$InstallDir = (Split-Path -Parent $MyInvocation.MyCommand.Path),
    [string]$KeyName = "VSMPClient",
    [switch]$Quiet
)

# Run from a temporary copy so the install folder (which holds this file) can be deleted.
if ($PSCommandPath -and -not $PSCommandPath.StartsWith($env:TEMP, [StringComparison]::OrdinalIgnoreCase)) {
    $copy = Join-Path $env:TEMP "vclient-uninstall.ps1"
    Copy-Item -LiteralPath $PSCommandPath -Destination $copy -Force
    $arguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", $copy,
        "-InstallDir", $InstallDir, "-KeyName", $KeyName)
    if ($Quiet) { $arguments += "-Quiet" }
    Start-Process -FilePath "powershell.exe" -ArgumentList $arguments
    exit 0
}

Add-Type -AssemblyName System.Windows.Forms
function Say([string]$text, [string]$icon = "Information") {
    if (-not $Quiet) { [void][System.Windows.Forms.MessageBox]::Show($text, "V-Client", "OK", $icon) }
}

if (-not $Quiet) {
    $answer = [System.Windows.Forms.MessageBox]::Show(
        "Remove V-Client from this computer?`n`nYour worlds, accounts and settings in %APPDATA%\V-Client are kept.",
        "Uninstall V-Client", "YesNo", "Question")
    if ($answer -ne "Yes") { exit 0 }
}

try {
    # Only stop the launcher that lives in the folder being removed.
    Get-Process -Name "V-Client" -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path.StartsWith($InstallDir, [StringComparison]::OrdinalIgnoreCase) } | Stop-Process -Force
    Start-Sleep -Milliseconds 800

    $startMenu = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\V-Client.lnk"
    $desktop = Join-Path ([Environment]::GetFolderPath("Desktop")) "V-Client.lnk"
    if (Test-Path -LiteralPath $InstallDir) {
        Remove-Item -LiteralPath $startMenu, $desktop -Force -ErrorAction SilentlyContinue
    }

    if (Test-Path -LiteralPath $InstallDir) { Remove-Item -LiteralPath $InstallDir -Recurse -Force }
    Remove-Item -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\$KeyName" -Recurse -Force -ErrorAction SilentlyContinue

    Say "V-Client was removed. Your game data in %APPDATA%\V-Client was kept."
} catch {
    Say ("V-Client could not be fully removed:`n`n" + $_.Exception.Message + "`n`nClose V-Client and try again.") "Error"
    exit 1
}
