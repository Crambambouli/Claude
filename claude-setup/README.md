# Claude-Setup — Phase 3: Hybrid-Sicherheits-Architektur

Dieses Verzeichnis enthält die Konfiguration und Scripts für das hybride
LLM-Setup auf Christians Windows-11-Rechner.

**Idee:** Cloud-LLMs für generische Aufgaben, lokales Ollama für alles Sensible.

## Struktur

```
claude-setup/
├── README.md                             ← du bist hier
├── ollama/
│   ├── install-ollama.ps1                Installer + Modell-Download
│   ├── ollama-config.md                  Hardware, Integration, Health-Check
│   └── security.Modelfile                Persistenter Sicherheits-Agent
├── guardrails/
│   ├── data-classification.md            Welche Daten wohin dürfen
│   ├── claude-custom-instructions.md     Einfügen in claude.ai Settings
│   └── chatgpt-custom-instructions.md    Einfügen in ChatGPT Settings
├── agents/
│   ├── claude-programmer.md              Rolle: Code, Architektur
│   ├── chatgpt-editor.md                 Rolle: Lektorat, Brainstorming
│   └── ollama-security.md                Rolle: Deployment, Secrets
└── monitoring/
    ├── cost-check.ps1                    Täglicher Budget-Check
    ├── kill-switch.ps1                   Notfall-Stopp aller LLMs
    └── README.md                         Bedienung + Task-Scheduler
```

## Reihenfolge der Inbetriebnahme

1. **Guardrails zuerst** — Custom Instructions in Claude.ai und ChatGPT
   einpflegen (`guardrails/*.md` als Copy-Paste-Vorlage).
2. **Ollama installieren** — `ollama/install-ollama.ps1` ausführen.
3. **Security-Modell bauen** — `ollama create security -f ollama/security.Modelfile`.
4. **Agent-Rollen lernen** — die drei `agents/*.md` einmal durchlesen,
   damit klar ist welcher LLM was bekommt.
5. **Monitoring scharfschalten** — `monitoring/cost-check.ps1` im Task Scheduler
   registrieren.

## Datenfluss-Entscheidung in 3 Sekunden

```
Steht ein Credential / Key / echter Hostname / privater Pfad im Prompt?
├── JA  → Ollama (lokal)
└── NEIN
    ├── Deutscher Text, Lektorat, Brainstorming?  → ChatGPT
    └── Code, Architektur, Debugging?             → Claude
```

Bei Unsicherheit: **Ollama.** Nie umgekehrt.

## Budget (100 EUR / Monat, Ziel ~40 EUR)

| Posten | EUR/Monat |
|--------|-----------|
| Claude Max (Web) | 20 |
| ChatGPT Plus (Web) | 20 |
| Ollama (lokal) | 0 |
| Reserve (Notfall-API) | 60 |

`monitoring/cost-check.ps1` loggt täglich und warnt bei Überschreitung.

## Fallback-Strategien

| Situation | Plan |
|-----------|------|
| Ollama down | Keine Secrets in Cloud — Task vertagen oder manuell anonymisieren |
| Claude rate-limited | ChatGPT oder Ollama als Notnagel |
| Budget überschritten | `kill-switch.ps1 -RemoveUserEnvKeys` |
| Versehentlicher Key-Leak in Cloud-Chat | Key rotieren + `kill-switch.ps1` |

## Offene Punkte (für nächsten Claude-Chat)

- [ ] Hardware-Benchmark: welches Modell läuft auf Christians GPU zügig?
- [ ] OpenCode/VS-Code-Extensions final auswählen (Continue vs. Cline vs. Copilot).
- [ ] Audit-Logging: sollen Prompts in lokale Datei geschrieben werden?
- [ ] Backup der Modelle (Ollama-Modell-Ordner in tägliches Backup aufnehmen).
