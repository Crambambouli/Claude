# Erstellt "Blitztext.lnk" auf dem Desktop → danach Rechtsklick → "An Taskleiste anheften"
$appDir     = "$env:USERPROFILE\voice-typer-repo\voice-typer"
$electron   = "$appDir\node_modules\electron\dist\electron.exe"
$mainJs     = "$appDir\dist\main.js"
$icon       = "$appDir\assets\icons\app.ico"
$linkPath   = "$env:USERPROFILE\Desktop\Blitztext.lnk"

$wsh        = New-Object -ComObject WScript.Shell
$lnk        = $wsh.CreateShortcut($linkPath)
$lnk.TargetPath       = $electron
$lnk.Arguments        = "`"$mainJs`""
$lnk.WorkingDirectory = $appDir
$lnk.IconLocation     = $icon
$lnk.Description      = "Blitztext – Sprachsteuerung"
$lnk.Save()

Write-Host ""
Write-Host "  Blitztext.lnk auf dem Desktop erstellt." -ForegroundColor Green
Write-Host ""
Write-Host "  Jetzt:" -ForegroundColor Cyan
Write-Host "  1. Desktop-Verknuepfung 'Blitztext' suchen"
Write-Host "  2. Rechtsklick → 'An Taskleiste anheften'"
Write-Host ""
