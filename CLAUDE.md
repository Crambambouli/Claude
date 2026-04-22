# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Arbeitssprache

**Immer Deutsch antworten.** Der Nutzer kommuniziert auf Deutsch.

### Abkürzungen
| Kürzel | Bedeutung |
|--------|-----------|
| VT | Voice Typer = Blitztext (der Produktname der App) |
| OC | OpenCode (die IDE des Nutzers) |

---

## Umgebung

- **Nutzer arbeitet auf Windows 11** — dort liegt das Git-Repo, dort wird installiert, gebaut, gestartet und getestet.
- **Claude läuft in einer Linux-Sandbox** (`/home/user/Claude/…`) die **nur** Code lesen/schreiben und committen/pushen kann. Sie ist **nicht** dein Windows-PC.
- **Was hier funktioniert:** Code-Änderungen, `npm install`, `npm run build` (TypeScript-Kompilierung), `git` / GitHub-Operationen.
- **Was hier nicht funktioniert:** `npm run package` (electron-builder braucht Wine für den Windows-Installer), `npm start` (keine GUI), Zugriff auf Logs/Config.
- **Runtime-Artefakte sind nicht erreichbar:** `%APPDATA%\Blitztext\voice-typer.log`, `%APPDATA%\Blitztext\config.json` liegen ausschließlich auf dem Windows-Rechner. Nicht danach fragen, stattdessen den Code selbst analysieren.
- **Arbeits-Flow:** Claude ändert Code → pusht Branch → Nutzer zieht auf Windows 11 → `npm install` + `npm run dev` (oder `npm run package`).

---

## Zusammenarbeit & Unterlagen — Quelle der Wahrheit ist das Repo

**Alles Dauerhafte gehört ins Repo.** Claude hat **keinen** Zugriff auf Windows-Pfade (`C:\…`), OneDrive, lokale Notiz-Ordner oder externe Tools. Jedes Dokument außerhalb des Repos ist für Claude unsichtbar.

