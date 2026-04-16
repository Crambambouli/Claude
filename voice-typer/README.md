# Voice Typer

Electron + TypeScript Desktop-App für Windows.  
Drücke **F8** → spreche → drücke **F8** erneut → Text erscheint im aktiven Textfeld.

---

## Voraussetzungen

### 1. Node.js ≥ 18
https://nodejs.org/

### 2. Python-Whisper (STT)
```bash
# Python ≥ 3.9 erforderlich
pip install openai-whisper
# Whisper lädt beim ersten Start das Base-Modell (~150 MB) herunter
```

> **Alternativ**: [whisper.cpp](https://github.com/ggerganov/whisper.cpp) – kompilieren  
> und den Pfad zum Binary in den Einstellungen hinterlegen.

### 3. ffmpeg (Whisper-Abhängigkeit)
```bash
# Windows (winget)
winget install ffmpeg
# oder: https://ffmpeg.org/download.html → Bin in PATH eintragen
```

### 4. Anthropic API Key (optional)
Nur nötig für die Modi **Plus**, **Rage** und **Emoji**.  
https://console.anthropic.com/

---

## Setup & Start

```bash
# 1. Abhängigkeiten installieren
npm install

# 2. Icons generieren + kompilieren + starten
npm run dev
```

Beim ersten Start erscheint ein Tray-Icon in der Windows-Taskleiste.  
Über Rechtsklick → **Einstellungen** können alle Parameter konfiguriert werden.

---

## Dev-Workflow (Hot-Reload)

```bash
# Terminal 1: TypeScript kompilieren (beobachtend)
npm run watch

# Terminal 2: Electron mit Auto-Restart
npx electronmon dist/main.js
```

---

## Verzeichnisstruktur

```
voice-typer/
├── assets/icons/          PNG-Icons (erzeugt durch scripts/create-icons.js)
├── scripts/
│   ├── create-icons.js    Generiert die drei Tray-Icons
│   └── post-build.js      Kopiert renderer/*.html nach dist/renderer/
├── src/
│   ├── main.ts            Electron-Hauptprozess – Orchestrierung
│   ├── preload.ts         contextBridge für das Settings-Fenster
│   ├── tray.ts            Tray-Icon & Kontextmenü
│   ├── hotkey.ts          Globaler F8-Shortcut (electron.globalShortcut)
│   ├── audio-recorder.ts  Audio-Aufnahme via verstecktem BrowserWindow
│   ├── whisper.ts         Whisper-CLI-Wrapper (child_process)
│   ├── modes.ts           Textmodus-Verarbeitung (Normal/Plus/Rage/Emoji)
│   ├── window-detection.ts  Aktives Fenster per PowerShell ermitteln
│   ├── clipboard-manager.ts Clipboard + Ctrl+V via PowerShell
│   ├── settings.ts        JSON-basierter Konfigurations-Manager
│   ├── logger.ts          Datei- & Konsolen-Logger
│   ├── types.ts           Gemeinsame TypeScript-Typen
│   └── renderer/
│       ├── recorder.html  Verstecktes Fenster für Mikrofon-Zugriff
│       └── settings.html  Einstellungs-Dialog
├── package.json
└── tsconfig.json
```

---

## Modi

| Modus    | Beschreibung                                          | API |
|----------|-------------------------------------------------------|-----|
| Normal   | Rohtext genau wie gesprochen                         | –   |
| Plus     | Höflich & professionell umformuliert                 | ✓   |
| Rage     | Aggressive Diktate → sachliche, höfliche Nachricht   | ✓   |
| Emoji    | Passende Emojis werden hinzugefügt                   | ✓   |

---

## Einstellungen

`%APPDATA%\voice-typer\config.json`

| Key               | Default  | Beschreibung                            |
|-------------------|----------|-----------------------------------------|
| `whisperPath`     | `""`     | Pfad zum Whisper-Binary / Verzeichnis   |
| `whisperModel`    | `"base"` | Whisper-Modell (tiny/base/small/medium) |
| `whisperLanguage` | `"auto"` | Sprache (auto/de/en/…)                  |
| `apiKey`          | `""`     | Anthropic API Key                       |
| `hotkey`          | `"F8"`   | Globaler Hotkey                         |
| `audioDevice`     | `""`     | Mikrofon-Geräte-ID (leer = Standard)    |

---

## Logs

`%APPDATA%\voice-typer\voice-typer.log`  
Über **Einstellungen → Log öffnen** direkt einsehbar.

---

## Build / Packaging

```bash
npm run package
# → release/ enthält NSIS-Installer für Windows x64
```

---

## Bekannte Hinweise

- **Erster Whisper-Aufruf** kann 20–30 s dauern (Modell-Download & Python-Startup).  
  Danach ist es deutlich schneller (~2–5 s für kurze Diktate).

- **Tray-Icon** erscheint nach dem Start in der Windows-Taskleiste (ggf. im  
  versteckten Bereich → Pfeil anklicken → Icon sichtbar machen).

- Der **Hotkey F8** kann in den Einstellungen auf eine beliebige Taste  
  (z.B. eine Mouse-Button-Zuweisung über Gaming-Software) geändert werden.

- Für **optimale Whisper-Qualität**: Mikrofon-Eingangspegel 70–80 %, kein Echo.
