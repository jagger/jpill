# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android application targeting Android 15 (API 35). The project has not been initialized yet — when scaffolding begins, use Android Studio's standard Gradle project layout or the `android create project` CLI.

## Target Platform

- **Min SDK**: 35 (Android 15) — use features available from API 35 onward without version guards
- **Target SDK**: 35
- **Language**: Kotlin (preferred over Java for all new code)
- **UI toolkit**: Jetpack Compose (preferred over XML layouts)

## Build Commands

Once the project is scaffolded, standard Gradle wrapper commands apply:

```bash
./gradlew assembleDebug          # build debug APK
./gradlew assembleRelease        # build release APK
./gradlew installDebug           # build and install on connected device/emulator
./gradlew test                   # run unit tests
./gradlew connectedAndroidTest   # run instrumented tests on device/emulator
./gradlew lint                   # run lint checks
./gradlew testDebugUnitTest --tests "com.example.MyTest#myMethod"  # run single test
```

## Architecture

Follow MVVM with the standard Android Architecture Components stack:

- **UI layer**: Jetpack Compose screens and composables
- **ViewModel**: `androidx.lifecycle.ViewModel`, one per screen/feature
- **Repository**: single source of truth, mediates between data sources
- **Data sources**: Room (local DB), Retrofit/Ktor (network), DataStore (preferences)
- **DI**: Hilt (preferred)

Package by feature, not by layer:

```
app/src/main/java/com/<package>/
  feature_name/
    ui/          # Composables, Screen.kt
    viewmodel/   # FeatureViewModel.kt
    repository/  # FeatureRepository.kt
    model/       # Data classes
  core/          # Shared utilities, network client, DB
```

## Key Conventions

- Compose previews (`@Preview`) for every top-level composable
- Coroutines + Flow for async; avoid RxJava
- `StateFlow`/`UiState` sealed class pattern in ViewModels
- Navigation: `androidx.navigation.compose`
