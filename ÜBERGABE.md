# Blitztext – Chat-Übergabe

**Stand:** 21. Juni 2026  
**Letzter Commit:** `2e39d50` – i18n vollständig (24 Sprachen)  
**Branches:** `claude/fix-f8-settings-i9Pe1` = `claude/whisper-cpp-native-stt` (immer beide pushen)  
**Offene PRs:** #12 (`fix-f8-settings-i9Pe1`) · #15 (`whisper-cpp-native-stt`) – beide Draft

---

## Schritt 1: Zuerst lesen

```
/home/user/Claude/CLAUDE.md
```

Enthält bindende Regeln: Immer Deutsch antworten, Umgebungsbeschränkungen, Abkürzungen (VT = Blitztext, OC = OpenCode), Git-Workflow.

---

## Offener Auftrag

**Kein konkreter nächster Schritt vom Nutzer definiert.** Nutzer fragen was als nächstes gewünscht ist.

**Bekannte Kandidaten (Priorität offen):**
1. i18n auf Windows testen – wurde **nur gebaut, nie gestartet**
2. GPU-Beschleunigung für RTX 5070 Ti (Blackwell CC 10.0 braucht Vulkan-Build – noch nicht verfügbar)
3. Eigenes GitHub-Repository für voice-typer (derzeit im Mono-Repo `Crambambouli/Claude`)
4. Installer deployen auf V-Server (`46.225.83.170`)

---

## Umgebung

| Was | Wo |
|-----|----|
| Linux-Sandbox | `/home/user/Claude/` – lesen, schreiben, bauen, committen, pushen |
| App-Pfad (Sandbox) | `/home/user/Claude/voice-typer/` |
| Windows-Repo (Nutzer) | `C:\Users\Christian\voice-typer-repo\voice-typer\` |
| Config (Windows) | `%APPDATA%\Blitztext\config.json` |
| Log (Windows) | `%APPDATA%\Blitztext\voice-typer.log` |
| Modelle (Windows) | `%APPDATA%\Blitztext\models\ggml-small.bin` |

Die Linux-Sandbox hat **kein GUI, keine Windows-APIs, keinen Zugriff auf Logs/Config**. Runtime-Artefakte nur auf Windows. `npm run package` funktioniert in der Sandbox **nicht** (electron-builder braucht Wine).

**Nutzer ist Laie** → PowerShell-Befehle immer vollständig mit `cd`-Schritt angeben:
```powershell
cd C:\Users\Christian\voice-typer-repo\voice-typer
git fetch origin claude/fix-f8-settings-i9Pe1
git reset --hard origin/claude/fix-f8-settings-i9Pe1
npm run dev
```

---

## Was ist fertig (und bestätigt funktionierend)

| Feature | Getestet |
|---------|----------|
| whisper.cpp HTTP-Server statt Python | ✅ Windows bestätigt |
| `npm run setup` lädt `whisper-server.exe` + DLLs | ✅ Windows bestätigt |
| Modell `small` (~3–4 s CPU-Latenz) | ✅ Windows bestätigt |
| Wörterbuch / `initial_prompt` | ✅ Windows bestätigt |
| README.md + HANDBUCH.md | ✅ fertig |
| MIT-Lizenz | ✅ fertig |
| i18n: 24 Sprachen + UI-Integration | ⚠️ Build OK, **Windows ungetestet** |

---

## Dateistruktur (`voice-typer/src/`)

```
src/
├── main.ts              – Hauptprozess, State-Machine, IPC-Routing
├── types.ts             – Settings-Interface + DEFAULT_SETTINGS
├── settings.ts          – JSON-Config-Manager + Schema-Migration
├── whisper.ts           – WhisperService (HTTP-Client für whisper.cpp)
├── model-manager.ts     – Modell-Download von HuggingFace
├── tray.ts              – Tray-Icon + Kontextmenü
├── overlay.ts           – Overlay-Fenster (focusable:false!)
├── hotkey.ts            – Globaler Shortcut-Manager
├── audio-recorder.ts    – Mikrofon via verborgenem BrowserWindow
├── mode-processor.ts    – Normal/Plus/Rage/Emoji via Claude API
├── clipboard-manager.ts – clipboard.writeText() + SendKeys
├── preload.ts           – IPC-Bridge für Renderer
├── logger.ts            – Datei-Logger
├── i18n/
│   ├── index.ts         – loadLanguage(), t(), getAllTranslations()
│   └── *.json           – 24 Sprachdateien (de en fr es it pt nl pl sv no da fi cs sk hu ro bg hr sl et lv lt el tr)
└── renderer/
    ├── overlay.html     – Schwebendes UI (i18n via data-i18n + i18n-update IPC)
    ├── settings.html    – Einstellungs-Dialog (i18n via data-i18n + applyI18n())
    ├── recorder.html    – Unsichtbar, 1×1px, Web Audio API
    └── correction.html  – Ctrl+F9 Korrektur-Editor (⚠️ KEIN i18n – hartcodiertes Deutsch)

