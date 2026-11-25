# jPill

A private medication reminder and timer app for Android. Everything stays on your phone.

jPill reminds you when to take your medicines, logs every dose, keeps an eye on how much you can safely take, and tells you when a prescription is running low. It works without an account and makes no network connections.

> **Status:** pre-1.0 and under active development. Signed APKs for sideloading are attached to each [release](https://github.com/jagger/jpill/releases); see [Install](#install-on-your-phone).

## Features

- **Scheduled medicines:** fixed times, time slots (morning, evening, ...) or repeating intervals; daily, weekly, monthly or "second Tuesday"-style schedules. Reminders use exact alarms and survive reboots; Taken, Skip and Snooze work straight from the notification.
- **As-needed medicines:** a live countdown to your next safe dose, a rolling 24-hour limit, and a confirmation if you take one early. Warns when two of your medicines share an active ingredient.
- **Prescriptions:** supply tracking and refill reminders, prescriber, pharmacy and Rx number, and food and timing tags ("With food", "Stay upright 30 min", "No heavy machinery").
- **History:** every taken, skipped and missed dose, filterable, with CSV export through Android's share sheet.
- **Timers:** general-purpose countdowns that keep running with the app closed.
- **NFC tags (beta, off by default; Settings, Beta features):** stick a tag on a medicine lid and tap your phone to confirm a dose, or on the washer to start its timer. Tags hold only a random id and a name, never a dose.
- **Home-screen widget:** wait times for as-needed medicines with a one-tap Take now.
- **Privacy:** optional fingerprint, face or PIN app lock, an option to hide medicine names on the lock screen, and a Delete all data button.
- **Looks:** dark and light themes, accessibility checked (contrast, 48 dp touch targets, TalkBack labels).

## Privacy

- **No network.** The app does not request the `INTERNET` permission, so it cannot send anything anywhere. (Its manifest also lists `ACCESS_NETWORK_STATE`, which an AndroidX library adds; it only lets an app see whether a connection exists, not use it.) There are no accounts, ads, analytics or crash reporters.
- **Your data lives on the device** in a local database. The app lock only guards the screen; the database itself is not encrypted beyond Android's own storage protection (reminders have to work while the app is locked, and keys tied to your fingerprint cannot be used by background alarms).
- **Backups:** Android's automatic backup is allowed for the app. If you have device backup turned on, app data can be included in it, under the rules and encryption of your phone's backup service.
- **NFC tags** carry a random id plus the medicine or timer name (the medicine name is optional per tag), readable by anyone with an NFC phone. They never carry a dose or any other detail.
- **Share and export are manual.** Nothing leaves the phone unless you share a CSV yourself.
- **Delete all data** (Settings, Privacy and data) wipes every medicine, dose, schedule and timer and cancels their reminders.

## Medical disclaimer

jPill is a personal organiser, **not a medical device**, and it does not give medical advice. Dose limits for over-the-counter medicines and the prescription catalog are informational, typical-adult values that have **not** all been verified against current labels. Always follow your medicine's label and your prescriber or pharmacist. Never rely on this app alone for anything where a missed or doubled dose could hurt you. See the licence for the warranty disclaimer.

## Install on your phone

jPill is not on an app store. You sideload the APK from the latest [release](https://github.com/jagger/jpill/releases/latest) (Android 15 or newer):

1. Download `jpill-<version>.apk` on your phone and open it.
2. Allow installs from that app when Android asks, then tap **Install** (if Play Protect warns about an unknown developer, choose **Install anyway**).
3. Open jPill and allow notifications, then set its battery use to **Unrestricted** so reminders arrive on time.

**Full step-by-step guide, update and uninstall notes, and troubleshooting: [docs/INSTALL.md](docs/INSTALL.md).**

**Updating:** install the new APK over the old one. Your medicines and history are kept, because every official release is signed with the same key.

**Verify what you downloaded.** Each release has a `.sha256` file with the APK's checksum, and every APK is signed with the same certificate:

```
SHA-256 of the signing certificate:
3527252d8290f901f5fa6dc8f80abd0dc2e91b768b9f6684faa0b68a6528ebaf
```

If the fingerprint from `apksigner verify --print-certs jpill-<version>.apk` is different, do not install it.

**Switching from a build you compiled yourself:** it has a different signature, so Android won't install the release over it. Uninstall first, which deletes the app's data; export what you need first (History has a CSV share).

## Build from source

Requirements: a full JDK 17 (a JRE without `javac` will not do) and the Android SDK with platform 35. The app needs Android 15 (API 35) or newer.

```bash
./gradlew assembleDebug         # build the debug APK (app/build/outputs/apk/debug/)
./gradlew testDebugUnitTest     # unit tests
./gradlew lint                  # lint
./gradlew installDebug          # install on a connected device
```

If Gradle picks a JRE, point it at a JDK: `JAVA_HOME=/path/to/jdk17 ./gradlew assembleDebug`. Gradle needs network access to fetch dependencies.

A release build (`./gradlew assembleRelease`, shrunk with R8) is unsigned unless you provide your own `keystore.properties`; see [CLAUDE.md](CLAUDE.md). Release APKs from this project are signed with the project's key, so a build you sign yourself will not update an installed official release (and vice versa).

> **Careful with instrumented tests.** `./gradlew connectedAndroidTest` uninstalls the app, which **wipes its data**. Only run it on an emulator or a spare device, never on a phone that holds real medicine data.

Architecture, data model and conventions are written up in [CLAUDE.md](CLAUDE.md). The bundled medicine-name list and its licence are documented in [docs/DATASETS.md](docs/DATASETS.md).

## Roadmap

Open work is tracked in [issues](https://github.com/jagger/jpill/issues). Next up: distribution (signed releases). Releases and their notes are in [CHANGELOG.md](CHANGELOG.md).

## Support

jPill is free and has no ads. If it is useful to you and you want to say thanks, you can [buy me a coffee on Ko-fi](https://ko-fi.com/I0X62B9ETA).

## Contributing

Bug reports, ideas and pull requests are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md). Please do not paste real medicine or health details into issues.

## License

jPill is free software, licensed under the **GNU General Public License v3.0 or later** (`GPL-3.0-or-later`). See [LICENSE](LICENSE). The app also shows this in Settings, About.

Third-party components: AndroidX, Jetpack Compose, Room, Hilt, Navigation, Biometric and Glance (all Apache License 2.0), and the openFDA drug-name list (CC0 1.0, public domain).
