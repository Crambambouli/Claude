# Monitoring & Kill-Switch

## cost-check.ps1

Liest die Fixkosten der Web-Abos, loggt täglich, warnt bei Budget-Überschreitung.

```powershell
cd claude-setup\monitoring
powershell -ExecutionPolicy Bypass -File .\cost-check.ps1
```

**Automatisierung via Task Scheduler:**

```powershell
$action  = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$PWD\cost-check.ps1`""
$trigger = New-ScheduledTaskTrigger -Daily -At 08:00
Register-ScheduledTask -TaskName 'Blitztext-LLM-Cost-Check' `
    -Action $action -Trigger $trigger -Description 'Tägliches LLM-Budget-Check'
```

Log landet unter `%APPDATA%\Blitztext\cost-check.log`.

## kill-switch.ps1

Notfall-Stopp. Drei Aktionen, jeweils mit Bestätigung:

1. API-Keys aus aktueller Session entfernen.
2. Lokale Ollama-Prozesse beenden.
3. Optional User-Env-Keys dauerhaft löschen (Flag `-RemoveUserEnvKeys`).

```powershell
# Interaktiv (fragt nach)
.\kill-switch.ps1

# Automatisch ja zu allem außer User-Env
.\kill-switch.ps1 -Force

# Auch User-Env-Keys dauerhaft entfernen
.\kill-switch.ps1 -Force -RemoveUserEnvKeys
```

**Wann nutzen?**

- Verdacht auf Key-Leak (Key wurde in Cloud-LLM geklebt).
- Budget überschritten und automatisches Abrechnen droht.
- Vor Rechner-Übergabe an Dritte.
- Test der Fallback-Strategie.

## Endlosschleife-Prevention

Claude Code selbst hat keine Endlosschleife im klassischen Sinn, aber:

- **Agent-Limits:** Agent-Aufrufe kosten Tokens. Bei Auto-Mode Budget im Auge behalten.
- **Watchdog:** Wenn ein Claude-Code-Prozess länger als 30 min läuft ohne Output → manuell stoppen.
- **Hooks:** Ein SessionStart-Hook mit Timeout schützt vor hängenden Initialisierungen
  (siehe `.claude/settings.json`).
