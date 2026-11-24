# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**jPill** — an Android medication and general-purpose timer app (formerly JaggerNauTimer).
Package: `com.jagger.timer`  
App name: jPill (launcher label: jPill)

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

If the system JDK is a JRE without `javac`, point Gradle at a full JDK 17, e.g.
`JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2 ./gradlew assembleDebug`.

## Versioning & Releases

- Version lives in one place: `val appVersion` at the top of `app/build.gradle.kts` (SemVer). `versionName` is that string; `versionCode` is derived (`major*10000 + minor*100 + patch`).
- `CHANGELOG.md` follows Keep a Changelog. Add entries under `[Unreleased]` as you work.
- To release: move `[Unreleased]` entries under a new `[X.Y.Z] - date` heading, bump `appVersion`, update the compare links, merge via PR, then tag the merge commit `vX.Y.Z` and push the tag.
- Bump MINOR for features, PATCH for fixes. Any Room schema change needs a `Migration` (never destructive) and a changelog note.

## Architecture

MVVM with Android Architecture Components:

- **UI layer**: Jetpack Compose (`home/ui/`, `add_pill/ui/`)
- **ViewModel**: `androidx.lifecycle.ViewModel` + `@HiltViewModel`, one per screen
- **Repository**: single source of truth (`core/data/repository/`)
- **Data sources**: Room (local DB via `AppDatabase`), no network layer yet
- **DI**: Hilt — `@HiltAndroidApp` on `TimerApplication`, `@AndroidEntryPoint` on activities/receivers/services

Package by feature:

```
app/src/main/java/com/jagger/timer/
  home/
    ui/           HomeScreen.kt          — timeline view, FAB, empty state
    viewmodel/    HomeViewModel.kt
  add_pill/
    ui/           PillFormScreen.kt      — add/edit form: common-medicine search, as-needed toggle + dose limits, schedule, TimePicker
    viewmodel/    PillFormViewModel.kt
  core/
    data/
      catalog/    OtcCatalog, RxCatalog  — curated OTC (label limits) and prescription (names, strengths, typical frequency, notes; never doses) lists
                  DrugDataset            — bundled openFDA name list (assets/drugs.csv) parser + lazy loader
                  CatalogSearch          — one search: curated Rx, then OTC, then dataset (deduped by ingredient)
      local/
        entity/   PillEntity, ScheduleEntity, DoseLogEntity, DoseStatus, ScheduleType, RecurrencePattern
        dao/      PillDao
        AppDatabase
      model/      TimelineEntry          — pill + schedule + dueTime
      repository/ PillRepository
    di/           DatabaseModule
    receiver/     AlarmReceiver (BOOT_COMPLETED + pill alarm action)
    service/      PillReminderService (foreground, specialUse type)
    util/         AlarmScheduler, ScheduleCalculator, ScheduleManager, CooldownCalculator, SupplyCalculator,
                  RxFormLogic (+RefillPlanner), RefillState, DurationFormat
  MainActivity.kt                        — NavHost: "home" → "pill_form?pillId={pillId}"
  TimerApplication.kt
```

## Data Model

### PillEntity (`pills` table)
`id`, `name`, `dosage`, `unit`, `color` (ARGB Int), `icon` (nullable resource name), plus as-needed fields:
`asNeeded`, `minIntervalMinutes` (wait between doses), `maxPer24h` (rolling 24h cap), `maxIsDoses` (cap counts doses, else total amount in `unit`), `catalogId` (OtcCatalog entry it came from).
As-needed pills have **no** `ScheduleEntity` and no schedule alarms. `PillEntity.doseAmount()` = strength (`dosage`) x `tabletsPerDose`.
Prescription fields (DB v3): `isPrescription`, `tabletsPerDose` (`dosage` is the strength of ONE tablet), `supplyCount` (null = not tracked), `refillThresholdDays` (default 7), `instructions` (food/timing), `prescriber`, `pharmacy`, `rxNumber`.

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
`pillId`, `scheduleId` (nullable; null for as-needed doses), `timestamp`, `status` (`TAKEN` | `SKIPPED` | `MISSED`), `note`, `amount` (dose in the pill's unit when logged; null on pre-v2 logs, which fall back to the pill's current dose)

### TimelineEntry (`core/data/model/`)
In-memory projection: `pill: PillEntity`, `schedule: ScheduleEntity`, `dueTime: LocalDateTime`. Used to drive the home screen timeline.

## Scheduling Engine

