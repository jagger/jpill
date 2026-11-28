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

## Release signing

- Releases are signed with one long-lived key. The keystore and its passwords live **outside the repo**, in `~/.jpill-signing/` (`jpill-release.jks`, `keystore.properties`, mode 600). **Back that folder up somewhere safe** (e.g. a password manager). If the key is lost, nobody can install an update over an existing install: they would have to uninstall (losing data) and reinstall. Never commit the keystore or passwords; `.gitignore` covers `keystore.properties`, `*.jks`, `*.keystore`.
- To build: copy `~/.jpill-signing/keystore.properties` to the project root as `keystore.properties` (gitignored), then run `tools/build_release.sh`. It builds with R8 (`isMinifyEnabled`, `isShrinkResources`), verifies the signature, writes `build/release/jpill-<version>.apk` and a `.sha256`, and prints the certificate fingerprint, which must equal the one in `README.md`.
- Publishing a release: after the normal release steps (version bump PR, tag), attach both files: `gh release upload vX.Y.Z build/release/jpill-X.Y.Z.apk build/release/jpill-X.Y.Z.apk.sha256`.
- A debug build (debug key) and a release build (project key) cannot be installed over each other; switching means uninstalling and losing app data. To test an upgrade without touching a real install, build both versions with a throwaway `applicationId` suffix (and `isDebuggable = true` if you need `run-as` to seed data), install the old one, then `adb install -r` the new one. Debuggable builds skip shrinking, so check R8 separately with a non-debuggable build.
- Verified for 0.8.0: a release-signed update installs over the previous release and keeps data (including the v6 to v7 migration), and the shrunk build runs (Room, Hilt, assets, NFC screen).

## Open Source

- Licensed **GPL-3.0-or-later**. `LICENSE` is the full GPLv3 text; `app/src/main/assets/LICENSE.txt` is a byte-for-byte copy shown in Settings > About > "Read the full license". `LicenseTextTest` fails if they drift apart, so update both together (you should never need to).
- Every Kotlin file starts with `// SPDX-License-Identifier: GPL-3.0-or-later`. Add it to new files.
- The app declares **no `INTERNET` permission** and the README, About screen and CONTRIBUTING all promise "no network". Do not add one without a design discussion; About only opens links in the user's browser.
- About links (project page, issues, Ko-fi) live in `ProjectLinks` in `settings/ui/AboutScreen.kt`. `.github/FUNDING.yml` carries the same Ko-fi id.
- Public-facing docs: `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `.github/` templates. Keep real health data out of issues, tests, fixtures and screenshots.

## Reminder reliability

- `reliability/ReminderHealth` (pure, tested) models the four checks and the banner text; `ReminderHealthChecker` reads the phone (`areNotificationsEnabled` + the two reminder channels, `isIgnoringBatteryOptimizations`, `canUseFullScreenIntent`, `canScheduleExactAlarms`) and builds the fix intents (the system battery prompt needs `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). `ReliabilityViewModel` re-checks on every resume (`RefreshOnResume`) and stores the one-week "Not now" snooze in `AppSettings`.
- UI: `ReliabilityBanner` is the first item in Home's list; `ReliabilityScreen` is Settings > Reminder reliability. Apps cannot flip these settings silently, only ask; Samsung's Sleeping/Deep sleeping lists have no API, so that tip is static text shown on Samsung devices.

## NFC Tags

