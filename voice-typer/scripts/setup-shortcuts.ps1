# Erstellt Desktop-Verknüpfung und optionalen Windows-Autostart für Blitztext
$appDir = Split-Path -Parent $PSScriptRoot

# ─── Desktop-Verknüpfung ────────────────────────────────────────────────────
$electronExe = Join-Path $appDir "node_modules\electron\dist\electron.exe"
$mainJs      = Join-Path $appDir "dist\main.js"
$iconPath    = Join-Path $appDir "assets\icons\app.ico"
$shortcutPath = "$env:USERPROFILE\Desktop\Blitztext.lnk"

$wsh      = New-Object -ComObject WScript.Shell
$shortcut = $wsh.CreateShortcut($shortcutPath)
$shortcut.TargetPath       = $electronExe
$shortcut.Arguments        = "`"$mainJs`""
$shortcut.WorkingDirectory = $appDir
$shortcut.IconLocation     = $iconPath
$shortcut.Description      = "Blitztext – Ctrl+F8 zum Diktieren"
$shortcut.Save()
Write-Host "Desktop-Verknüpfung erstellt: $shortcutPath" -ForegroundColor Green

# ─── Autostart (optional) ────────────────────────────────────────────────────
$answer = Read-Host "`nMit Windows automatisch starten? (j/n)"
if ($answer -eq 'j') {
    $startupDir  = "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Startup"
    $startupLink = Join-Path $startupDir "Blitztext.lnk"
    $sc2 = $wsh.CreateShortcut($startupLink)
    $sc2.TargetPath       = $electronExe
    $sc2.Arguments        = "`"$mainJs`""
    $sc2.WorkingDirectory = $appDir
    $sc2.IconLocation     = $iconPath
    $sc2.Save()
    Write-Host "Autostart eingerichtet: $startupLink" -ForegroundColor Green
} else {
    Write-Host "Autostart übersprungen." -ForegroundColor Gray
}

Write-Host "`nFertig! Blitztext jetzt über Desktop-Verknüpfung starten." -ForegroundColor Cyan
