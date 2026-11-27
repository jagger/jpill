# Changelog

All notable changes to jPill are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Fixed
- **Snooze could erase tomorrow's reminder.** A snooze reused the alarm slot of the schedule's next regular reminder, so after snoozing, the next day's alarm was gone until a dose was logged. Snooze is now its own alarm.

### Changed
- **Timed doses ring like an alarm.** Reminders for a dose with a specific time (Fixed time, Interval) now play your phone's alarm sound and vibrate on the alarm volume, so silent mode and Do Not Disturb (which allows alarms) no longer swallow them. This uses a new notification channel, "Pill Reminders"; the old one is removed.
- **Doses with no exact time (a Morning/Evening/Night slot) get a quieter reminder.** They use a separate channel, "Daily doses (no set time)": a normal notification sound, no full-screen takeover, and the notification stays until you tap Taken or Skip. A late dose is fine, so a slot dose is only logged as missed after 6 hours instead of 1.
- **The reminder stays until you deal with it.** It is no longer cleared when its dose is logged as missed, and it now disappears as soon as you mark the dose Taken or Skipped inside the app (before, it lingered).

## [0.10.1] - 2026-11-26

### Added
- **Reminder log** (Settings > Reminder log). A trace of everything the reminder machinery does: every alarm armed (and for when), every alarm that fired, every notification posted and whether Android accepted it, the Do Not Disturb state at that moment, Taken/Skip/Snooze taps, and when a dose was logged as missed because its reminder alarm never fired. The screen also lists the next 48 hours of doses with whether each one's alarm is actually registered with Android, and has **Re-arm all**, **Share** and **Clear**. The log stays on the phone in jPill's own folder.
- **Self-healing alarms.** Each time jPill opens it re-arms any scheduled medicine whose alarm is missing.

### Changed
- **Reminders are posted directly by the alarm instead of through a foreground service.** Two medicines due at the same minute used to replace each other's notification, tapping Taken on one dismissed the other, and a refused service start lost the reminder without a trace. Each medicine now gets its own notification, and a failure to post is logged. The foreground-service permissions are gone from the manifest. A reminder that is ignored is now cleared when its dose is logged as missed.

## [0.10.0] - 2026-11-25

### Added
- **"What's new" popup after an update.** The first time you open jPill after updating, a popup lists what changed since the version you last used (every version in between, newest first) and stays until you tap **Got it**. It reads the changelog bundled inside the app, so it works offline. Nothing is shown on a fresh install, and it waits behind the app lock if that is on. **Settings > About > What's new** shows the last five versions' notes any time.

## [0.9.2] - 2026-11-25

### Added
- **Edit or delete a History entry.** Long-press a row in History to change its status (Taken, Skipped, Missed), date, time and amount, or delete it (with a confirmation). Supply stays right: deleting a taken dose, or changing it to something else, puts the tablets back on a medicine that tracks supply; changing a skipped or missed entry to Taken uses them. The medicine's reminders are re-planned afterwards.

### Fixed
- **Reminders vanished after updating the app.** Android cancels every alarm an app set when it is updated, and jPill only re-armed them after a reboot, so the first dose reminders after an update never fired. They are now re-armed right after an update, and when the clock or time zone changes.
- **Tapping Taken twice logged two doses.** An identical Taken or Skipped log for the same medicine within 10 seconds is now treated as the same dose (also covers tapping the notification button and the app together).

## [0.9.1] - 2026-11-25

### Added
- **Tappable web addresses in a medicine's details.** A website in the Doctor, Rx number or Pharmacy field (for example your pharmacy's refill page: `https://...`, `www....`, or `example.com/refill`) is underlined and opens in your browser when you tap it in the expanded medicine card. While editing it stays plain text. Only http and https links are ever made tappable.

## [0.9.0] - 2026-11-25