- **Projektunterlagen** (Briefings, Konzepte, Handover, Deployment-Notizen, Architektur-Entscheidungen, Tabu-Listen, offene-Fragen-Listen) liegen als Markdown im Repo unter `docs/` — mit sprechenden Unterordnern wie `docs/handover/`, `docs/deployment/`, `docs/konzept/`, `docs/cadris/`.
- **Keine Chat-Dumps großer Dokumente.** Statt einen Briefing-Inhalt in den Chat zu kopieren: Datei ins Repo committen, pushen, dann im Chat referenzieren („siehe `docs/handover/BRIEFING_LANDINGPAGE_DEPLOYMENT.md`"). So bleibt alles versioniert, diffbar und wiederauffindbar.
- **Verweise auf `C:\…`-Pfade sind Sackgassen.** Wenn Claude eine Referenz darauf bekommt, fordert sie den Transfer ins Repo ein, bevor sie weiterarbeitet — besonders bei Deployment-, Security- oder Produktentscheidungen.
- **Auch Credentials-Metadaten gehören dokumentiert** (welche Secrets existieren, wofür, wo im Workflow benutzt) — **niemals die Werte selbst**. Werte ausschließlich als GitHub Secrets.
- **Neue Repos** (z.B. `crambambouli/cadris`) bekommen eine eigene `CLAUDE.md` mit denselben Prinzipien.

---

## Projekt

**Pfad:** `voice-typer/`  
**Typ:** Electron 30 + TypeScript  
**Produkt:** Blitztext (`com.blitztext.app`)  
**Ziel:** Windows x64, NSIS-Installer

### Build-Befehle (auf dem Server, `cd voice-typer/`)

```bash
npm install            # Abhängigkeiten (einmalig)
npm run dev            # Kompilieren + Electron starten (Hot-Reload via electronmon)
npm run watch          # TypeScript nur beobachten (Terminal 1 für Dev-Workflow)
npx electronmon dist/main.js  # Electron mit Auto-Restart (Terminal 2 für Dev-Workflow)
npm run build          # Einmaliger TypeScript-Build (kein Start)
npm run package        # Installer bauen → installer/Blitztext-Setup-1.0.0.exe
npx tsc --noEmit --ignoreDeprecations 6.0  # Type-Check ohne Ausgabe
```

> `tsc --noEmit` schlägt fehl wenn `node_modules` fehlt (electron/node types). Das ist normal auf dem Server, weil `npm install` Windows-native-Bindings braucht. Die Syntax der eigenen TS-Dateien manuell prüfen reicht.

---

## Architektur

### Prozesse

```
Main Process (main.ts)
 ├── TrayManager      – Tray-Icon + Kontextmenü (kein rebuild() bei setState, sonst Fokus-Flackern)
 ├── OverlayManager   – Schwebendes Overlay-Fenster (focusable: false – stehlt nie den Fokus)
 ├── HotkeyManager    – Globaler Shortcut via electron.globalShortcut
 ├── AudioRecorder    – Verstecktes BrowserWindow (1×1px) für MediaRecorder/Web Audio
 ├── WhisperService   – Python-HTTP-Server (Port 8765) + CLI-Fallback
 ├── ModeProcessor    – Normal (kein API), Plus/Rage/Emoji (Anthropic claude-haiku-4-5-20251001)
 ├── ClipboardManager – clipboard.writeText() + WScript.Shell SendKeys "^(v)"
 └── SettingsManager  – JSON-Config in %APPDATA%\Blitztext\config.json

Renderer-Prozesse (alle src/renderer/)
 ├── overlay.html    – Sichtbares Overlay (IPC: overlay-toggle-recording, overlay-set-mode, overlay-exit, overlay-hide, overlay-minimize)
 ├── recorder.html   – Unsichtbar; Mikrofon + Web Audio Beep; IPC: start-recording, stop-recording, recording-complete, play-beep
 └── settings.html   – Einstellungs-Dialog
```

### Aufnahme-Flow (happy path)

1. Hotkey / Mic-Button → `handleHotkey()` → `startRecording()`
2. Beep 880 Hz, `AudioRecorder.start()` → `recorder.html` startet MediaRecorder
3. Zweiter Hotkey / Mic-Button → `stopAndProcess()`
4. Beep 440 Hz, `AudioRecorder.stop()` → WAV-Buffer
5. RMS-Check (< 0.005 → verwerfen), Whisper-Transkription
6. Halluzinations-Filter (`stripHallucinations`)
7. `ModeProcessor.process()` (Normal: direkt; andere: Anthropic API)
8. `clipboard.writeText(result)`, 300ms warten, `simulatePaste()`

### IPC-Kanäle (Main ↔ Renderer)

| Kanal | Richtung | Zweck |
|-------|----------|-------|
| `update-state` | Main→Overlay | State + Mode + letzter Text |
| `overlay-toggle-recording` | Overlay→Main | Aufnahme starten/stoppen |
| `overlay-set-mode` | Overlay→Main | Modus wechseln |
| `overlay-exit` | Overlay→Main | App beenden |
| `start-recording` / `stop-recording` | Main→Recorder | Aufnahme steuern |
| `recording-complete` | Recorder→Main | WAV-Buffer liefern |
| `play-beep` | Main→Recorder | Ton abspielen |
| `settings-get` / `settings-save` | Settings→Main | Config lesen/schreiben |

---

## Kritische Implementierungsdetails

### Paste (clipboard-manager.ts)
`SendKeys "^(v)"` — **Klammern sind zwingend**. Ohne Klammern bleibt der Ctrl-Modifier in WSH/SendKeys gelegentlich aktiv; der nächste Keystroke wird Ctrl-modifiziert (z.B. Ctrl+W) und schließt das aktive Fenster des Nutzers. Nie zu `"^v"` zurückändern.

### Python-Prozesse (whisper.ts)
Alle `spawn()`/`spawnSync()`-Aufrufe für Python **müssen** `windowsHide: true` enthalten, sonst öffnet sich ein sichtbares Konsolfenster.

### Tray (tray.ts)
`setState()` ruft **kein** `rebuild()` auf — Win32-HMENU-Neuaufbau löst Fokus-Interaktionen aus, die das aktive Fenster stören können. Nur Icon und Tooltip werden aktualisiert.

### Overlay (overlay.ts)
`focusable: false` ist absichtlich gesetzt. Das Overlay darf nie den Fokus stehlen, damit der Paste-Ziel-Fokus erhalten bleibt.

### Hotkey
Standard-Hotkey: `Ctrl+F8`. Der nackte `F8`-Hotkey kollidiert mit `WH_KEYBOARD_LL`-Hooks anderer Apps und kann Fenster schließen. Config-Migration `F8 → Ctrl+F8` läuft automatisch in `settings.ts::load()`.

### Whisper-Modi
- **Server-Modus** (bevorzugt): `whisper_server.py` läuft auf Port 8765, Modell bleibt im RAM
- **CLI-Fallback**: wenn Server nicht verfügbar oder explizit anderes Binary konfiguriert
- `faster-whisper` wird als Python-Paket erwartet (`pip install faster-whisper`)

---

## Dead Code

`window-detection.ts` — kein Import, kein Aufruf mehr. Kann gelöscht werden.

---

## Einstellungen

Config-Pfad (Windows): `%APPDATA%\Blitztext\config.json`  
Log-Pfad (Windows): `%APPDATA%\Blitztext\voice-typer.log` (Fallback: `%TEMP%\blitztext.log`)

| Key | Default | Hinweis |
|-----|---------|---------|
| `hotkey` | `"Ctrl+F8"` | Electron-Accelerator-Format |
| `whisperPath` | `""` | leer = Server-Modus mit whisper_server.py |
| `whisperModel` | `"base"` | tiny/base/small/medium |
| `whisperLanguage` | `"auto"` | auto/de/en/… |
| `apiKey` | `""` | Anthropic Key für Modi Plus/Rage/Emoji |
| `audioDevice` | `""` | leer = Standard-Mikrofon |

---

## Git-Workflow

- Entwicklungs-Branch: `claude/<feature-name>` (vom Harness vorgegeben)
- Nach jedem Push immer einen Draft-PR erstellen falls noch keiner existiert
- Commit-Messages auf Deutsch oder Englisch, präzise (Ursache + Lösung)
