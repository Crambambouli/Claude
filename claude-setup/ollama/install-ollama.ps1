# install-ollama.ps1
# Installiert Ollama auf Windows 11 und zieht die gewünschten Modelle.
# Ausführung: Rechtsklick → "Mit PowerShell ausführen" ODER
#             powershell -ExecutionPolicy Bypass -File .\install-ollama.ps1

[CmdletBinding()]
param(
    [string[]]$Models = @('mistral', 'codellama', 'dolphin-mixtral'),
    [switch]$SkipInstall,
    [switch]$StartService
)

$ErrorActionPreference = 'Stop'

function Write-Step($msg) {
    Write-Host "==> $msg" -ForegroundColor Cyan
}

function Test-OllamaInstalled {
    $cmd = Get-Command ollama -ErrorAction SilentlyContinue
    return [bool]$cmd
}

# ----------------------------------------------------------------------
# 1) Installation
# ----------------------------------------------------------------------
if (-not $SkipInstall -and -not (Test-OllamaInstalled)) {
    Write-Step "Ollama wird heruntergeladen und installiert"
    $installer = Join-Path $env:TEMP 'OllamaSetup.exe'
    Invoke-WebRequest -Uri 'https://ollama.com/download/OllamaSetup.exe' -OutFile $installer -UseBasicParsing
    Start-Process -FilePath $installer -ArgumentList '/S' -Wait
    Remove-Item $installer -Force

    # PATH in laufender Session auffrischen
    $env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ';' +
                [System.Environment]::GetEnvironmentVariable('Path','User')
} else {
    Write-Step "Ollama ist bereits installiert (übersprungen)"
}

if (-not (Test-OllamaInstalled)) {
    throw "Ollama wurde nicht gefunden. Bitte Shell neu öffnen und Script erneut starten."
}

# ----------------------------------------------------------------------
# 2) Service starten (optional)
# ----------------------------------------------------------------------
if ($StartService) {
    Write-Step "Starte Ollama-Server (im Hintergrund)"
    Start-Process -FilePath 'ollama' -ArgumentList 'serve' -WindowStyle Hidden
    Start-Sleep -Seconds 3
}

# ----------------------------------------------------------------------
# 3) Modelle ziehen
# ----------------------------------------------------------------------
foreach ($model in $Models) {
    Write-Step "Lade Modell: $model"
    try {
        ollama pull $model
    } catch {
        Write-Warning "Konnte $model nicht laden: $_"
    }
}

# ----------------------------------------------------------------------
# 4) Smoke-Test
# ----------------------------------------------------------------------
Write-Step "Smoke-Test mit $($Models[0])"
$response = ollama run $Models[0] "Antworte nur mit: OK" 2>&1
Write-Host $response

Write-Step "Fertig. Verfügbare Modelle:"
ollama list