### Added
- **Reminder reliability check.** jPill now checks the things that stop reminders arriving on time: notifications (including the reminder channel being switched off), battery use (unrestricted), full-screen reminders (Android 14+) and exact alarms. When something is missing, Home shows a dismissible banner ("Reminders may be late or missing: battery use isn't unrestricted") with a **Fix** button; "Not now" hides it for a week. **Settings > Reminder reliability** lists every check with a one-tap fix (the system's own "Allow jPill to always run in the background?" prompt for battery, the right Settings page for the rest). It re-checks when you come back from Settings. On Samsung phones it also reminds you to take jPill off the "Sleeping apps" lists, which Android gives no way to check. Adds the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission (not a network permission; jPill still has no internet access).

## [0.8.1] - 2026-11-25

### Added
- **docs/INSTALL.md**: a full sideloading guide (download, allowing the install, Play Protect, reminder reliability settings, verifying the download, updating, uninstalling, troubleshooting), linked from the README and every release.

### Changed
- **History heatmap redesigned in the style of GitHub's contribution graph, now covering 90 days:** small tight squares, weeks as columns and weekdays as rows (Sunday first, Mon/Wed/Fri labelled), with one line underneath giving the overall percentage and a Less/More legend. Tap a square for that day's numbers.

### Fixed
- The History list no longer opens scrolled past the heatmap: it now has its full size from the first frame instead of growing once the data loads.

## [0.8.0] - 2026-11-25

### Added
- **Signed release APKs** for sideloading: attached to each GitHub release with a checksum, signed with the project key, shrunk with R8 (about 4.5 MB). Updates install over the previous release and keep your data. See README, Install on your phone.
- **30-day adherence heatmap** at the top of History: a calendar grid of halftone blocks, one per day. Each block is a 4 x 4 dot matrix whose dots grow with the share of scheduled doses taken that day and merge into a solid block at 100%; an empty red outline means doses were scheduled but none taken, a faint dot means none were scheduled. Tap a day for the numbers. Follows the medication filter. Only scheduled doses count (as-needed doses have no expected number).
- **Beta features** screen (Settings > Beta features): features still being tested stay off until you switch them on. NFC tags is the first.
- **NFC tags for medicines and timers (beta, off by default).** Switch on in Settings > Beta features. Stick an NFC sticker on a medicine's lid (or on the washer) and tap your phone to it.
  - **Medicines:** tapping opens a small confirm card (it never opens the whole app, so it works with the app lock on); confirming logs the dose. A scheduled dose inside the 2 hour early window counts toward that dose, anything else asks first; as-needed medicines follow the wait, daily-max and shared-ingredient rules with the same "take it early?" confirmation; a repeat scan of a dose already logged says so instead of logging twice.
  - **Timers:** tapping starts that timer (for example "Washer, 45:00"); a running timer offers Restart, a paused one Resume.
  - **Setting up a tag:** in a medicine's edit screen (NFC tag section) or from the NFC button on a timer, Write tag, Rewrite tag (reuses the same id, so a replacement sticker keeps working), Link existing tag (medicines), or Remove tag. It warns before overwriting a tag that already holds other data and verifies what it wrote.
  - **What is on the tag:** a random id and the name (the medicine's name is optional per tag, a timer tag also holds its length), as one NFC link such as `jpill://timer/<id>?label=Washer&secs=2700`. Never a dose. Because timer tags are self-describing, scanning one on a fresh install or after deleting the timer recreates it.
  - A tag jPill doesn't recognise (new phone) offers "Add as new medicine" with the name and the tag already filled in.
  - Needs a phone with NFC; the app still works without it.
- Database version 7 (non-destructive: an optional unique `nfcTagId` on medicines and on timers). Existing data is untouched.

## [0.7.0] - 2026-11-25

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

[Unreleased]: https://github.com/jagger/jpill/compare/v0.10.1...HEAD
[0.10.1]: https://github.com/jagger/jpill/compare/v0.10.0...v0.10.1
[0.10.0]: https://github.com/jagger/jpill/compare/v0.9.2...v0.10.0
[0.9.2]: https://github.com/jagger/jpill/compare/v0.9.1...v0.9.2
[0.9.1]: https://github.com/jagger/jpill/compare/v0.9.0...v0.9.1
[0.9.0]: https://github.com/jagger/jpill/compare/v0.8.1...v0.9.0
[0.8.1]: https://github.com/jagger/jpill/compare/v0.8.0...v0.8.1
[0.8.0]: https://github.com/jagger/jpill/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/jagger/jpill/compare/v0.6.1...v0.7.0
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
