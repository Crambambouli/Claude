# Regelwerk für die Zusammenarbeit

## Sprache
- Alle Antworten **immer auf Deutsch**, unabhängig von der Eingabesprache.

## Grundprinzip: Claude führt alles selbst aus
- Der User testet, baut und führt **nichts lokal aus** — das ist Claudes Aufgabe.
- Claude baut, testet, pusht und überwacht CI selbst.
- Wenn ein Build fehlschlägt: Fehler analysieren, fixen, erneut pushen — ohne Rückfrage, solange die Ursache klar ist.

## Git & Branches
- Entwicklung immer auf dem angegebenen Feature-Branch (nie auf `main` committen).
- Nach jedem Push: prüfen ob ein PR existiert; falls nicht, als **Draft-PR** anlegen.
- Commit-Messages auf Deutsch, prägnant, mit `https://claude.ai/code/session_...` am Ende.

## Android-Build (puzzle_android)
- Kein Android SDK lokal verfügbar → Builds laufen ausschließlich über **GitHub Actions CI**.
- CI-Workflow: `gradle/actions/setup-gradle@v3` mit `gradle-version: '8.7'`, dann `gradle assembleDebug --no-daemon --stacktrace --no-configuration-cache`.
- `--no-configuration-cache` immer setzen (defekter Cache aus fehlgeschlagenen Runs verursacht 8-Sekunden-Sofortfehler).
- Nach jedem Push auf CI-Ergebnis warten und reagieren.

## Kotlin / Jetpack Compose — bekannte Fallstricke
- **Keine non-local returns** in Compose-Lambdas (`return@key`, `return@Scaffold` → D8-Dexer-Fehler). Immer `if/else` verwenden.
- Nach jeder Änderung an `.kt`-Dateien: Klammern auf Balance prüfen (`{` count == `}` count).
- `exportSchema = false` in Room-Datenbanken (sonst `kspDebugKotlin`-Fehler).

## Projekt-Kontext
- **Gerät**: Samsung SM-T500 (Android-Tablet, API 31+)
- **Repo**: `crambambouli/claude`
- **Aktives Projekt**: `puzzle_android/` — Jigsaw-Puzzle-App (Kotlin + Jetpack Compose)
- **Architektur**: MVVM, kein DI-Framework, `PuzzleViewModel` + `StateFlow`
- **Board/Ablage**: 75% / 25% Breite (`BOARD_FRACTION = 0.75f`)
- **Jigsaw-Formen**: kubische Bezier-Kurven, `TAB_FRACTION = 0.18f`, `TAB_PEAK_FRACTION = 0.207f`
- **Snap-Schwelle**: 40% der Zellgröße
- **Koordinaten**: fraktional (0..1), Mittelpunkt als Anteil der Spielfeldgröße
- **Bildgenerierung**: Pollinations.ai, Fallback auf `TestImageGenerator`

## Was Claude nicht macht
- Keine halbfertigen Implementierungen — entweder vollständig oder gar nicht.
- Keine Kommentare die erklären WAS der Code tut (nur das WARUM, wenn nicht offensichtlich).
- Keine Emojis, es sei denn explizit gewünscht.
- Keine Rückfragen bei eindeutigen Aufgaben — einfach ausführen.
