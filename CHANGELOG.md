# Changelog

All notable changes to jPill are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- **About screen** (Settings > About): version, a link to the project on GitHub, a link to report problems or suggest features, a Ko-fi link to support jPill, the GPL notice with the full GNU General Public License v3 text built in, the medical disclaimer and credits.
- **Open-source release preparation:** jPill is licensed under the GNU GPL v3 or later (`LICENSE`, and an SPDX identifier on every source file). New `README.md` (features, privacy, medical disclaimer, build steps), `CONTRIBUTING.md`, `SECURITY.md`, issue and pull request templates, and a GitHub funding link.

## [0.6.1] - 2026-11-24

### Changed
- **New app icon:** a flat capsule, solid on the left and empty on the right like a progress bar, graphite on a soft ice-blue background, replacing the clock. Includes a themed (monochrome) layer for Android 13+ themed icons.

## [0.6.0] - 2026-11-24

### Added
- **Share History as CSV.** A share icon in the History top bar hands the list to Android's share sheet as `jpill-history-<date>.csv` (Date, Time, Medication, Status, Amount, Unit, Note). It exports everything the current medication and status filters match, not just the rows on screen. Text a spreadsheet would run as a formula is neutralised.
- **Food and timing tags** for every medicine (not just prescriptions): With food, Empty stomach, Morning, Evening, At bedtime, Stay upright 30 min, Full glass of water, No heavy machinery, Avoid alcohol, Avoid grapefruit, Avoid sun exposure, plus free text. Stored in the existing notes field, so nothing in the database changes.

### Changed
- **Compact medicine tiles on Home**, about half as tall. Each is one row: icon, name, a status line (dose time, or the wait countdown for as-needed medicines) and its buttons (Skip and Taken, or Take now). A warning icon appears when a medicine has a shared-ingredient or low-supply alert.
- **Tap a tile to expand it** (one open at a time): tags, doctor, Rx number, pharmacy, supply, 24 h usage for as-needed medicines, and the Edit button.

## [0.5.1] - 2026-11-24

### Added
- **Settings menu.** The Home menu now opens Settings, with Appearance and Privacy and data under it.
- **Theme choice** (Settings > Appearance): follow the phone, light or dark. The widget always follows the phone.
- **Delete all data** (Settings > Privacy and data): removes every medicine (archived too), all dose history, schedules and timers, and cancels their reminders and notifications. Needs DELETE typed to confirm. Lock and theme settings are kept.

### Accessibility
- Colour and icon pickers announce names ("Indigo color", "heart icon") and their selected state to TalkBack; switch rows are single toggle elements.
- Skip, Taken and the widget's Take now button are at least 48dp tall; picker targets are 48dp.
- Raised the dark-theme input outline contrast; pill icons are darkened on the light theme.

## [0.5.0] - 2026-11-24

### Added
- **Home-screen widget** (4x2, as-needed medicines). A thin ring per medicine in its own colour fills as the wait ends, with the ready time ("Ready at 9:41 PM") and a Take now button. A ready medicine is logged in one tap; one that is waiting, at its daily max or sharing an ingredient opens the app's early-dose confirmation. Redraws after every dose and when a wait ends.
- **App lock.** Optional fingerprint / face / phone-PIN prompt when jPill opens (Home menu > Privacy), with a timing choice (immediately, 30 s, 1, 5 or 15 min). A cold start always asks. The recent-apps card hides your medicines while the lock is on, and the lock turns itself off if the phone's screen lock is removed so you can't be locked out.
- **Hide medicine names on the lock screen.** Notifications show "Medication reminder" while the phone is locked; Taken, Skip and Snooze still work without unlocking.
- The Privacy screen explains why data is not encrypted: background reminders can't use a fingerprint-gated key, and Keystore keys don't survive restoring a backup.

## [0.4.2] - 2026-11-24

### Changed
- **Smaller medicine tiles on Home.** Tighter padding, smaller text and icon, the dose time shares a line with the dose, and the Skip/Taken buttons are shorter. An as-needed tile now shows its status and "Take now" on one row. Roughly four tiles fit on a screen instead of two or three.

### Changed
- **New look across the app:** graphite-blue dark and light themes with one ice-blue accent, flat outlined cards, larger corner radii and a lighter type scale. Pill icons are lightened on the dark theme so dark colours stay visible.

### Fixed
- A dose taken up to 2 hours before its due time is no longer logged as Missed afterwards (the missed-dose check now uses the same early window, per schedule).

## [0.4.1] - 2026-11-24

### Fixed
- **Doses taken early no longer re-fire their reminder.** A scheduled dose you take or skip up to 2 hours before it is due now counts as that dose: the alarm moves to the next occurrence and no reminder or missed-dose entry follows.
- **Home shows what you logged.** A taken or skipped dose stays on the list greyed out ("Taken at 7:30 AM") with its buttons disabled until it comes due, so it can't be logged twice. The list now refreshes as soon as a dose is logged.

## [0.4.0] - 2026-11-24

