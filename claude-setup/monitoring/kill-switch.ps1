# kill-switch.ps1
# Notfall-Shutdown: trennt alle LLM-Verbindungen, entfernt API-Keys
# aus der aktuellen Session und stoppt lokale Ollama-Prozesse.
#
# Der Kill-Switch ist BEWUSST nicht automatisch — er fragt zweimal nach,
# bevor er etwas tut. Damit kein versehentliches Ausführen stört.
#
# Aufruf: powershell -ExecutionPolicy Bypass -File .\kill-switch.ps1

[CmdletBinding()]
param(
    [switch]$Force,
    [switch]$RemoveUserEnvKeys
)

$ErrorActionPreference = 'Continue'

function Confirm-Action($msg) {
    if ($Force) { return $true }
    $r = Read-Host "$msg  [yes/NO]"
    return ($r -eq 'yes')
}

Write-Host "=== LLM KILL-SWITCH ===" -ForegroundColor Red
Write-Host "Dieser Vorgang:"
Write-Host "  1. Entfernt ANTHROPIC_API_KEY und OPENAI_API_KEY aus der Session"
Write-Host "  2. Stoppt lokale Ollama-Prozesse"
Write-Host "  3. Optional: Löscht die Keys auch aus den User-Env-Variablen"
Write-Host ""

if (-not (Confirm-Action "Wirklich fortfahren?")) {
    Write-Host "Abgebrochen." -ForegroundColor Yellow
    exit 0
}

# ----------------------------------------------------------------------
# 1) Session-Env clearen
# ----------------------------------------------------------------------
Write-Host "[1/3] Entferne API-Keys aus aktueller Session..." -ForegroundColor Cyan
foreach ($k in @('ANTHROPIC_API_KEY', 'OPENAI_API_KEY', 'OLLAMA_HOST')) {
    if (Test-Path "Env:\$k") {
        Remove-Item "Env:\$k" -ErrorAction SilentlyContinue
        Write-Host "  - $k entfernt"
    }
}

# ----------------------------------------------------------------------
# 2) Ollama-Prozesse beenden
# ----------------------------------------------------------------------
Write-Host "[2/3] Stoppe Ollama-Prozesse..." -ForegroundColor Cyan
$procs = Get-Process -Name 'ollama*' -ErrorAction SilentlyContinue
if ($procs) {
    $procs | Stop-Process -Force -ErrorAction SilentlyContinue
    Write-Host "  - $($procs.Count) Prozess(e) beendet"
} else {
    Write-Host "  - kein Ollama aktiv"
}

# ----------------------------------------------------------------------
# 3) User-Env-Keys (optional, hart)
# ----------------------------------------------------------------------
Write-Host "[3/3] User-Env-Variablen..." -ForegroundColor Cyan

if ($RemoveUserEnvKeys) {
    if (Confirm-Action "User-Env-Keys DAUERHAFT entfernen?") {
        foreach ($k in @('ANTHROPIC_API_KEY', 'OPENAI_API_KEY')) {
            [Environment]::SetEnvironmentVariable($k, $null, 'User')
            Write-Host "  - $k aus User-Env entfernt"
        }
    } else {
        Write-Host "  - User-Env unverändert (abgebrochen)"
    }
} else {
    Write-Host "  - übersprungen (Flag -RemoveUserEnvKeys nicht gesetzt)"
}

Write-Host ""
Write-Host "Kill-Switch fertig." -ForegroundColor Green
Write-Host "Neue Shells öffnen, um Env-Änderungen komplett zu spüren."
