# Datenschutz-Matrix (Hybrid-LLM)

Verbindliche Regel für jede LLM-Interaktion. Bei Unsicherheit gilt: **Lokal statt Cloud.**

## Klassifikationsstufen

| Stufe | Beispiele | Erlaubt bei |
|-------|-----------|-------------|
| **🟢 Öffentlich** | Generischer Code, öffentliche APIs, Tutorials, Public-Repo-Code | Claude Web, ChatGPT Web, Ollama |
| **🟡 Intern** | Architektur-Skizzen, Pseudo-Code ohne Hostnames, Lektorat-Texte | Claude Web, ChatGPT Web (anonymisiert), Ollama |
| **🔴 Vertraulich** | Credentials, API-Keys, SSH-Schlüssel, echte Hostnames/IPs, Kundendaten, Deployment-Scripts mit Secrets | **NUR Ollama** |

## Konkrete Entscheidungstabelle

| Datenkategorie | Cloud? | Ollama? | Begründung |
|---|---|---|---|
| API-Keys, Tokens, Passwörter | ❌ | ✅ | Anbieter-Speicherung unzumutbar |
| SSH-Keys, `.env`-Dateien | ❌ | ✅ | Gleiche Gefahrenklasse |
| V-Server-IP `46.225.83.170`, SSH-User `deployer` | ❌ | ✅ | Angriffsfläche |
| Pfade unter `C:\Nofak\` | ❌ | ✅ | Privater Ordner |
| Deployment-Scripts (konkret) | ❌ | ✅ | Enthalten typisch Secrets |
| Code-Review (anonymisiert) | ✅ | ✅ | Generischer Code ist ok |
| Architektur-Fragen | ✅ | ✅ | Design ist meist generisch |
| Lektorat deutscher Texte | ✅ | ✅ | ChatGPT Plus ist hier stark |
| Öffentliche Repo-Snippets | ✅ | ✅ | Bereits öffentlich |
| Interne Projekt-Roadmap | ❌ | ✅ | Geschäftsgeheimnis |

## Anonymisierung (falls Cloud-LLM zwingend nötig)

Vor dem Prompt-Eingeben ersetzen:

```
echte IP        → 203.0.113.10         (Doku-IP nach RFC 5737)
echter Host     → server.example.com
echter User     → alice
echter Pfad     → C:\Projects\MyApp
echte Domain    → example.com
Passwort/Token  → <REDACTED> oder xxxxx
```

Danach Prompt **nochmal** durchlesen, bevor Enter gedrückt wird.

## Red Flags (Stopp-Signale)

Wenn du einen dieser Begriffe im Prompt siehst und ihn an eine Cloud-LLM schicken willst — **abbrechen** und Ollama verwenden:

- `password`, `passwort`, `secret`, `token`, `api_key`, `apikey`
- `BEGIN PRIVATE KEY`, `BEGIN OPENSSH PRIVATE KEY`
- `ssh deployer@`, `scp`, `rsync ... @`
- `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`
- Konkrete IP-Adressen außerhalb von `192.168.*`, `10.*`, `127.*`
- Inhalt aus `.env`, `config.json` mit Secrets, `id_ed25519*`
