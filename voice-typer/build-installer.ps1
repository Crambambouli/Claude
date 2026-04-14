# Blitztext – Installer bauen
# Erzeugt: installer\Blitztext Setup 1.0.0.exe
# Voraussetzungen: Node.js, Git, Python (mit faster-whisper oder openai-whisper)

$ErrorActionPreference = 'Stop'

$repo   = "$env:USERPROFILE\voice-typer-repo"
$appDir = "$repo\voice-typer"

function Write-Step($msg) {
  Write-Host ""
  Write-Host ">> $msg" -ForegroundColor Cyan
}

# ── 1. Neueste Version holen ──────────────────────────────────────────────────
Write-Step "Schritt 1: Neueste Version laden"
if (Test-Path "$repo\.git") {
  Set-Location $repo
  git fetch origin claude/electron-voice-app-KdI1i
  git checkout claude/electron-voice-app-KdI1i
  git pull origin claude/electron-voice-app-KdI1i
} else {
  Write-Host "Klone Repository..." -ForegroundColor Yellow
  git clone --branch claude/electron-voice-app-KdI1i `
    https://github.com/Crambambouli/Claude.git $repo
}

Set-Location $appDir

# ── 2. Abhängigkeiten ─────────────────────────────────────────────────────────
Write-Step "Schritt 2: npm install"
npm install
if ($LASTEXITCODE -ne 0) { Write-Host "FEHLER: npm install" -ForegroundColor Red; Read-Host; exit 1 }

# ── 3. TypeScript bauen ───────────────────────────────────────────────────────
Write-Step "Schritt 3: npm run build"
npm run build
if ($LASTEXITCODE -ne 0) { Write-Host "FEHLER: Build" -ForegroundColor Red; Read-Host; exit 1 }

if (-not (Test-Path ".\dist\main.js")) {
  Write-Host "FEHLER: dist\main.js fehlt!" -ForegroundColor Red
  Read-Host; exit 1
}

# ── 4. Installer erstellen ────────────────────────────────────────────────────
Write-Step "Schritt 4: Installer bauen (electron-builder)  –  dauert ca. 2-5 Min."
Write-Host "Lädt Electron-Binaries herunter falls nötig..." -ForegroundColor Yellow
npx electron-builder --win --x64
if ($LASTEXITCODE -ne 0) { Write-Host "FEHLER: electron-builder" -ForegroundColor Red; Read-Host; exit 1 }

# ── 5. Ergebnis ───────────────────────────────────────────────────────────────
$installer = Get-ChildItem ".\installer\*.exe" -ErrorAction SilentlyContinue | Select-Object -First 1

if ($installer) {
  Write-Host ""
  Write-Host "==========================================" -ForegroundColor Green
  Write-Host " Installer fertig:" -ForegroundColor Green
  Write-Host " $($installer.FullName)" -ForegroundColor White
  Write-Host " Größe: $([math]::Round($installer.Length/1MB, 1)) MB" -ForegroundColor White
  Write-Host "==========================================" -ForegroundColor Green
  Write-Host ""
  Write-Host "Den Installer kannst Du auf jedem Windows-PC ausführen." -ForegroundColor Yellow
  explorer.exe ".\installer"
} else {
  Write-Host "Installer nicht gefunden – prüfe den installer\ Ordner." -ForegroundColor Red
}
