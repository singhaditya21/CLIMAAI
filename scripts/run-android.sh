#!/bin/zsh
# Launch ClimaAI on a visible Android emulator on this Mac. No phone needed.
#
#   ./scripts/run-android.sh
#
# Boots the emulator with a window, waits for it, builds and installs the debug
# app, grants its permissions and launches it. Safe to re-run — it reuses an
# already-running emulator instead of starting a second one.
#
# The debug build talks to http://10.0.2.2:8000, which is how the emulator
# reaches a server on this Mac, so start the backend first:
#
#   cd backend/api && .venv/bin/python -m uvicorn app.main:app --port 8000
#
# Without it the app still runs but shows no weather.

set -u
cd "$(dirname "$0")/.." || exit 1

: ${ANDROID_HOME:="$HOME/Library/Android/sdk"}
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
AVD=${AVD_NAME:-climaai_test}
PKG=com.climaai.app.debug

# Gradle 8.13 cannot parse class files from JDK 24+; prefer a supported JDK.
if [[ -z "${JAVA_HOME:-}" ]] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE '"(17|21)\.'; then
  for candidate in /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
                   /opt/homebrew/opt/openjdk@21 /opt/homebrew/opt/openjdk@17; do
    [[ -x "$candidate/bin/java" ]] && export JAVA_HOME=$candidate && break
  done
fi
print "JAVA_HOME: ${JAVA_HOME:-<unset>}"

[[ -x "$ADB" ]] || { print "adb not found at $ADB — install the Android SDK"; exit 1; }

if ! "$ADB" devices | grep -q "emulator.*device"; then
  print "Starting emulator '$AVD' (a window will open)…"
  # -gpu host for hardware rendering; the UI uses blur and gradients.
  "$EMULATOR" -avd "$AVD" -gpu host -no-boot-anim >/dev/null 2>&1 &
  print -n "Waiting for boot"
  for i in $(seq 1 90); do
    if [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
      print " ready"
      break
    fi
    print -n "."
    sleep 3
  done
else
  print "Emulator already running — reusing it."
fi

if [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; then
  print "Emulator did not finish booting. Try again, or open it from Android Studio."
  exit 1
fi

print "\nBuilding debug app…"
( cd android && ./gradlew --quiet :app:assembleDebug ) || { print "Build failed."; exit 1; }

print "Installing…"
"$ADB" install -r android/app/build/outputs/apk/debug/app-debug.apk >/dev/null || exit 1

for perm in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS; do
  "$ADB" shell pm grant "$PKG" "android.permission.$perm" 2>/dev/null
done

# Without a fix the emulator reports no location and the app falls back to a
# default. Change these to move the app somewhere else.
"$ADB" emu geo fix -0.1278 51.5074 >/dev/null 2>&1

"$ADB" shell am start -n "$PKG/com.climaai.app.MainActivity" >/dev/null 2>&1

if curl -sf http://127.0.0.1:8000/health >/dev/null 2>&1; then
  print "\nBackend is up — the app will load live weather."
else
  print "\nNo backend on :8000. The app will run but show no weather."
  print "Start it with: cd backend/api && .venv/bin/python -m uvicorn app.main:app --port 8000"
fi

print "\nClimaAI is running in the emulator window."
print "Tap 'Skip' on the onboarding screen to reach the weather view."
