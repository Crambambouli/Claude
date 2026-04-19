# Blitztext

Electron + TypeScript Desktop-App für Windows (interner Code-Name: *voice-typer*).  
Drücke **Ctrl+F8** → spreche → drücke **Ctrl+F8** erneut → Text erscheint im aktiven Textfeld.

---

## Voraussetzungen

### 1. Node.js ≥ 18
https://nodejs.org/

### 2. Python-Whisper (STT)
```bash
# Python ≥ 3.9 erforderlich
pip install faster-whisper
# Das Modell wird beim ersten Start geladen (~150 MB für "base")
```

> **Alternativ**: `pip install openai-whisper` (wird als Fallback erkannt)  
> oder [whisper.cpp](https://github.com/ggerganov/whisper.cpp) – Pfad zum Binary in den Einstellungen hinterlegen.

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
│   ├── hotkey.ts          Globaler Shortcut (Default Ctrl+F8, electron.globalShortcut)
│   ├── audio-recorder.ts  Audio-Aufnahme via verstecktem BrowserWindow
│   ├── whisper.ts         Whisper-Server (Port 8765) + CLI-Fallback
│   ├── modes.ts           Textmodus-Verarbeitung (Normal/Plus/Rage/Emoji)
│   ├── clipboard-manager.ts Clipboard + Ctrl+V via WScript.Shell
│   ├── correction-manager.ts Korrektur-Datenbank (LCS-Diff, Ctrl+F9)
│   ├── tts-manager.ts     Text-to-Speech via Windows SAPI
│   ├── settings.ts        JSON-basierter Konfigurations-Manager
│   ├── logger.ts          Datei- & Konsolen-Logger
│   ├── types.ts           Gemeinsame TypeScript-Typen
│   └── renderer/
│       ├── overlay.html   Schwebendes Overlay-Fenster
│       ├── recorder.html  Verstecktes Fenster für Mikrofon-Zugriff
│       ├── correction.html Korrektur-Editor (Ctrl+F9)
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

`%APPDATA%\Blitztext\config.json`

| Key               | Default      | Beschreibung                            |
|-------------------|--------------|-----------------------------------------|
| `whisperPath`     | `""`         | Pfad zum Whisper-Binary / Verzeichnis (leer = Server-Modus) |
| `whisperModel`    | `"base"`     | Whisper-Modell (tiny/base/small/medium) |
| `whisperLanguage` | `"auto"`     | Sprache (auto/de/en/…)                  |
| `apiKey`          | `""`         | Anthropic API Key                       |
| `hotkey`          | `"Ctrl+F8"`  | Globaler Hotkey (Electron-Accelerator)  |
| `audioDevice`     | `""`         | Mikrofon-Geräte-ID (leer = Standard)    |
| `ttsVoice`        | `""`         | SAPI-Stimme (leer = Systemstimme)       |

Gelernte Korrekturen: `%APPDATA%\Blitztext\corrections.json`  
Editor öffnen: nach einem Diktat **Ctrl+F9** drücken.

---

## Logs

`%APPDATA%\Blitztext\voice-typer.log`  
Über **Einstellungen → Log öffnen** direkt einsehbar.

---

## Build / Packaging

```bash
npm run package
# → installer/ enthält NSIS-Installer für Windows x64
```

---

## Bekannte Hinweise

- **Erster Whisper-Aufruf** kann 20–30 s dauern (Modell-Download & Python-Startup).  
  Danach ist es deutlich schneller (~2–5 s für kurze Diktate).

- **Tray-Icon** erscheint nach dem Start in der Windows-Taskleiste (ggf. im  
  versteckten Bereich → Pfeil anklicken → Icon sichtbar machen).

- Der **Hotkey Ctrl+F8** kann in den Einstellungen auf eine beliebige Taste  
  (z.B. eine Mouse-Button-Zuweisung über Gaming-Software) geändert werden.

- Für **optimale Whisper-Qualität**: Mikrofon-Eingangspegel 70–80 %, kein Echo.
