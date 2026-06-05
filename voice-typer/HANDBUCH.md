# Blitztext – Benutzerhandbuch

## Inhaltsverzeichnis

1. [Was ist Blitztext?](#was-ist-blitztext)
2. [Installation](#installation)
3. [Erste Schritte](#erste-schritte)
4. [Das Overlay](#das-overlay)
5. [Aufnahme und Diktat](#aufnahme-und-diktat)
6. [Modi](#modi)
7. [Wörterbuch und Lernfunktion](#wörterbuch-und-lernfunktion)
8. [Einstellungen](#einstellungen)
9. [TTS – Text vorlesen](#tts--text-vorlesen)
10. [Fehlerbehebung](#fehlerbehebung)

---

## Was ist Blitztext?

Blitztext wandelt gesprochene Sprache in Text um und fügt ihn direkt in das aktive Fenster ein – egal ob E-Mail, Texteditor, Browser oder Chat. Die gesamte Verarbeitung läuft lokal auf deinem Rechner. Es werden keine Audiodaten übertragen.

---

## Installation

### Voraussetzungen

- Windows 10 oder 11 (64-Bit)
- Node.js 20 oder neuer ([nodejs.org](https://nodejs.org))
- Mikrofon

### Schritt für Schritt

1. **Repository herunterladen**
   ```powershell
   git clone https://github.com/Crambambouli/Claude.git
   cd Claude\voice-typer
   ```

2. **Abhängigkeiten installieren**
   ```powershell
   npm install
   ```

3. **whisper-server.exe herunterladen**
   ```powershell
   npm run setup
   ```
   Lädt die whisper.cpp-Binaries automatisch herunter.

4. **App starten**
   ```powershell
   npm run dev
   ```
   Beim ersten Start wird das Sprachmodell (~244 MB) automatisch heruntergeladen.

---

## Erste Schritte

Nach dem Start erscheint das **Blitztext-Overlay** auf dem Bildschirm und ein Icon im System-Tray (Benachrichtigungsbereich).

1. Klicke in ein beliebiges Textfeld (z. B. E-Mail, Word, Browser)
2. Drücke **Ctrl+F8** (oder deinen konfigurierten Hotkey)
3. Sprich deinen Text – das Overlay zeigt „Aufnahme"
4. Drücke **Ctrl+F8** erneut zum Stoppen
5. Nach kurzer Verarbeitung (~3–4 Sekunden) erscheint der Text im Textfeld

---

## Das Overlay

Das Overlay ist ein schwebendes Fenster, das immer sichtbar bleibt, aber nie den Fokus stiehlt.

| Element | Funktion |
|---------|---------|
| Mikrofon-Button | Aufnahme starten / stoppen |
| Modus-Anzeige | Aktueller Modus (Normal, Plus, Rage, Emoji) |
| Lautsprecher-Button | Letzten Text vorlesen |
| Einstellungs-Button | Einstellungen öffnen |
| END-Button | App beenden |

Das Overlay lässt sich an jede Bildschirmposition ziehen.

---

## Aufnahme und Diktat

### Tipps für gute Erkennung

- **Deutlich und in normalem Tempo** sprechen
- **Kurze Pausen** zwischen Sätzen helfen der Erkennung
- **Fachbegriffe** im Wörterbuch eintragen (Einstellungen → Wörterbuch)
- **Sprache auf „Automatisch"** stellen wenn Deutsch und Englisch gemischt gesprochen wird

### Sprache einstellen

- **Automatisch** – empfohlen für Deutsch/Englisch-Mix
- **Deutsch** – für rein deutschsprachige Diktate
- **Englisch** – für rein englischsprachige Diktate

---

## Modi

Der Modus bestimmt, wie der erkannte Text verarbeitet wird:

| Modus | Verarbeitung | API nötig |
|-------|-------------|-----------|
| **Normal** | Text wird direkt eingefügt | Nein |
| **Plus** | Text wird von Claude KI verbessert und formatiert | Ja |
| **Rage** | Text wird in freundlichere Formulierung umgewandelt | Ja |
| **Emoji** | Text wird mit passenden Emojis ergänzt | Ja |

Modi Plus, Rage und Emoji benötigen einen **Anthropic API-Key**, der in den Einstellungen eingetragen werden kann.

Den Modus wechseln: im Overlay auf den Modus-Namen klicken oder per Tray-Menü.

---

## Wörterbuch und Lernfunktion

### Wörterbuch (Einstellungen)

Trage Fachbegriffe, Namen und Fremdwörter ein, die whisper.cpp kennen soll. Diese werden als Kontext-Hinweis bei jeder Transkription übergeben:

*Beispiel:* `Cadris, Blitztext, OpenCode, HuggingFace`

### Lernfunktion (Ctrl+F9)

Nach einem Diktat, das einen Fehler enthält:

1. **Ctrl+F9** drücken – der Korrektur-Editor öffnet sich
2. Den erkannten Text korrigieren
3. **Speichern** – die App merkt sich das Wort-Paar dauerhaft
4. Bei zukünftigen Diktaten wird die Korrektur automatisch angewendet

Gelernte Korrekturen sind in den Einstellungen einsehbar und einzeln löschbar.

---

## Einstellungen

Erreichbar über: Tray-Icon → rechte Maustaste → **Einstellungen**

### Whisper STT

| Einstellung | Beschreibung |
|-------------|-------------|
| **Modell** | Qualität vs. Geschwindigkeit (siehe unten) |
| **Sprache** | Automatisch / Deutsch / Englisch / … |
| **Wörterbuch** | Fachbegriffe für bessere Erkennung |

#### Verfügbare Modelle

| Modell | Latenz (CPU) | Latenz (GPU) | Qualität |
|--------|-------------|-------------|---------|
| `tiny` | ~1–2 s | <1 s | Basis |
| `base` | ~2–3 s | ~1 s | Gut |
| `small` | ~3–4 s | ~1 s | Sehr gut *(Standard)* |
| `medium` | ~6–10 s | ~2 s | Exzellent |
| `large-v3` | ~10 s | ~2 s | Beste Qualität |

Nach einem Modellwechsel muss die App neu gestartet werden. Das neue Modell wird automatisch heruntergeladen.

### Hotkey

Standard: `Ctrl+F8`. Weitere Beispiele: `F9`, `Alt+F8`, `Ctrl+Shift+R`.

### Mikrofon

Auswahl des Aufnahmegeräts. Leer = Standard-Mikrofon des Systems.

### Claude API

Für die Modi Plus, Rage und Emoji. Key unter [console.anthropic.com](https://console.anthropic.com) erstellen.

---

## TTS – Text vorlesen

Der **Lautsprecher-Button** im Overlay liest den zuletzt erkannten Text vor.

- **Pause/Fortsetzen**: ⏸-Button erscheint während des Vorlesens
- **Stimme wählen**: Einstellungen → TTS Stimme
- **Aussprache-Korrekturen**: Englische Begriffe können phonetisch eingetragen werden (z. B. `Layer → Lejer`)

---

## Fehlerbehebung

### App startet nicht / „Zweite Instanz erkannt"

Eine andere Blitztext-Instanz läuft noch. Beenden über:
```powershell
Get-Process -Name "Blitztext","electron" -ErrorAction SilentlyContinue | Stop-Process -Force
```

### Whisper-Server startet nicht

```powershell
cd voice-typer
Remove-Item -Recurse -Force bin
npm run setup
npm run dev
```

### Modell fehlt / wird neu heruntergeladen

Das Modell liegt unter `%APPDATA%\Blitztext\models\`. Bei einem Modellwechsel in den Einstellungen wird das neue Modell beim nächsten Start automatisch heruntergeladen.

### Log-Datei

Für detaillierte Fehleranalyse:
```powershell
Get-Content "$env:APPDATA\Blitztext\voice-typer.log" -Tail 50
```

---

*Blitztext – Open Source, lokal, datenschutzfreundlich.*