### Added
- **Duplicate-ingredient warning.** When two of your medicines share an active ingredient (Tylenol plus a cold medicine, both acetaminophen), both cards show a banner. Their doses over the last 24 hours are added together and counted against the lowest daily limit among them, in the same unit. If your next dose would pass that shared limit, "Take now" asks for confirmation. It warns only: logging is never blocked.
- Database version 6 (non-destructive: nullable `ingredients` on pills). Medicines you added before this update have no ingredients; open one and re-pick it from the search to fill them in.
- **Timers.** A new Timers tab for general-purpose countdowns: label (optional), hours/minutes/seconds or a quick preset (1-60 min), pause, resume, restart, delete. A timer keeps running with the app closed, rings with a notification when it ends, and survives a reboot (overdue ones are marked done and notified at boot).
- **Bottom navigation:** Medicines | Timers | History. History moved out of the Medicines menu (Archived stays there).
- Database upgraded to version 5 (non-destructive: adds a `timers` table).
- **Pill colors and icons.** Pick one of 12 colors and 12 icons in the medication form; both show on every card (scheduled and as-needed).
- **Unit dropdown** (mg, ml, pill, IU, mcg, other) replaces the read-only unit field. A unit set by a catalog pick or strength chip (e.g. "g") still shows.

## [0.3.0] - 2026-11-24

### Added
- **Dose history screen.** Every logged dose across all medications, archived ones included (tagged), grouped by day with time, status and amount. Filter by medication and by status (Taken / Skipped / Missed).
- **Missed-dose detection.** A check at due time plus a grace window (60 min, at most half of a short interval) logs `MISSED` if nothing was logged. Skips archived and as-needed medications; doses missed while the phone is off are not back-filled.
- Home top-bar menu with **History** and **Archived**, always visible (Archived used to appear only once something was archived).

### Fixed
- An ignored reminder no longer silences the schedule: the missed check also plans the next dose. Before, nothing did until the next Taken/Skip/Snooze or a reboot.

## [0.2.0] - 2026-11-23

### Added
- **Prescriptions.** Search ~120 curated prescriptions by generic or brand name (Lipitor, Zoloft, ...). A pick fills the name, strength chips, food/timing notes and a typical schedule. It never fills a dose or limits: those come from your prescriber.
- Bundled offline name list (from the openFDA NDC directory, public domain) fills gaps beyond the curated list.
- **Supply and refills:** tablets on hand drops with every taken dose (home button, Take now, or the notification), cards show "N left, about D days" and turn red when low, and a one-time refill reminder fires at a threshold you set (default 7 days).
- Tablets per dose (strength x tablets, e.g. 2 x 500 mg) for every pill; dose log and cooldown totals use the full amount.
- Food and timing notes (preset chips plus free text); prescriber, pharmacy and Rx number fields.
- Weekly schedules: "Certain days" with a weekday picker.
- Room schema export, with instrumented tests for the migrations (v2 to v3, v3 to v4, v2 to v4), the atomic supply decrement, delete and archive.
- **Archive a medication.** The Edit screen has an "Archive (keeps history)" button. An archived medication disappears from the home screen and all its reminders stop (schedule alarms, "available again" and refill alarms, notifications already showing), but its schedule, supply and full dose history are kept. A new **Archived** screen (button appears in the top bar when something is archived) lists archived medications with doses taken and last taken date, and lets you **Restore** (reminders re-arm) or **Delete** permanently.
- Refill reminders escalate: one "refill soon" when supply is low and one "you're out of ..." when it runs out, each once per low spell.

### Fixed
- **You can now delete a medication.** The Edit screen has a "Delete medication" button with a confirmation. Deleting also cancels its alarms and any reminder, "available again" or refill notification already showing, clears its refill flag, and removes its schedules and dose history. Before, the delete code existed but nothing in the app could trigger it.

### Changed
- Database upgraded to version 4 (non-destructive migrations: prescriptions in v3, archiving in v4).
- The "Common Medicines" search now covers prescriptions and the name list as well as OTC.

### Notes
- **Internal use only for now.** The prescription catalog (`RxCatalog`) and the remaining OTC values (naproxen, antihistamines, famotidine, guaifenesin, dextromethorphan) are typical-adult values written from general knowledge and have not been reviewed against current labels. A review page exists for doing that before any wider release. Prescriptions never get a dose or limits from the app; those always come from the prescriber's label.
- The bundled name list (826 drugs) comes from the openFDA NDC directory (public domain); see `docs/DATASETS.md`.

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

[Unreleased]: https://github.com/jagger/jpill/compare/v0.6.1...HEAD
[0.6.1]: https://github.com/jagger/jpill/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/jagger/jpill/compare/v0.5.1...v0.6.0
[0.5.1]: https://github.com/jagger/jpill/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/jagger/jpill/compare/v0.4.2...v0.5.0
[0.4.2]: https://github.com/jagger/jpill/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/jagger/jpill/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/jagger/jpill/compare/v0.2.0...v0.4.0
[0.3.0]: https://github.com/jagger/jpill/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/jagger/jpill/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/jagger/jpill/compare/v0.0.3...v0.1.0
[0.0.3]: https://github.com/jagger/jpill/compare/v0.0.1...v0.0.3
[0.0.1]: https://github.com/jagger/jpill/releases/tag/v0.0.1