- **Beta, off by default:** `AppSettings.nfcBetaEnabled` (Settings > Developer tools > Beta features). It hides the medicine-form NFC section and the timer NFC button, and medicine scans in `NfcScanActivity` just say tags are switched off. Timer tags are never gated: they are scan-and-go (a new or idle timer starts with no confirm tap, created from the tag's own label/secs; running/paused ones still ask), and the card asks for the notification permission if this phone hasn't granted it yet. Remove the gate once tags are proven on real stickers.

- `nfc/NfcPayload` (pure) is the tag format: `jpill://tag/<id>[?name=..]` for a medicine, `jpill://timer/<id>?label=..&secs=..` for a timer. `NfcTagUri` makes/validates the 128-bit random id. Never put a dose or other medicine detail on a tag.
- `nfc/TagScanResolver` (pure) turns a scanned medicine into a `ScanDecision` (due / early / already taken / as-needed ready or early / archived / unknown) using `DoseCoverage` (2 h early window), `CooldownCalculator` and the shared-ingredient warning; `TimerScanResolver` does the same for timers. A dose is only logged after the user confirms; new/idle timers start immediately (`startsImmediately`), running/paused ones ask.
- `nfc/NfcScanActivity` is exported with an `NDEF_DISCOVERED` filter for `jpill://tag` and `jpill://timer`; it shows a confirm card only (so it works over the lock screen and with the app lock on) and hides the medicine name on a locked screen when that setting is on. `NfcScanViewModel` logs through the normal `PillRepository.logDose` + `ScheduleManager.refreshAlarmsForPill` path.
- `nfc/NfcTagIo` reads/writes NDEF in reader mode (foreground activity only); `nfc/NfcTagPanel` is the write/rewrite/link/remove UI used by the medicine form and the timer dialog. `NfcTagDirectory` keeps one sticker from being linked to two things.
- DB v7 adds a nullable unique `nfcTagId` to `pills` and `timers` (`MIGRATION_6_7`). Test simulated scans without hardware: `adb shell "am start -a android.nfc.action.NDEF_DISCOVERED -d 'jpill://timer/<22-char-id>?label=Washer&secs=2700'"` (quote the URL for the device shell).

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
    receiver/     AlarmReceiver (boot/update/time-change re-arm, reminder, missed check, actions), ReminderNotifier
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
- `AlarmReceiver` — handles `BOOT_COMPLETED`/update/time changes (refreshes all alarms) and `ACTION_PILL_REMINDER` (posts the reminder via `ReminderNotifier`)

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

## Notifications and the reminder log

- `AlarmReceiver.ACTION_PILL_REMINDER` calls `ReminderNotifier.post` directly (there is **no foreground service**: it used to be one, but simultaneous reminders replaced each other, Taken on one stopped it for all, and a refused start lost the reminder silently). One notification per medicine (id = `safeNotificationId(pillId)`), `ongoing`, with Taken / Skip / Snooze (10m) actions that log through `AlarmReceiver`. It stays until the user acts; a missed-dose check does NOT clear it; marking the dose Taken/Skipped inside the app clears it (`ScheduleManager.dismissDueReminder`).
- Two channels, picked by schedule type: **`pill_reminders_alarm`** for timed doses (FIXED_TIME / INTERVAL / unknown): alarm sound on `USAGE_ALARM` so silent mode and Do Not Disturb (alarms allowed) don't mute it, `CATEGORY_ALARM`, full-screen intent. **`pill_reminders_general`** for TIME_SLOT doses ("Morning", no exact time): normal notification sound, `CATEGORY_REMINDER`, no full-screen. A channel's sound type is immutable once created, so changing it needs a new id (the old `pill_reminders` is deleted in `ReminderNotifier.ensureChannel`, also run from `TimerApplication`).
- Slot doses get a 6 h missed grace (`MissedDoseDetector.SLOT_GRACE_MINUTES`) because a late dose is fine; others get 60 min.
- Snooze is its own alarm (`AlarmScheduler.scheduleSnooze`, request code `1_000_000 + scheduleId`) so it can't replace the schedule's next regular alarm.
- `reminderlog/ReminderLog` is a persistent trace (app's external files dir `reminder_log.txt`, capped, mirrored to logcat tag `jPillAlarm`): every ARM/PLAN, RECEIVED, NOTIFY (with notification/channel/DND state), DOSE ACTION, SNOOZE, MISSED (or `MISSED, NO ALARM` when the reminder alarm never fired), REARM. UI: Settings > Developer tools > Reminder log (armed list, Re-arm all, Share, Clear). Read it on the phone with `adb pull /sdcard/Android/data/com.jagger.timer/files/reminder_log.txt`. Android re-arm: `AlarmReceiver` handles BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET and TIMEZONE_CHANGED with `refreshAlarms()`, and `MainActivity` runs `ScheduleManager.rearmMissing` on open (only catches alarms that are missing, not wrong ones). MY_PACKAGE_REPLACED was observed NOT being delivered after some `adb install -r` runs; cause unknown.
- What's new: `whatsnew/WhatsNew` parses the bundled `CHANGELOG.md` (copied into assets by the `copyChangelog` Gradle task); after an update `MainActivity` shows every version newer than `AppSettings.lastSeenVersionCode`, cleared with "Got it". Settings > About > What's new reopens the last five.

Permissions declared in the manifest: `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `NFC`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (no foreground-service permissions, no INTERNET). `POST_NOTIFICATIONS` is requested at runtime in `MainActivity`, and by the NFC scan card after a timer scan on a phone that never opened jPill.

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
- Room DB (v7) with pills, schedules, dose logs; migrations: 1->2 as-needed fields and `dose_logs.amount`, 2->3 prescriptions, 3->4 archiving, 4->5 timers, 5->6 ingredients
- Full scheduling engine (fixed time, time slot, interval, weekly/monthly recurrence)
- AlarmManager-based exact alarm scheduling, boot-completed restoration
- Reminder notifications (alarm-style for timed doses, quiet persistent for slot doses); Taken / Skip / Snooze actions log from the notification
- Delete medication: button + confirm on the Edit screen -> `ScheduleManager.deletePill` (cancels alarms and posted notifications, clears `RefillState`, DB cascades schedules and dose logs)
- Add / edit pill form with Material3 `TimePicker`; schedule type picker (Fixed/Slot/Interval)
- Home screen: upcoming dose timeline (next 24h) with Taken/Skip, FAB, empty state
- As-needed OTC medicines: catalog search, dose limits, live cooldown countdown, Take now with early-dose confirm, "available again" notification
- Dose history (M3): `history/ui/HistoryScreen` lists all logs (archived pills included) grouped by day with medication/status filters; `MissedDoseDetector` + a check alarm at due + grace log `MISSED` and re-plan the next dose; Home top-bar menu has History and Archived
- General timers (M5): `TimerEntity` (DB v5; RUNNING keeps `endAtMillis`, PAUSED keeps `remainingMillis`), pure `TimerLogic`, `TimerManager` (rows + one-shot `ACTION_TIMER_DONE` alarm per timer, re-armed or finished on boot), `timers/ui/TimersScreen`, bottom nav `nav/MainNavBar` (Medicines | Timers | History). Notification channel `timers`, ids via `AlarmNotificationIds.timer`
- Duplicate ingredients (M6): `PillEntity.ingredients` (DB v6, normalized names joined by `|`, set from the catalog pick via `IngredientLogic.normalize`); pure `IngredientLogic.warnings` finds pills sharing an ingredient and sums their 24h TAKEN doses (same unit, or dose counts) against the lowest cap; `HomeViewModel.ingredientWarnings` -> banner on cards and the early-dose dialog. Warns only, never blocks. Pills created before v6 have no ingredients until re-picked from the catalog Rx catalog + name list search, tablets per dose, supply tracking and refill reminder, food/timing notes, prescriber/pharmacy/Rx number, weekly day picker
- Unit tests for `CooldownCalculator`, `SupplyCalculator`, `RxFormLogic`/`RefillPlanner`, catalogs and search, `formatCountdown`; instrumented tests for the v2 -> v3 migration and atomic supply decrement (run with `adb shell am instrument`, NOT `connectedAndroidTest`, which uninstalls the app and wipes its data)

### Not yet built
- Settings (time slot defaults, toggle for the "available again" notification)
- Proper Material3 color theme (currently bare `MaterialTheme` wrapper)
- Unit tests for `ScheduleCalculator`
- Instrumented test for the v1 -> v2 migration (no `1.json` schema snapshot; v2 -> v3 is covered)
- Verify the remaining OTC label values in `OtcCatalog` (naproxen, antihistamines, famotidine, guaifenesin, dextromethorphan) and the whole `RxCatalog` against current labels