scripts/
├── create-icons.js      – Generiert PNG-Tray-Icons + ICO (läuft als prebuild)
├── download-whisper.mjs – Binary-Download von GitHub-Releases (npm run setup)
└── post-build.js        – Kopiert HTML nach dist/renderer/, JSONs nach dist/i18n/

Dead Code:
└── src/window-detection.ts  – Kein Import, kein Aufruf. Nicht anfassen, nicht löschen.
```

---

## Settings (vollständig)

**Datei:** `%APPDATA%\Blitztext\config.json` (+ `_schemaVersion: 1`)

| Key | Typ | Default | Hinweis |
|-----|-----|---------|---------|
| `whisperPath` | string | `""` | Reserviert, leer lassen |
| `whisperModel` | string | `"small"` | tiny/base/small/medium/large-v3/large-v3-q5_0 |
| `whisperLanguage` | string | `"de"` | auto/de/en/fr/es/it/… |
| `whisperPrompt` | string | `""` | Fachbegriffe als Kontext-Hinweis |
| `apiKey` | string | `""` | Anthropic Key für Plus/Rage/Emoji |
| `hotkey` | string | `"Ctrl+F8"` | Electron-Accelerator-Format |
| `audioDevice` | string | `""` | leer = Standard-Mikrofon |
| `ttsVoice` | string | `""` | leer = Systemstandard |
| `uiLanguage` | string | `"de"` | **NEU** – ISO-639-1-Code |
| `ttsReplacements` | object | `{Layer: "Lejer", …}` | Aussprache-Korrekturen für TTS |

**Config-Migration in `settings.ts`:**
- `F8` → `Ctrl+F8` (immer)
- `whisperModel: "base"` → `"small"` (einmalig, _schemaVersion < 1)
- `whisperLanguage: "auto"` → `"de"` (einmalig, _schemaVersion < 1)

---

## WhisperService – kritische Details

```typescript
// Konstruktor
new WhisperService(whisperPath, model='small', language='de', prompt='')

// Binary-Pfad-Logik (binaryPath())
// 1. process.resourcesPath/whisper-server.exe  → Produktion (electron-builder extraResources)
// 2. __dirname/../bin/whisper-server.exe        → Entwicklung (npm run setup)

// Server-Start-Args – KEIN --convert, KEIN --gpu
['-m', modelPath, '--port', '8765', '--host', '127.0.0.1', '-l', lang, '-t', '4']

// language='auto' → wird zu '' in der Transcribe-Request (Server erkennt automatisch)
// Fast-Exit: Prozess beendet sich <10s nach Start → sofortiger Fehler (nicht 120s warten)
// Health-Check: GET http://127.0.0.1:8765/health → {"status":"ok"}
// Transkription: POST /inference, multipart/form-data, Feld "initial_prompt" optional
```

---

## i18n-System

**Modul:** `src/i18n/index.ts` → kompiliert nach `dist/i18n/index.js`  
`__dirname` zeigt **direkt** auf `dist/i18n/` → JSON-Pfad ist `path.join(__dirname, 'de.json')` (kein extra `i18n/`-Segment).

**24 Sprachen:** bg cs da de el en es et fi fr hr hu it lt lv nl no pl pt ro sk sl sv tr  
**55 Keys** – alle Sprachen identisch validiert.

**Datenfluss:**
```
main.ts: loadLanguage(uiLanguage) beim Start
  → getAllTranslations() → IPC 'settings-init' → settings.html: applyI18n()
  → overlay.sendTranslations() → IPC 'i18n-update' → overlay.html: applyI18n()

Sprachwechsel in Einstellungen:
  main.ts: settings-save → loadLanguage(next.uiLanguage) → sendTranslations()
