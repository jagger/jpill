# Changelog

All notable changes to jPill are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- **Prescriptions.** Search ~120 curated prescriptions by generic or brand name (Lipitor, Zoloft, ...). A pick fills the name, strength chips, food/timing notes and a typical schedule. It never fills a dose or limits: those come from your prescriber.
- Bundled offline name list (from the openFDA NDC directory, public domain) fills gaps beyond the curated list.
- **Supply and refills:** tablets on hand drops with every taken dose (home button, Take now, or the notification), cards show "N left, about D days" and turn red when low, and a one-time refill reminder fires at a threshold you set (default 7 days).
- Tablets per dose (strength x tablets, e.g. 2 x 500 mg) for every pill; dose log and cooldown totals use the full amount.
- Food and timing notes (preset chips plus free text); prescriber, pharmacy and Rx number fields.
- Weekly schedules: "Certain days" with a weekday picker.
- Room schema export, with instrumented tests for the v2 -> v3 migration and the atomic supply decrement.

### Changed
- Database upgraded to version 3 (non-destructive migration).
- The "Common Medicines" search now covers prescriptions and the name list as well as OTC.

## [0.1.0] - 2026-11-23

### Added
- **As-needed (OTC) medicines** with dose cooldown timers.
  - Built-in catalog of common over-the-counter medicines searchable by generic or brand name: ibuprofen (Advil, Motrin), acetaminophen (Tylenol), naproxen sodium (Aleve), aspirin (Bayer, Ecotrin, Bufferin), diphenhydramine (Benadryl), loratadine (Claritin), cetirizine (Zyrtec), famotidine (Pepcid), guaifenesin (Mucinex), dextromethorphan (Robitussin DM, Delsym).
  - "As Needed" section on the home screen: live countdown to the next dose (minute precision), running 24-hour total against the daily maximum, and a **Take now** button.
  - Taking a dose early (or at the daily max) asks for confirmation instead of blocking.
  - "Available again" notification when the wait between doses ends.
  - Add/Edit form: "Common Medicines" search, "Take as needed" switch, and dose limits (minimum wait in hours, decimals allowed; maximum per 24 hours as total mg or number of doses).
- Dose log now records the amount taken, so 24-hour totals stay correct if a pill's dose is edited later.
- Unit tests for the cooldown logic, catalog search and countdown formatting.
- This changelog and single-source app versioning.
- From Milestones 0 and 1 (previously untagged): Taken / Skip / Snooze actions directly from the reminder notification, and editing existing pills and schedules.

### Changed
- App renamed from JaggerNauTimer to **jPill** (the package name and database file are unchanged, so existing installs and data carry over).
- Database upgraded to version 2 (migration keeps existing pills, schedules and history).
- App version is now defined once in `app/build.gradle.kts`; the version code is derived from it.

### Notes
- Catalog values are adult OTC label defaults and informational only. Always follow the label on your package. Ibuprofen, acetaminophen, aspirin and diphenhydramine limits were checked against published references; the rest should be verified.

## [0.0.3] - 2026-05-20

### Added
- Polished Add Pill screen and upcoming-dose timeline on the home screen.
- Extended Material icons.

## [0.0.1] - 2026-05-18

### Added
- Initial Android project: Room data layer for pills, schedules and dose logs.
- Scheduling engine with AlarmManager exact alarms and a foreground reminder service.
- Basic home screen for testing, and runtime notification permission for Android 15.

[Unreleased]: https://github.com/jagger/jpill/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/jagger/jpill/compare/v0.0.3...v0.1.0
[0.0.3]: https://github.com/jagger/jpill/compare/v0.0.1...v0.0.3
[0.0.1]: https://github.com/jagger/jpill/releases/tag/v0.0.1
