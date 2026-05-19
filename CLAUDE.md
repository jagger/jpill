# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**JaggerNautTimer** — an Android medication and general-purpose timer app.  
Package: `com.jagger.timer`  
App name: JaggerNauTimer (launcher label: JaggerNaut)

## Target Platform

- **Min SDK**: 35 (Android 15) — use features available from API 35 onward without version guards
- **Target SDK**: 35
- **Language**: Kotlin
- **UI toolkit**: Jetpack Compose

## Build Commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew assembleRelease        # build release APK
./gradlew installDebug           # build and install on connected device/emulator
./gradlew test                   # run unit tests
./gradlew connectedAndroidTest   # run instrumented tests on device/emulator
./gradlew lint                   # run lint checks
./gradlew testDebugUnitTest --tests "com.jagger.timer.MyTest#myMethod"  # run single test
```

## Architecture

MVVM with Android Architecture Components:

- **UI layer**: Jetpack Compose (`home/ui/`)
- **ViewModel**: `androidx.lifecycle.ViewModel` + `@HiltViewModel`, one per screen
- **Repository**: single source of truth (`core/data/repository/`)
- **Data sources**: Room (local DB via `AppDatabase`), no network layer yet
- **DI**: Hilt — `@HiltAndroidApp` on `TimerApplication`, `@AndroidEntryPoint` on activities/receivers/services

Package by feature:

```
app/src/main/java/com/jagger/timer/
  home/
    ui/           HomeScreen.kt
    viewmodel/    HomeViewModel.kt
  core/
    data/
      local/
        entity/   PillEntity, ScheduleEntity, DoseLogEntity, DoseStatus, ScheduleType, RecurrencePattern
        dao/      PillDao
        AppDatabase
      repository/ PillRepository
    di/           DatabaseModule
    receiver/     AlarmReceiver (BOOT_COMPLETED + pill alarm action)
    service/      PillReminderService (foreground, specialUse type)
    util/         AlarmScheduler, ScheduleCalculator, ScheduleManager
  MainActivity.kt
  TimerApplication.kt
```

## Data Model

### PillEntity (`pills` table)
`id`, `name`, `dosage`, `unit`, `color` (ARGB Int), `icon` (nullable resource name)

### ScheduleEntity (`schedules` table)
Foreign key → `PillEntity`. Fields:
- `type`: `FIXED_TIME` | `TIME_SLOT` | `INTERVAL`
- `pattern`: `DAILY` | `WEEKLY` | `MONTHLY` | `NTH_WEEKDAY`
- `timeMinutes`: minutes from midnight (for FIXED_TIME)
- `timeSlotName`: "Morning" / "Afternoon" / "Evening" / "Night" (for TIME_SLOT)
- `intervalMinutes`: repeat interval (for INTERVAL)
- `daysOfWeek`: comma-separated day indices e.g. `"1,3,5"` (for WEEKLY)
- `dayOfMonth`, `nthOccurrence`, `dayOfWeek` (for MONTHLY / NTH_WEEKDAY)
- `reminderAdvanceMinutes` (default 30), `isCritical` (default true)

### DoseLogEntity (`dose_logs` table)
`pillId`, `scheduleId` (nullable), `timestamp`, `status` (`TAKEN` | `SKIPPED` | `MISSED`), `note`

## Scheduling Engine

- `ScheduleCalculator` — pure object, computes next `LocalDateTime` from a `ScheduleEntity` + optional last-dose timestamp
- `AlarmScheduler` — wraps `AlarmManager.setAlarmClock()`, keyed by `schedule.id` as request code
- `ScheduleManager` — orchestrates repository + scheduler; call `refreshAlarms()` on boot and after any pill/schedule change
- `AlarmReceiver` — handles `BOOT_COMPLETED` (refreshes all alarms) and `ACTION_PILL_REMINDER` (starts `PillReminderService`)

## Notifications / Foreground Service

`PillReminderService` is a foreground service with type `specialUse`. It posts a `PRIORITY_MAX` / `CATEGORY_ALARM` notification with:
- Full-screen intent → `MainActivity`
- "Taken" action (currently routes back to `MainActivity`; wire up proper action handling)
- `ongoing = true`, `autoCancel = false` (non-dismissable by default)

Required permissions declared in manifest:
`SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`

`POST_NOTIFICATIONS` is also requested at runtime in `MainActivity` (Android 13+).

## Key Conventions

- Compose previews (`@Preview`) for every top-level composable
- Coroutines + Flow for async; avoid RxJava
- `StateFlow` exposed from ViewModels; use `SharingStarted.WhileSubscribed(5000)`
- Navigation: `androidx.navigation.compose` (not yet wired; only `HomeScreen` exists)
- KSP for Room and Hilt annotation processing (not kapt)
- Java 17 / `jvmTarget = "17"`

## What's Built vs What's Next

### Done
- Room DB with pills, schedules, dose logs
- Full scheduling engine (fixed time, time slot, interval, weekly/monthly recurrence)
- AlarmManager-based exact alarm scheduling
- Foreground service notification with full-screen intent
- Boot-completed alarm restoration
- Basic HomeScreen: list pills, add test pill (fires in 1 min), delete pill

### Not yet built
- Add/edit pill UI with real schedule configuration
- "Taken" / "Skip" action handling from notification
- Dose history / log screen
- Navigation graph (multi-screen)
- General-purpose (non-pill) timers
- Settings / time slot configuration
- Theming (currently bare `MaterialTheme`)