```

**⚠️ Noch nicht i18n-fähig:** `correction.html` (Ctrl+F9) – hartcodiertes Deutsch. Wenn i18n dort gewünscht, muss `correction.html` dieselbe `data-i18n`-Logik + `ipcRenderer.on('i18n-update')` bekommen und `main.ts` muss Übersetzungen beim Öffnen des Korrektur-Fensters senden.

---

## Kritische Implementierungsregeln (NIE ändern)

| Regel | Grund |
|-------|-------|
| `SendKeys "^(v)"` mit Klammern | Ohne Klammern bleibt Ctrl aktiv → nächste Taste wird Ctrl+modifiziert → z.B. Ctrl+W schließt Nutzerfenster |
| `windowsHide: true` bei allen spawn() | Sonst öffnet sich sichtbares Konsolenfenster |
| `focusable: false` beim Overlay | Overlay darf nie Fokus stehlen – Paste-Ziel-Fokus muss erhalten bleiben |
| Hotkey `Ctrl+F8`, nie nacktes `F8` | Nacktes F8 kollidiert mit WH_KEYBOARD_LL-Hooks anderer Apps |
| Tray: kein `rebuild()` in `setState()` | Win32-HMENU-Neuaufbau verursacht Fokus-Interaktionen |

---

## npm-Scripts

```bash
npm run build      # prebuild (Icons) + tsc + postbuild (HTML + i18n-JSONs nach dist/)
npm run dev        # build + electronmon dist/main.js (Hot-Reload)
npm run setup      # whisper-server.exe + DLLs von GitHub-Releases herunterladen
npm run package    # build + electron-builder → installer/Blitztext Setup 1.0.0.exe
npm run clean      # dist/ löschen
npx tsc --noEmit   # nur Type-Check
```

---

## Modell-Download

**Quelle:** HuggingFace `ggerganov/whisper.cpp`  
**Ziel:** `%APPDATA%\Blitztext\models\ggml-<model>.bin`  
**Auslöser:** `ModelManager.ensureModel()` in `main.ts` beim Start (Download nur wenn Datei fehlt)  
**Atom:** Download nach `<datei>.part`, dann `rename()` → kein korrupter Zustand bei Abbruch

| Modell | Größe | CPU-Latenz | GPU-Latenz |
|--------|-------|-----------|-----------|
| `tiny` | ~75 MB | ~1–2 s | <1 s |
| `base` | ~142 MB | ~2–3 s | ~1 s |
| `small` *(Standard)* | ~244 MB | ~3–4 s | ~1 s |
| `medium` | ~769 MB | ~6–10 s | ~2 s |
| `large-v3` | ~1,5 GB | ~10 s | ~2 s |

---

## GPU-Status (RTX 5070 Ti)

GIGABYTE GeForce RTX 5070 Ti Gaming OC 16G – Blackwell-Architektur (Compute Capability 10.0).  
**Problem:** CUDA 12.8+ benötigt, aktuelle whisper.cpp-Releases nur CUDA 12.4 (CUBLAS).  
**Workaround:** BLAS-Build (CPU-optimiert) – ~10 s mit medium, ~3–4 s mit small.  
**Lösung wenn verfügbar:** `scripts/download-whisper.mjs` → `pickAsset()` bevorzugt Vulkan (Priorität 1) – sobald ggml-org/whisper.cpp ein Vulkan-Windows-Asset released, wird es automatisch geladen.

---

## Git-Workflow

```bash
# Vor jedem Commit: Author setzen (Stop-Hook prüft dies)
git config user.email noreply@anthropic.com
git config user.name Claude

git add -A
git commit -m "feat/fix/docs: ..."

# Immer auf BEIDE Branches pushen
git push -u origin claude/whisper-cpp-native-stt
git push -u origin claude/whisper-cpp-native-stt:claude/fix-f8-settings-i9Pe1

# Nach Push: Draft-PR erstellen falls keiner existiert (via mcp__github__create_pull_request)
# Repo: owner=crambambouli, repo=claude, base=main
```

---

## Installer deployen (nur auf Windows)

```powershell
cd C:\Users\Christian\voice-typer-repo\voice-typer
npm run package
scp -i C:/Nofak/.ssh/id_ed25519_ws "installer\Blitztext Setup 1.0.0.exe" deployer@46.225.83.170:/var/www/downloads/
```

---

## Schnell-Checkliste für neue Session

- [ ] `CLAUDE.md` gelesen?
- [ ] Nutzer gefragt, was als nächstes gewünscht ist?
- [ ] Bei Commits: `git config user.email noreply@anthropic.com && git config user.name Claude`
- [ ] Immer auf beide Branches pushen
- [ ] `correction.html` hat noch kein i18n – falls Übersetzung gewünscht, explizit umsetzen
