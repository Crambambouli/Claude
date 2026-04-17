# Puzzle Android

Jigsaw-Puzzle-App für Android (Kotlin + Jetpack Compose) — App-ID `puzzle_android`, Version **0.1.0**.

Zielgerät: **Samsung SM-T500** (Android 12+, API 31+), gebaut mit **Gradle 8.7**.

---

## Features

| Feature | Implementierung |
|---|---|
| Setup-Screen | Kategorie, Stil, Teileanzahl wählen |
| Puzzle-Screen | Jigsaw-Teile per Drag & Drop platzieren |
| Jigsaw-Formen | Kubische Bezier-Kurven (TAB / BLANK / FLAT) |
| Ablage (Tray) | Scrollbare `LazyVerticalGrid` rechts (25% Breite) |
| Spielfeld (Board) | Canvas links (75% Breite), Snap-to-Grid |
| Bilderzeugung | Assets → Pollinations.ai → TestImageGenerator (Fallback) |
| State-Management | MVVM + `StateFlow`, `AndroidViewModel` |
| Dark Mode | Dynamic Colour (API 31+) + statische M3-Palette |

---

## Voraussetzungen

| Werkzeug | Mindestversion |
|---|---|
| Android Studio | Hedgehog (2023.1.1) oder neuer |
| JDK | 17 |
| Android SDK | API 34 (compile) / API 31 (min) |
| Gradle | 8.7 (via Wrapper — kein lokales Install nötig) |

---

## Setup

### 1. Repository klonen

```bash
git clone <repo-url>
cd puzzle_android
```

### 2. Android-SDK-Pfad konfigurieren

```bash
cp local.properties.template local.properties
```

`local.properties` bearbeiten:

```properties
# macOS
sdk.dir=/Users/<name>/Library/Android/sdk

# Linux
sdk.dir=/home/<name>/Android/Sdk

# Windows
sdk.dir=C\:\\Users\\<name>\\AppData\\Local\\Android\\Sdk
```

> Android Studio setzt dies beim Öffnen automatisch.

### 3. Eigenes Puzzle-Bild hinterlegen (optional)

Eine Datei `puzzle_image.jpg` in folgendes Verzeichnis legen:

```
puzzle_android/app/src/main/assets/puzzle_image.jpg
```

Wird diese Datei gefunden, nutzt die App sie als Puzzle-Bild.  
Fehlt sie, wird automatisch ein Bild von Pollinations.ai heruntergeladen  
(oder ein farbiges Testbild als letzter Fallback).

---

## Build

```bash
# Debug-APK
./gradlew assembleDebug

# Vollständiger Build (Kompilieren + Lint + Tests)
./gradlew build
```

APK-Ausgabepfad:

```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Installation auf dem Tablet (Samsung SM-T500)

### Schritt 1 – Entwickleroptionen aktivieren

1. Einstellungen → Über das Tablet → **Buildnummer** 7× tippen.
2. Einstellungen → Entwickleroptionen → **USB-Debugging** einschalten.

### Schritt 2a – Via Android Studio (empfohlen)

1. `puzzle_android/` in Android Studio öffnen.
2. Tablet per USB verbinden und „USB-Debugging zulassen" bestätigen.
3. Tablet in der Toolbar auswählen → ▶ **Run**.

### Schritt 2b – Via ADB (Kommandozeile)

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Schritt 2c – APK manuell übertragen (kein USB nötig)

1. `./gradlew assembleDebug` ausführen.
2. `app/build/outputs/apk/debug/app-debug.apk` per E-Mail oder Cloud-Dienst aufs Tablet übertragen.
3. Auf dem Tablet: Einstellungen → **Installation aus unbekannten Quellen** für den Browser/Dateimanager erlauben → APK tippen → Installieren.

### Schritt 2d – APK aus GitHub Actions CI herunterladen

Nach jedem Push baut die CI automatisch eine Debug-APK:

1. GitHub → Repository → **Actions** → letzten erfolgreichen Build öffnen.
2. Unter **Artifacts** → `puzzle-android-debug-apk` herunterladen.
3. ZIP entpacken → APK wie in Schritt 2c auf dem Tablet installieren.

---

## CI / GitHub Actions

Workflow: `.github/workflows/build-puzzle-android.yml`

Wird ausgelöst bei Push auf `claude/android-jigsaw-puzzle-app-*`-Branches  
und bei PRs, die `puzzle_android/**` berühren.

```yaml
- uses: gradle/actions/setup-gradle@v3
  with:
    gradle-version: '8.7'
- run: |
    cd puzzle_android
    gradle assembleDebug --no-daemon --stacktrace --no-configuration-cache
```

---

## Projektstruktur

```
puzzle_android/
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── puzzle_image.jpg        ← eigenes Bild hier ablegen
│       └── kotlin/com/puzzle/android/
│           ├── MainActivity.kt
│           ├── data/
│           │   ├── image/              ← ImageGenerator, TestImageGenerator
│           │   └── model/              ← PuzzleCategory, PuzzleStyle
│           ├── game/
│           │   ├── JigsawShapeGenerator.kt
│           │   ├── JigsawState.kt      ← JigsawPiece, JigsawState
│           │   └── PieceDefinition.kt
│           ├── ui/
│           │   ├── screens/
│           │   │   ├── SetupScreen.kt
│           │   │   └── PuzzleScreen.kt ← Board + Tray
│           │   └── theme/
│           └── viewmodel/
│               └── PuzzleViewModel.kt
```

---

## Architektur

```
MainActivity
  └── NavHost
        ├── SetupScreen  →  PuzzleViewModel
        └── PuzzleScreen →  PuzzleViewModel
                               │  StateFlow<JigsawState>
                               │  StateFlow<ImageBitmap?>
                               └── ImageGenerator (Assets → Pollinations.ai → Fallback)
```

Muster: **MVVM**, kein DI-Framework.

---

## Spielfeldkoordinaten

- Alle Positionen sind **fraktional** (0..1), Mittelpunkt als Anteil der Spielfeldgröße.
- `BOARD_FRACTION = 0.75f` — das Spielfeld nimmt 75% der Gesamtbreite ein.
- Die Ablage (Tray) belegt die rechten 25%.
- Snap-Schwelle: 40% der Zellgröße.

---

## Abhängigkeiten

| Bibliothek | Version | Zweck |
|---|---|---|
| Kotlin | 1.9.23 | Sprache |
| Jetpack Compose BOM | 2024.04.01 | UI-Framework |
| Material 3 | (aus BOM) | Design-System |
| OkHttp | 4.12.0 | HTTP-Client (Bilddownload) |
| Coroutines | 1.8.0 | Async / Flow |
| Lifecycle ViewModel | 2.7.0 | MVVM |
