# Blitztext – Lokale Spracheingabe für Windows

Blitztext ist eine lokale, datenschutzfreundliche Spracheingabe-App für Windows. Gesprochener Text wird per Hotkey aufgenommen, lokal transkribiert und automatisch in das aktive Fenster eingefügt – ohne Cloud, ohne Internetverbindung, ohne Datenweitergabe.

## Funktionen

- **Globaler Hotkey** (Standard: `Ctrl+F8`) – startet und stoppt die Aufnahme aus jeder Anwendung heraus
- **Lokale KI-Transkription** via [whisper.cpp](https://github.com/ggml-org/whisper.cpp) – läuft vollständig auf dem eigenen Rechner
- **Deutsch/Englisch-Mix** – automatische Spracherkennung, konfigurierbar
- **Wörterbuch** – Fachbegriffe und Namen eintragen für bessere Erkennungsgenauigkeit
- **Lernfunktion** – Erkennungsfehler per `Ctrl+F9` korrigieren, die App merkt sich die Korrekturen dauerhaft
- **Modi**: Normal · Plus · Rage · Emoji (erfordern Anthropic API-Key)
- **TTS** – erkannten Text vorlesen lassen
- **Overlay** – schwebendes Statusfenster, stiehlt nie den Fokus

## Systemvoraussetzungen

- Windows 10 / 11 (64-Bit)
- Node.js 20 oder neuer
- Ca. 500 MB freier Speicherplatz (Modell + Binaries)
- Mikrofon

## Installation

```powershell
# 1. Repository klonen
git clone https://github.com/Crambambouli/Claude.git
cd Claude\voice-typer

# 2. Abhängigkeiten installieren
npm install

# 3. whisper-server.exe herunterladen
npm run setup

# 4. App starten
npm run dev
```

Beim ersten Start lädt Blitztext das Whisper-Sprachmodell automatisch herunter (~244 MB für `small`).

## Einstellungen

Erreichbar über das Tray-Icon (rechte Maustaste → Einstellungen):

| Einstellung | Beschreibung |
|-------------|-------------|
| Modell | `small` (Standard, ~3–4 s Latenz) bis `large-v3` (~2 s mit GPU) |
| Sprache | Automatisch (für Deutsch/Englisch-Mix empfohlen) |
| Wörterbuch | Fachbegriffe und Namen für bessere Erkennung |
| Hotkey | Frei wählbar, Standard: `Ctrl+F8` |
| Mikrofon | Auswahl des Aufnahmegeräts |

## Installer bauen

```powershell
npm run package
```

Erstellt `installer\Blitztext Setup 1.0.0.exe` (NSIS-Installer, Windows x64).

## Lizenz

MIT – siehe [LICENSE](LICENSE)
