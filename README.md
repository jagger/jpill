# jPill

A private medication reminder and timer app for Android. Everything stays on your phone.

jPill reminds you when to take your medicines, logs every dose, keeps an eye on how much you can safely take, and tells you when a prescription is running low. It works without an account and makes no network connections.

> **Status:** pre-1.0 and under active development. For now you install it by building from source; signed APKs are planned (see [roadmap](#roadmap)).

## Features

- **Scheduled medicines:** fixed times, time slots (morning, evening, ...) or repeating intervals; daily, weekly, monthly or "second Tuesday"-style schedules. Reminders use exact alarms and survive reboots; Taken, Skip and Snooze work straight from the notification.
- **As-needed medicines:** a live countdown to your next safe dose, a rolling 24-hour limit, and a confirmation if you take one early. Warns when two of your medicines share an active ingredient.
- **Prescriptions:** supply tracking and refill reminders, prescriber, pharmacy and Rx number, and food and timing tags ("With food", "Stay upright 30 min", "No heavy machinery").
- **History:** every taken, skipped and missed dose, filterable, with CSV export through Android's share sheet.
- **Timers:** general-purpose countdowns that keep running with the app closed.
- **Home-screen widget:** wait times for as-needed medicines with a one-tap Take now.
- **Privacy:** optional fingerprint, face or PIN app lock, an option to hide medicine names on the lock screen, and a Delete all data button.
- **Looks:** dark and light themes, accessibility checked (contrast, 48 dp touch targets, TalkBack labels).

## Privacy

- **No network.** The app does not request the `INTERNET` permission, so it cannot send anything anywhere. There are no accounts, ads, analytics or crash reporters.
- **Your data lives on the device** in a local database. The app lock only guards the screen; the database itself is not encrypted beyond Android's own storage protection (reminders have to work while the app is locked, and keys tied to your fingerprint cannot be used by background alarms).
- **Backups:** Android's automatic backup is allowed for the app. If you have device backup turned on, app data can be included in it, under the rules and encryption of your phone's backup service.
- **Share and export are manual.** Nothing leaves the phone unless you share a CSV yourself.
- **Delete all data** (Settings, Privacy and data) wipes every medicine, dose, schedule and timer and cancels their reminders.

## Medical disclaimer

jPill is a personal organiser, **not a medical device**, and it does not give medical advice. Dose limits for over-the-counter medicines and the prescription catalog are informational, typical-adult values that have **not** all been verified against current labels. Always follow your medicine's label and your prescriber or pharmacist. Never rely on this app alone for anything where a missed or doubled dose could hurt you. See the licence for the warranty disclaimer.

## Build from source

Requirements: a full JDK 17 (a JRE without `javac` will not do) and the Android SDK with platform 35. The app needs Android 15 (API 35) or newer.

```bash
./gradlew assembleDebug         # build the debug APK (app/build/outputs/apk/debug/)
./gradlew testDebugUnitTest     # unit tests
./gradlew lint                  # lint
./gradlew installDebug          # install on a connected device
```

If Gradle picks a JRE, point it at a JDK: `JAVA_HOME=/path/to/jdk17 ./gradlew assembleDebug`. Gradle needs network access to fetch dependencies.

> **Careful with instrumented tests.** `./gradlew connectedAndroidTest` uninstalls the app, which **wipes its data**. Only run it on an emulator or a spare device, never on a phone that holds real medicine data.

Architecture, data model and conventions are written up in [CLAUDE.md](CLAUDE.md). The bundled medicine-name list and its licence are documented in [docs/DATASETS.md](docs/DATASETS.md).

## Roadmap

Open work is tracked in [issues](https://github.com/jagger/jpill/issues). Next up: NFC tags you stick on a medicine lid to confirm a dose with a tap, then distribution (signed releases). Releases and their notes are in [CHANGELOG.md](CHANGELOG.md).

## Support

jPill is free and has no ads. If it is useful to you and you want to say thanks, you can [buy me a coffee on Ko-fi](https://ko-fi.com/I0X62B9ETA).

## Contributing

Bug reports, ideas and pull requests are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md). Please do not paste real medicine or health details into issues.

## License

jPill is free software, licensed under the **GNU General Public License v3.0 or later** (`GPL-3.0-or-later`). See [LICENSE](LICENSE). The app also shows this in Settings, About.

Third-party components: AndroidX, Jetpack Compose, Room, Hilt, Navigation, Biometric and Glance (all Apache License 2.0), and the openFDA drug-name list (CC0 1.0, public domain).
