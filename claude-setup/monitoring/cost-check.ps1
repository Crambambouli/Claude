# cost-check.ps1
# Täglicher Budget-Check für das LLM-Setup.
# Web-Abos sind Fixkosten und werden nur manuell bestätigt; API-Usage wird
# optional abgefragt (wenn Keys in Env gesetzt sind).
#
# Ausführung: powershell -ExecutionPolicy Bypass -File .\cost-check.ps1
# Automatisieren: Windows Task Scheduler → täglich 08:00.

[CmdletBinding()]
param(
    [double]$BudgetEUR = 100.0,
    [string]$LogPath   = "$env:APPDATA\Blitztext\cost-check.log"
)

$ErrorActionPreference = 'Stop'
$today = Get-Date -Format 'yyyy-MM-dd HH:mm'

# ----------------------------------------------------------------------
# 1) Fixkosten (Web-Abos)
# ----------------------------------------------------------------------
$fixedCosts = [ordered]@{
    'Claude Max (Web)'   = 20.00
    'ChatGPT Plus (Web)' = 20.00
    'Ollama (lokal)'     = 0.00
}
$fixedTotalUSD = ($fixedCosts.Values | Measure-Object -Sum).Sum
$fixedTotalEUR = [math]::Round($fixedTotalUSD * 0.92, 2)   # grober USD→EUR

Write-Host "===== LLM-Kosten-Check $today =====" -ForegroundColor Cyan
Write-Host ""
Write-Host "Fixkosten (Web-Abos):" -ForegroundColor Yellow
foreach ($k in $fixedCosts.Keys) {
    "  {0,-22}  {1,6:F2} USD" -f $k, $fixedCosts[$k] | Write-Host
}
Write-Host ("  {0,-22}  {1,6:F2} EUR (ca.)" -f 'Summe', $fixedTotalEUR) -ForegroundColor Green

# ----------------------------------------------------------------------
# 2) Optional: API-Usage (nur wenn Keys gesetzt sind)
# ----------------------------------------------------------------------
$apiTotalUSD = 0.0

if ($env:ANTHROPIC_API_KEY) {
    Write-Host ""
    Write-Host "Anthropic-API-Key gesetzt — manuelle Prüfung empfohlen:" -ForegroundColor Yellow
    Write-Host "  https://console.anthropic.com/settings/usage"
}

if ($env:OPENAI_API_KEY) {
    Write-Host ""
    Write-Host "OpenAI-API-Key gesetzt — manuelle Prüfung empfohlen:" -ForegroundColor Yellow
    Write-Host "  https://platform.openai.com/usage"
}

# ----------------------------------------------------------------------
# 3) Budget-Ampel
# ----------------------------------------------------------------------
$totalEUR = $fixedTotalEUR + [math]::Round($apiTotalUSD * 0.92, 2)
$remain   = $BudgetEUR - $totalEUR
$pct      = [math]::Round(($totalEUR / $BudgetEUR) * 100, 1)

Write-Host ""
Write-Host "Budget:     $BudgetEUR EUR / Monat" -ForegroundColor Cyan
Write-Host "Verbraucht: $totalEUR EUR ($pct %)"
Write-Host "Rest:       $remain EUR" -ForegroundColor $(if ($remain -lt 20) { 'Red' } elseif ($remain -lt 40) { 'Yellow' } else { 'Green' })

if ($remain -lt 0) {
    Write-Warning "BUDGET ÜBERSCHRITTEN. Kill-Switch erwägen: .\kill-switch.ps1"
}

# ----------------------------------------------------------------------
# 4) Logging
# ----------------------------------------------------------------------
$logDir = Split-Path $LogPath -Parent
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

"$today | total=$totalEUR EUR | remain=$remain EUR | pct=$pct%" |
    Out-File -FilePath $LogPath -Append -Encoding utf8

Write-Host ""
Write-Host "Log: $LogPath"
