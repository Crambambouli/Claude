# Puzzle Android

Android app scaffold (Kotlin + Jetpack Compose) — App-ID `puzzle_android`, version **0.1.0**.

Targets **Android 12+ (API 31+)** and is built with **Gradle 8.7**.

---

## Features

| Feature | Implementation |
|---|---|
| Start screen `/` | Jetpack Compose + Material Design 3 |
| Health check | `GET /api/health` via Retrofit |
| Local storage | Room Database – `Example` entity |
| State management | MVVM + `StateFlow` |
| Dark mode | Dynamic colour (API 31+) + static M3 palette fallback |
| Logging | `HttpLoggingInterceptor` (DEBUG only) |
| Tests | JUnit 4 · Mockito-Kotlin · Compose UI tests |

---

## Prerequisites

| Tool | Minimum version |
|---|---|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 |
| Android SDK | API 34 (compile) / API 31 (min) |
| Gradle | 8.7 (via wrapper — no local install needed) |

---

## Setup

### 1. Clone the repository

```bash
git clone <repo-url>
cd puzzle_android
```

### 2. Configure the Android SDK path

Copy the template and set your local SDK location:

```bash
cp local.properties.template local.properties
```

Edit `local.properties`:

```properties
# macOS example
sdk.dir=/Users/<yourname>/Library/Android/sdk

# Linux example
sdk.dir=/home/<yourname>/Android/Sdk

# Windows example
sdk.dir=C\:\\Users\\<yourname>\\AppData\\Local\\Android\\Sdk
```

> Android Studio sets this automatically when you open the project.

### 3. Configure the API base URL (optional)

The default URL is `https://api.example.com/`.  
To override it, add a build config field in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://your-real-api.com/\"")
```

Or set it per variant / via CI environment.

---

## Build

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires a keystore — see Signing below)
./gradlew assembleRelease

# Full build (compile + lint + test)
./gradlew build
```

The debug APK is output to:

```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Run

### On an emulator / device via ADB

```bash
./gradlew installDebug
```

### Via Android Studio

1. Open the `puzzle_android/` folder in Android Studio.
2. Select a device / emulator in the toolbar.
3. Press **Run** (▶).

---

## Signing (Debug Keystore)

Gradle automatically uses the default debug keystore (`~/.android/debug.keystore`) for debug builds.  
No additional configuration is needed for development.

For a release build, add signing config to `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile   = file(System.getenv("KEYSTORE_PATH") ?: "release.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias      = System.getenv("KEY_ALIAS")
        keyPassword   = System.getenv("KEY_PASSWORD")
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

---

## Tests

### Unit tests (JVM)

```bash
./gradlew test
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`

### Instrumented / UI tests (requires emulator or device)

```bash
./gradlew connectedAndroidTest
```

Reports: `app/build/reports/androidTests/connected/index.html`

### Lint

```bash
./gradlew lint
```

Report: `app/build/reports/lint-results-debug.html`

---

## Project Structure

```
puzzle_android/
├── gradle/
│   ├── libs.versions.toml          # Version catalog
│   └── wrapper/
│       └── gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts            # Module dependencies
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/puzzle/android/
│       │   │   ├── MainActivity.kt
│       │   │   ├── PuzzleApplication.kt   # Manual DI entry point
│       │   │   ├── data/
│       │   │   │   ├── api/               # Retrofit + OkHttp
│       │   │   │   ├── db/                # Room (entity, DAO, database)
│       │   │   │   ├── model/             # Gson DTOs
│       │   │   │   └── repository/        # Single source of truth
│       │   │   ├── ui/
│       │   │   │   ├── screens/           # Compose screens
│       │   │   │   └── theme/             # M3 colour, type, theme
│       │   │   └── viewmodel/             # StateFlow + ViewModel
│       │   └── res/
│       ├── test/                          # JVM unit tests
│       └── androidTest/                   # Compose UI tests
├── build.gradle.kts                # Root build
├── settings.gradle.kts
└── gradle.properties
```

---

## Architecture

```
MainActivity
  └── StartScreen (Compose, stateless content composable)
        └── MainViewModel
              │   StateFlow<MainUiState>   — health check status
              │   StateFlow<List<ExampleEntity>> — Room live list
              └── ExampleRepository
                    ├── ApiClient → ApiService → GET /api/health
                    └── AppDatabase → ExampleDao → examples table
```

Pattern: **MVVM + Repository**, no external DI framework (manual wiring in `PuzzleApplication`).

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health` | Returns `{ "status": "ok", "version": "…" }` |

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| Kotlin | 1.9.23 | Language |
| Jetpack Compose BOM | 2024.04.01 | UI framework |
| Material 3 | (from BOM) | Design system |
| Retrofit 2 | 2.11.0 | HTTP client |
| OkHttp | 4.12.0 | HTTP engine + logging |
| Gson | 2.10.1 | JSON serialisation |
| Room | 2.6.1 | Local SQLite ORM |
| Coroutines | 1.8.0 | Async / Flow |
| Lifecycle ViewModel | 2.7.0 | MVVM |
| Mockito-Kotlin | 5.2.1 | Unit test mocking |
