#!/usr/bin/env bash
# Builds the signed, shrunk release APK and prepares it for a GitHub release.
#
#   tools/build_release.sh        -> build/release/jpill-<version>.apk and .sha256
#
# Needs keystore.properties at the project root (gitignored; see CLAUDE.md "Release signing"). It refuses to
# produce an unsigned APK, verifies the signature, and prints the signing certificate fingerprint, which must
# match the one published in README.md.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

[ -f keystore.properties ] || { echo "keystore.properties is missing: cannot sign. See CLAUDE.md (Release signing)." >&2; exit 1; }

export JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2}"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
[ -x "$APKSIGNER" ] || { echo "apksigner not found under $SDK/build-tools" >&2; exit 1; }

VERSION="$(sed -n 's/^val appVersion = "\(.*\)"/\1/p' app/build.gradle.kts)"
./gradlew :app:assembleRelease -q

SRC="app/build/outputs/apk/release/app-release.apk"
OUT="build/release"
mkdir -p "$OUT"
APK="$OUT/jpill-$VERSION.apk"
cp "$SRC" "$APK"

echo "--- signature"
"$APKSIGNER" verify --verbose --print-certs "$APK" | grep -E "^Verifies|Number of signers|certificate (DN|SHA-256)"
( cd "$OUT" && sha256sum "jpill-$VERSION.apk" > "jpill-$VERSION.apk.sha256" && cat "jpill-$VERSION.apk.sha256" )
echo "--- ready: $APK (attach it and the .sha256 file to the GitHub release)"
