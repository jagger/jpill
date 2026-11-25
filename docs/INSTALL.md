# Installing jPill on your phone (sideloading)

jPill is not in an app store. You install it by downloading the APK file from GitHub and opening it. It takes about two minutes.

**You need:** an Android phone running **Android 15 or newer**.

## 1. Download

1. On your phone, open <https://github.com/jagger/jpill/releases/latest>.
2. Under **Assets**, tap `jpill-<version>.apk` to download it. (The `.sha256` file next to it is for [checking the download](#4-check-the-download-optional-but-recommended).)

## 2. Allow the install

Android only installs apps from outside the Play Store if you allow it, once, for the app you downloaded with (usually Chrome or Files):

1. Open the downloaded `.apk` (tap the download notification, or find it in **Files > Downloads**).
2. Android says *"For your security, your phone isn't allowed to install unknown apps from this source."* Tap **Settings**, switch on **Allow from this source**, then go back.
3. Tap **Install**.
4. If **Play Protect** shows a warning about an unknown developer, tap **More details**, then **Install anyway**. jPill is open source and not published through Google, so Play Protect has not seen it before.
5. Tap **Open**.

You can switch **Allow from this source** off again afterwards.

## 3. Set it up so reminders arrive on time

When jPill first opens it asks to send notifications: tap **Allow**. Then, because phones aggressively stop background apps:

- **Battery:** Settings > Apps > jPill > Battery > **Unrestricted** (on Samsung also make sure jPill is not in *Sleeping apps* or *Deep sleeping apps*).
- **Full-screen reminders (Android 14+):** Settings > Apps > Special app access > **Full screen notifications** > jPill > allow. Without it a reminder still shows as a notification, just not over the lock screen.
- **Do Not Disturb:** reminders are alarms; if you use Do Not Disturb, allow alarms or make jPill an exception.

## 4. Check the download (optional but recommended)

Every release lists two fingerprints:

- the **APK checksum** (also in the `.sha256` file): `sha256sum -c jpill-<version>.apk.sha256` on a computer should print `OK`;
- the **signing certificate** SHA-256, which is the same for every official release:

  ```
  3527252d8290f901f5fa6dc8f80abd0dc2e91b768b9f6684faa0b68a6528ebaf
  ```

  Check it with `apksigner verify --print-certs jpill-<version>.apk` (Android SDK build-tools). If it differs, do not install the file.

## Updating

Download the newer APK from the Releases page and install it over the old one. **Your medicines and history are kept**, because every official release is signed with the same key. There is no automatic update: watch the repository's Releases (Watch > Custom > Releases) to be notified.

## Uninstalling

Long-press the icon > **App info** > **Uninstall**. **This deletes all your jPill data** (medicines, history, timers). Before you do, open **History** and use the share icon to save a CSV of your history.

## Troubleshooting

| What you see | What to do |
|---|---|
| *App not installed as package conflicts with an existing package* | The installed jPill was signed with a different key (for example a build you made yourself). Uninstall it first, which deletes its data (export a CSV first), then install again. |
| *There was a problem parsing the package* or *App not compatible* | The download is incomplete, or the phone is older than Android 15. Download again and check the Android version. |
| Install button greyed out or nothing happens | Allow **Install unknown apps** for the app you opened the file from (step 2). |
| Reminders are late or missing | Do step 3: set Battery to Unrestricted and allow notifications. Open the app once after a restart. |
| Not sure it is the real app | Do step 4. Only download from <https://github.com/jagger/jpill/releases>. |

## Beta features

NFC tags (tap a sticker to confirm a dose or start a timer) are in beta and off by default. Turn them on in **Settings > Beta features**.

## Build it yourself instead

See [Build from source](../README.md#build-from-source). A build you make is signed with your own key, so it cannot update, or be updated by, the official releases.
