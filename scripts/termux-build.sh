#!/data/data/com.termux/files/usr/bin/bash
# GlacierLauncher: Termux build helper. It only CHECKS and BUILDS; it never fakes success.
# Honest caveat: Google's Android build-tools (aapt2) are x86-64 Linux binaries and do not run
# on Termux/ARM. This script therefore needs a Termux-native aapt2 (pkg install aapt2) and
# passes it to Gradle. If any check fails, use the GitHub Actions workflow instead.
set -u
cd "$(dirname "$0")/.." || exit 1
fail=0
need() { command -v "$1" >/dev/null 2>&1 && echo "OK   $1" || { echo "MISS $1  ->  $2"; fail=1; }; }

need java  "pkg install openjdk-17"
need git   "pkg install git"
need gradle "pkg install gradle   (only needed to create the wrapper)"
if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "${ANDROID_HOME}/platforms/android-34" ]; then
  echo "MISS Android SDK platform 34 (set ANDROID_HOME to an SDK containing platforms/android-34)"; fail=1
else echo "OK   ANDROID_HOME=$ANDROID_HOME"; fi
AAPT2="$(command -v aapt2 || true)"
[ -n "$AAPT2" ] && echo "OK   aapt2 ($AAPT2)" || { echo "MISS aapt2  ->  pkg install aapt2"; fail=1; }

if [ "$fail" -ne 0 ]; then
  echo; echo "Environment incomplete. Easiest alternative: push to GitHub and download the APK from the Actions tab."
  exit 1
fi

[ -f gradlew ] || gradle wrapper --gradle-version 8.9
chmod +x gradlew
echo "sdk.dir=$ANDROID_HOME" > local.properties   # git-ignored
./gradlew :app:assembleDebug -Pandroid.aapt2FromMavenOverride="$AAPT2" || { echo "Build failed (see log above)."; exit 1; }
find app/build/outputs/apk -name '*.apk'
