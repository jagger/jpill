# Contributing to jPill

Thanks for helping. jPill is a small, privacy-first Android app, so a few ground rules keep it that way.

## Ground rules

- **No network, no tracking.** Do not add the `INTERNET` permission, analytics, ads or anything that phones home. If a feature truly needs the network, open an issue to discuss it first.
- **Never include real health data** in issues, pull requests, screenshots or test fixtures. Use made-up medicines and values.
- **Medical content stays informational.** Catalog data must never supply a dose, frequency or limit for a prescription, and changes to over-the-counter label values need a source (the current label).
- **Licensing.** By contributing you agree your work is licensed under the project's GPL-3.0-or-later. New source files start with `// SPDX-License-Identifier: GPL-3.0-or-later`.

## Setup and checks

See the [README](README.md#build-from-source). Before opening a pull request:

```bash
./gradlew testDebugUnitTest lint assembleDebug
```

Do not run `./gradlew connectedAndroidTest` on a device with real data; it uninstalls the app and wipes its data. Use an emulator.

## Conventions

- Kotlin, Jetpack Compose, MVVM with Hilt, Room, coroutines and Flow. Architecture notes are in [CLAUDE.md](CLAUDE.md).
- Put logic in small pure functions or objects and unit-test them (see `core/util` and its tests).
- **Any Room schema change needs a non-destructive `Migration`**, an exported schema in `app/schemas`, and a changelog note. Never wipe user data.
- Keep screens accessible: 48 dp touch targets, text contrast of at least 4.5:1, and names and roles for custom controls.

## Pull requests

1. Branch from `main` with a short name (`fix-early-dose`, not the old `m2-*`/`m3-*` milestone branches).
2. Keep a PR to one change, add or update tests, and add a line under `[Unreleased]` in [CHANGELOG.md](CHANGELOG.md) ([Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format).
3. Maintainers merge with a merge commit. Releases bump `appVersion` in `app/build.gradle.kts` (SemVer), get a `vX.Y.Z` tag and a GitHub release.