- `ScheduleCalculator` — pure object, computes next `LocalDateTime` from a `ScheduleEntity` + optional last-dose timestamp
- `AlarmScheduler` — wraps `AlarmManager.setAlarmClock()`, keyed by `schedule.id` as request code
- `ScheduleManager` — orchestrates repository + scheduler:
  - `refreshAlarms()` / `refreshAlarmsForPill()` — (re)schedule AlarmManager alarms; call after any pill/schedule/dose change and on boot
  - `getUpcomingDoses(hours: Long = 24)` — returns sorted `List<TimelineEntry>` for the next N hours; used by `HomeViewModel.timeline`
- `AlarmReceiver` — handles `BOOT_COMPLETED` (refreshes all alarms) and `ACTION_PILL_REMINDER` (starts `PillReminderService`)

## Prescriptions

- Picking a prescription in the form search applies names, strength chips, notes and a *typical schedule* from `RxFrequency.scheduleDefaults()` (once daily -> Morning slot, BID -> every 12 h, weekly -> Monday 08:00, "as needed" -> cooldown with blank limits). It never fills a dose, daily max or minimum interval: those are the prescriber's.
- `RxCatalog` data is typical-adult and **unverified**: review against DailyMed before relying on it. A test guarantees `RxMed` has no dose/limit fields.
- Supply: `PillDao.logDoseAndAdjustSupply` inserts the log and subtracts `tabletsPerDose` (floor 0) in one transaction for TAKEN doses; `PillRepository.logDose` uses it, so every TAKEN path (home button, Take now, notification action) decrements. Skipped doses don't.
- `SupplyCalculator` (pure): days left = supply / (tablets per dose x doses per day from schedules); status OK / LOW / EMPTY against `refillThresholdDays`.
- Refill reminder: `ScheduleManager.refreshRefillAlarm(pillId)` re-reads the pill (callers may hold a pre-decrement copy) and `RefillPlanner` decides Cancel / FireNow / ScheduleAt. `AlarmReceiver.ACTION_REFILL_DUE` posts on the `refill_reminders` channel once per low spell; `RefillState` (SharedPreferences) remembers how far reminders escalated (level 1 low, level 2 out of pills; each fires once per low spell) and is cleared when supply is healthy again (e.g. after entering a refill in the edit form).
- Bundled names: `tools/build_drug_dataset.py` builds `app/src/main/assets/drugs.csv` from the openFDA NDC directory (CC0, https://open.fda.gov/terms/). Names/strengths/ingredients only (826 rows, ~60 KB). Provenance, license, cleaning rules and known limits are in `docs/DATASETS.md`. Regenerate with `python3 tools/build_drug_dataset.py` (network, ~30 min); `--clean raw.csv out.csv` re-applies the cleaning offline.
- DB encryption is deferred: biometric-gated keys can't be used by background alarms/notification actions, and Keystore keys don't survive backup restore.

## Archive and Delete

- **Archive** (`ScheduleManager.archivePill`): sets `isArchived`/`archivedAt` (DB v4), cancels schedule/cooldown/refill alarms and the pill's posted notifications. Schedules, supply and dose logs are kept. `PillDao.getAllPills()` and `getAsNeededPills()` exclude archived pills, so the home screen, alarms, boot restore and supply all ignore them. `restorePill` clears the flag and re-arms alarms. UI: Edit screen button; `archive/ui/ArchivedScreen` (Restore / Delete permanently), reached from the home top bar when `archivedCount > 0`.
- **Delete** (`ScheduleManager.deletePill`): cancels alarms and notifications, clears `RefillState`, deletes the pill; FK cascade removes schedules and dose logs (history is gone, the dialog says so and suggests Archive).
- Any future screen that lists history (M4) must include archived pills' logs.

## As-Needed (OTC) Medicines

For medicines taken when needed (ibuprofen, acetaminophen, ...), not on a schedule.
- `OtcCatalog` holds adult OTC label defaults; the form's "Common Medicines" search matches generic or brand names (Advil, Tylenol, Aleve, ...) and pre-fills the pill. Values are informational and user-editable; the UI shows a "check your label" disclaimer. Re-verify against current labels before changing.
- `CooldownCalculator` (pure) turns a pill + TAKEN logs + `now` into `CooldownState` (`READY` | `WAITING` | `AT_DAILY_MAX`, next-dose time, 24h totals). `forPill()` is the entry point used by both the UI and alarms.
- Home screen "As Needed" cards show a minute-precision countdown, the 24h total vs. max, and **Take now**. Taking early asks for confirmation instead of blocking.
- `HomeViewModel.asNeeded` combines pills + logs + a 5s ticker. `AsNeededItem` carries `nowMillis` on purpose: without it StateFlow de-dupes equal states and the countdown stops redrawing.
- When a wait ends, `ScheduleManager.refreshCooldownAlarm` has `AlarmScheduler` set a one-shot `setExactAndAllowWhileIdle` alarm (`ACTION_COOLDOWN_DONE`, request code = pill id). `AlarmReceiver` re-checks the cooldown and posts a normal-priority "available again" notification on the `dose_available` channel. Refreshed after Take now, on save, and on boot; cancelled on delete.

## Notifications / Foreground Service

`PillReminderService` is a foreground service with type `specialUse`. It posts a `PRIORITY_MAX` / `CATEGORY_ALARM` notification with:
- Full-screen intent → `MainActivity`
- "Taken" action (currently routes back to `MainActivity`; wire up proper action handling)
- `ongoing = true`, `autoCancel = false` (non-dismissable by default)

Required permissions declared in manifest:
`USE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`

(`SCHEDULE_EXACT_ALARM` was removed — `USE_EXACT_ALARM` is preferred for API 33+ as it doesn't require user approval in Settings.)

`POST_NOTIFICATIONS` is also requested at runtime in `MainActivity` (Android 13+).

## Key Conventions

- Compose previews (`@Preview`) for every top-level composable
- Coroutines + Flow for async; avoid RxJava
- `StateFlow` exposed from ViewModels; use `SharingStarted.WhileSubscribed(5000)`
- Navigation: `androidx.navigation.compose` with string routes (`"home"`, `"add_pill"`)
- KSP for Room and Hilt annotation processing (not kapt)
- Java 17 / `jvmTarget = "17"`
- `material.icons.extended` is included — prefer named icons from `Icons.Default.*` and `Icons.AutoMirrored.*`

## What's Built vs What's Next

### Done
- Room DB (v5) with pills, schedules, dose logs; migrations: 1->2 as-needed fields and `dose_logs.amount`, 2->3 prescriptions, 3->4 archiving, 4->5 timers
- Full scheduling engine (fixed time, time slot, interval, weekly/monthly recurrence)
- AlarmManager-based exact alarm scheduling, boot-completed restoration
- Foreground service notification with full-screen intent; Taken / Skip / Snooze actions log from the notification
- Delete medication: button + confirm on the Edit screen -> `ScheduleManager.deletePill` (cancels alarms and posted notifications, clears `RefillState`, DB cascades schedules and dose logs)
- Add / edit pill form with Material3 `TimePicker`; schedule type picker (Fixed/Slot/Interval)
- Home screen: upcoming dose timeline (next 24h) with Taken/Skip, FAB, empty state
- As-needed OTC medicines: catalog search, dose limits, live cooldown countdown, Take now with early-dose confirm, "available again" notification
- Dose history (M3): `history/ui/HistoryScreen` lists all logs (archived pills included) grouped by day with medication/status filters; `MissedDoseDetector` + a check alarm at due + grace log `MISSED` and re-plan the next dose; Home top-bar menu has History and Archived
- General timers (M5): `TimerEntity` (DB v5; RUNNING keeps `endAtMillis`, PAUSED keeps `remainingMillis`), pure `TimerLogic`, `TimerManager` (rows + one-shot `ACTION_TIMER_DONE` alarm per timer, re-armed or finished on boot), `timers/ui/TimersScreen`, bottom nav `nav/MainNavBar` (Medicines | Timers | History). Notification channel `timers`, ids via `AlarmNotificationIds.timer`
- Prescriptions (M2): Rx catalog + name list search, tablets per dose, supply tracking and refill reminder, food/timing notes, prescriber/pharmacy/Rx number, weekly day picker
- Unit tests for `CooldownCalculator`, `SupplyCalculator`, `RxFormLogic`/`RefillPlanner`, catalogs and search, `formatCountdown`; instrumented tests for the v2 -> v3 migration and atomic supply decrement (run with `adb shell am instrument`, NOT `connectedAndroidTest`, which uninstalls the app and wipes its data)

### Not yet built
- Settings (time slot defaults, toggle for the "available again" notification)
- Proper Material3 color theme (currently bare `MaterialTheme` wrapper)
- Unit tests for `ScheduleCalculator`
- Instrumented test for the v1 -> v2 migration (no `1.json` schema snapshot; v2 -> v3 is covered)
- Verify the remaining OTC label values in `OtcCatalog` (naproxen, antihistamines, famotidine, guaifenesin, dextromethorphan) and the whole `RxCatalog` against current labels
