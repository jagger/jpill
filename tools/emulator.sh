#!/usr/bin/env bash
# Local Android emulator for jPill (Android 15 / API 35, the app's minSdk), for when no phone is attached.
#
#   tools/emulator.sh create      install the system image and create the AVD (once)
#   tools/emulator.sh start       boot headless and wait until ready
#   tools/emulator.sh status      is it running?
#   tools/emulator.sh install     build the debug APK and install it on the emulator
#   tools/emulator.sh test        run the instrumented tests on the emulator
#   tools/emulator.sh shot FILE   save a screenshot
#   tools/emulator.sh stop        shut it down
#
# The emulator always uses serial emulator-5554. When a phone is also attached, every command here targets the
# emulator only, so it never touches the phone. Unlike a real phone, the emulator's data is disposable, so
# `test` (Gradle connectedDebugAndroidTest, which uninstalls the app afterwards) is safe here.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
AVD="jpill_api35"
IMG="system-images;android-35;google_apis;x86_64"
PORT=5554
SERIAL="emulator-$PORT"
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"
JAVA_HOME_FOR_GRADLE="${JPILL_JAVA_HOME:-$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2}"
RAM_MB="${JPILL_EMU_RAM_MB:-2048}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG="${TMPDIR:-/tmp}/jpill-emulator.log"

adb_emu() { "$ADB" -s "$SERIAL" "$@"; }
is_running() { "$ADB" devices 2>/dev/null | grep -q "^$SERIAL[[:space:]]*device"; }

cmd_create() {
  export PATH="$SDK/cmdline-tools/latest/bin:$PATH"
  if [ ! -d "$SDK/system-images/android-35/google_apis/x86_64" ]; then
    echo "Installing $IMG (large download)..."
    sdkmanager "$IMG"
  fi
  if "$SDK/cmdline-tools/latest/bin/avdmanager" list avd 2>/dev/null | grep -q "Name: $AVD"; then
    echo "AVD $AVD already exists."
  else
    echo "no" | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n "$AVD" -k "$IMG" -d pixel_8 --force
  fi
  local ini="$HOME/.android/avd/$AVD.avd/config.ini"
  # Modest memory/cores so it coexists with a busy workstation; storage for the debug APK and test runs.
  sed -i -e '/^hw.ramSize/d' -e '/^hw.cpu.ncore/d' -e '/^disk.dataPartition.size/d' "$ini"
  printf 'hw.ramSize=%s\nhw.cpu.ncore=4\ndisk.dataPartition.size=6G\n' "$RAM_MB" >> "$ini"
  echo "AVD ready: $AVD (RAM ${RAM_MB} MB)"
}

cmd_start() {
  if is_running; then echo "Already running ($SERIAL)."; return 0; fi
  echo "Starting $AVD headless (log: $LOG)..."
  nohup "$EMU" -avd "$AVD" -port "$PORT" -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect \
    -no-metrics >"$LOG" 2>&1 &
  "$ADB" start-server >/dev/null 2>&1 || true
  local waited=0
  until is_running; do
    sleep 3; waited=$((waited + 3))
    [ $waited -ge 240 ] && { echo "Timed out waiting for the device. See $LOG"; exit 1; }
  done
  until [ "$(adb_emu shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 3; waited=$((waited + 3))
    [ $waited -ge 480 ] && { echo "Timed out waiting for boot. See $LOG"; exit 1; }
  done
  adb_emu shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
  adb_emu shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
  adb_emu shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
  echo "Booted: Android $(adb_emu shell getprop ro.build.version.release | tr -d '\r') (API $(adb_emu shell getprop ro.build.version.sdk | tr -d '\r'))."
}

cmd_status() {
  if is_running; then echo "running ($SERIAL)"; else echo "not running"; exit 1; fi
}

cmd_install() {
  is_running || { echo "Emulator is not running: tools/emulator.sh start"; exit 1; }
  (cd "$ROOT" && JAVA_HOME="$JAVA_HOME_FOR_GRADLE" ./gradlew assembleDebug -q)
  adb_emu install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
}

cmd_test() {
  is_running || { echo "Emulator is not running: tools/emulator.sh start"; exit 1; }
  (cd "$ROOT" && ANDROID_SERIAL="$SERIAL" JAVA_HOME="$JAVA_HOME_FOR_GRADLE" ./gradlew connectedDebugAndroidTest)
}

cmd_shot() {
  is_running || { echo "Emulator is not running"; exit 1; }
  adb_emu exec-out screencap -p > "${1:?usage: emulator.sh shot FILE}"
}

cmd_stop() {
  if is_running; then adb_emu emu kill >/dev/null 2>&1 || true; echo "Stopped."; else echo "Not running."; fi
}

case "${1:-}" in
  create) cmd_create ;;
  start) cmd_start ;;
  status) cmd_status ;;
  install) cmd_install ;;
  test) cmd_test ;;
  shot) shift; cmd_shot "$@" ;;
  stop) cmd_stop ;;
  *) sed -n '2,15p' "$0"; exit 1 ;;
esac
