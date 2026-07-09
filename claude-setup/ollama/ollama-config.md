# Ollama Setup — Blitztext / Christian

Lokale LLM-Schiene für sensible Daten (Credentials, Deployment, interne Projekte).
Läuft ausschließlich auf Windows 11. Keine Daten verlassen den Rechner.

## Hardware-Empfehlung

| Modell | Größe | RAM min. | VRAM empfohlen | Zweck |
|--------|-------|----------|----------------|-------|
| `mistral` | 7B | 8 GB | 6 GB | Allrounder, Deutsch ok |
| `codellama` | 7B | 8 GB | 6 GB | Code-Completion, Refactoring |
| `dolphin-mixtral` | 8x7B | 48 GB | 24 GB | Qualität, wenn Hardware reicht |
| `neural-chat` | 7B | 8 GB | 6 GB | Chat / Brainstorming |
| `llama3.1` | 8B | 10 GB | 8 GB | Moderner Allrounder |

**Standard-Auswahl (ohne GPU):** `mistral` + `codellama` — beide quantisiert (Q4_0) unter 5 GB pro Modell.

## Installation

```powershell
# Aus diesem Repo:
cd claude-setup\ollama
powershell -ExecutionPolicy Bypass -File .\install-ollama.ps1

# Nur bestimmte Modelle:
powershell -ExecutionPolicy Bypass -File .\install-ollama.ps1 -Models mistral,codellama
```

Installer läuft silent (`/S`), Modelle werden per `ollama pull` nachgezogen.

## Service-Betrieb

Ollama läuft standardmäßig auf `http://127.0.0.1:11434`. Der Service startet
automatisch via Systray-Icon, kann aber auch manuell gesteuert werden:

```powershell
# Manuell starten
ollama serve

# Im Hintergrund
Start-Process ollama -ArgumentList serve -WindowStyle Hidden

# Status prüfen
Invoke-RestMethod http://127.0.0.1:11434/api/tags
```

## Speicherort der Modelle

Default: `%USERPROFILE%\.ollama\models`

Verschieben (z.B. auf D:\) per Umgebungsvariable:

```powershell
[Environment]::SetEnvironmentVariable('OLLAMA_MODELS', 'D:\Ollama\models', 'User')
# Danach Ollama-Tray beenden und neu starten.
```

## Integration in OpenCode (VS Code)

Ollama spricht eine OpenAI-kompatible API. Kompatible Extensions:

- **Continue** (`continue.continue`) — Chat + Autocomplete, direkte Ollama-Anbindung
- **Cline** (`saoudrizwan.claude-dev`) — Agent-Mode, unterstützt Ollama-Endpoints
- **Ollama Autocoder** (`10nates.ollama-autocoder`) — Tab-Completion

Continue-Config-Beispiel (`~/.continue/config.json`):

```json
{
  "models": [
    {
      "title": "Ollama (Mistral)",
      "provider": "ollama",
      "model": "mistral",
      "apiBase": "http://127.0.0.1:11434"
    },
    {
      "title": "Ollama (CodeLlama)",
      "provider": "ollama",
      "model": "codellama",
      "apiBase": "http://127.0.0.1:11434"
    }
  ]
}
```

## Health-Check

```powershell
# Ist der Server erreichbar?
Test-NetConnection 127.0.0.1 -Port 11434

# Welche Modelle sind geladen?
ollama list

# Kurzer Lauftest
ollama run mistral "Antworte nur mit OK"
```

## Fallback-Strategie

Wenn Ollama down ist:

1. **Keine** sensiblen Daten in Cloud-LLMs eingeben.
2. Task auf Notizzettel verschieben, bis Ollama wieder läuft.
3. Alternativ: Daten manuell anonymisieren (Keys durch `<REDACTED>` ersetzen),
   erst dann Cloud-LLM nutzen.

## Deinstallation

```powershell
# Über Windows → Apps & Features → "Ollama" deinstallieren
# Modelle liegen separat und werden NICHT mitgelöscht:
Remove-Item "$env:USERPROFILE\.ollama" -Recurse -Force
```
