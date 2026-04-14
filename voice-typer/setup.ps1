# Blitztext Setup – Alles in einem Script
# Speichern als setup.ps1, Rechtsklick → "Mit PowerShell ausführen"

$repo    = "$env:USERPROFILE\voice-typer-repo"
$appDir  = "$repo\voice-typer"
$desktop = "$env:USERPROFILE\Desktop"
$lnk     = "$desktop\Blitztext.lnk"

# ── 1. Repo klonen oder updaten ──────────────────────────────────────────────
if (Test-Path "$repo\.git") {
    Write-Host "Repo vorhanden – Update..." -ForegroundColor Cyan
    Set-Location $repo
    git pull
} else {
    Write-Host "Repo klonen..." -ForegroundColor Cyan
    git clone --branch claude/electron-voice-app-KdI1i https://github.com/Crambambouli/Claude.git $repo
}

# ── 2. Abhängigkeiten + Build ────────────────────────────────────────────────
Set-Location $appDir
Write-Host "npm install..." -ForegroundColor Cyan
npm install
Write-Host "npm run build..." -ForegroundColor Cyan
npm run build

# ── 3. Desktop-Verknüpfung erstellen ────────────────────────────────────────
$electron = "$appDir\node_modules\electron\dist\electron.exe"
$mainJs   = "$appDir\dist\main.js"
$ico      = "$appDir\assets\icons\app.ico"

$wsh = New-Object -ComObject WScript.Shell
$sc  = $wsh.CreateShortcut($lnk)
$sc.TargetPath       = $electron
$sc.Arguments        = "`"$mainJs`""
$sc.WorkingDirectory = $appDir
$sc.IconLocation     = $ico
$sc.Description      = "Blitztext – Spracherkennung"
$sc.Save()

Write-Host ""
Write-Host "Fertig! Blitztext.lnk liegt auf dem Desktop." -ForegroundColor Green
Write-Host ""
Write-Host "Taskleiste: Rechtsklick auf 'Blitztext' → 'An Taskleiste anheften'" -ForegroundColor Yellow
Write-Host "(Falls Option fehlt: Shortcut in C:\ProgramData\Microsoft\Windows\Start Menu\Programs kopieren)" -ForegroundColor Gray
Write-Host ""

# Desktop öffnen damit Verknüpfung sichtbar ist
explorer.exe $desktop

# App direkt starten
Write-Host "Starte Blitztext..." -ForegroundColor Cyan
Start-Process $electron -ArgumentList "`"$mainJs`"" -WorkingDirectory $appDir
