# Blitztext Setup – Alles in einem Script
# Speichern als setup.ps1, Rechtsklick → "Mit PowerShell ausführen"

$ErrorActionPreference = 'Stop'   # Jeder Fehler bricht ab

$repo    = "$env:USERPROFILE\voice-typer-repo"
$appDir  = "$repo\voice-typer"
$desktop = "$env:USERPROFILE\Desktop"
$lnk     = "$desktop\Blitztext.lnk"

function Write-Step($msg) {
  Write-Host ""
  Write-Host ">> $msg" -ForegroundColor Cyan
}

# ── 1. Repo klonen oder updaten ──────────────────────────────────────────────
Write-Step "Schritt 1: Repo prüfen / klonen"
if (Test-Path "$repo\.git") {
    Write-Host "Repo vorhanden – update..." -ForegroundColor Yellow
    Set-Location $repo
    git fetch origin claude/electron-voice-app-KdI1i
    git checkout claude/electron-voice-app-KdI1i
    git pull origin claude/electron-voice-app-KdI1i
} else {
    Write-Host "Klone Repository..." -ForegroundColor Yellow
    git clone --branch claude/electron-voice-app-KdI1i `
        https://github.com/Crambambouli/Claude.git $repo
}

if (-not (Test-Path $appDir)) {
    Write-Host "FEHLER: App-Verzeichnis nicht gefunden: $appDir" -ForegroundColor Red
    Read-Host "Eingabe zum Beenden"
    exit 1
}
Set-Location $appDir

# ── 2. npm install ───────────────────────────────────────────────────────────
Write-Step "Schritt 2: npm install"
npm install
if ($LASTEXITCODE -ne 0) {
    Write-Host "FEHLER: npm install fehlgeschlagen (Code $LASTEXITCODE)" -ForegroundColor Red
    Read-Host "Eingabe zum Beenden"
    exit 1
}

# ── 3. Build ─────────────────────────────────────────────────────────────────
Write-Step "Schritt 3: npm run build"
npm run build
if ($LASTEXITCODE -ne 0) {
    Write-Host "FEHLER: Build fehlgeschlagen (Code $LASTEXITCODE)" -ForegroundColor Red
    Read-Host "Eingabe zum Beenden"
    exit 1
}

$mainJs = "$appDir\dist\main.js"
if (-not (Test-Path $mainJs)) {
    Write-Host "FEHLER: dist\main.js fehlt nach Build!" -ForegroundColor Red
    Read-Host "Eingabe zum Beenden"
    exit 1
}
Write-Host "dist\main.js vorhanden. Build erfolgreich." -ForegroundColor Green

# ── 4. Desktop-Verknüpfung erstellen ────────────────────────────────────────
Write-Step "Schritt 4: Verknüpfung erstellen"
$electron = "$appDir\node_modules\electron\dist\electron.exe"
$ico      = "$appDir\assets\icons\app.ico"

if (-not (Test-Path $electron)) {
    Write-Host "WARNUNG: electron.exe nicht gefunden unter $electron" -ForegroundColor Yellow
}

$wsh = New-Object -ComObject WScript.Shell
$sc  = $wsh.CreateShortcut($lnk)
$sc.TargetPath       = $electron
$sc.Arguments        = "`"$mainJs`""
$sc.WorkingDirectory = $appDir
if (Test-Path $ico) { $sc.IconLocation = $ico }
$sc.Description      = "Blitztext – Spracherkennung"
$sc.Save()
Write-Host "Verknüpfung erstellt: $lnk" -ForegroundColor Green

# Auch ins Startmenü kopieren
$startMenu = "$env:APPDATA\Microsoft\Windows\Start Menu\Programs"
try {
    Copy-Item $lnk "$startMenu\Blitztext.lnk" -Force
    Write-Host "Ins Startmenü kopiert." -ForegroundColor Green
} catch {
    Write-Host "Startmenü-Kopie übersprungen: $_" -ForegroundColor Yellow
}

# ── 5. Fertig ────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "==========================================" -ForegroundColor Green
Write-Host " Fertig! Blitztext.lnk liegt auf dem Desktop." -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Log-Datei (nach Start): $env:APPDATA\Blitztext\voice-typer.log" -ForegroundColor Yellow
Write-Host ""
Write-Host "Starte Blitztext..." -ForegroundColor Cyan

explorer.exe $desktop
Start-Process $electron -ArgumentList "`"$mainJs`"" -WorkingDirectory $appDir
