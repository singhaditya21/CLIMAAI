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

# This build needs JDK 17 or 21. Newer JDKs are not merely unsupported by
# Gradle 8.13 — kapt fails outright on them, with "Failed to calculate the value
# of task ':app:kaptDebugKotlin' property 'javacOptions' > 26.0.1", which does
# not mention Java at all. Check up front so that failure is never a surprise.
if [[ -z "${JAVA_HOME:-}" ]] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE '"(17|21)[.\"]'; then
  unset JAVA_HOME
  for candidate in \
      $(/usr/libexec/java_home -v 21 2>/dev/null) \
      $(/usr/libexec/java_home -v 17 2>/dev/null) \
      /Library/Java/JavaVirtualMachines/*/Contents/Home \
      /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
      /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
      "/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
    if [[ -x "$candidate/bin/java" ]] && \
       "$candidate/bin/java" -version 2>&1 | grep -qE '"(17|21)[.\"]'; then
      export JAVA_HOME=$candidate
      break
    fi
  done
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  print "No JDK 17 or 21 found, and the build cannot run without one."
  print ""
  print "Install one, then re-run this script:"
  print "  brew install openjdk@21"
  print "  sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \\"
  print "               /Library/Java/JavaVirtualMachines/openjdk-21.jdk"
  print ""
  print "Installing Android Studio also works — it ships a JDK 21 that this"
  print "script picks up automatically."
  exit 1
fi
print "JAVA_HOME: $JAVA_HOME"

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

"$ADB" shell am start -n "$PKG/com.climaai.app.MainActivity" >/dev/null 2>&1

# A fresh emulator has no location at all, so the app would sit on its error
# screen. Feed it one.
#
# This has to happen *after* the app launches: `geo fix` injects into the GPS
# provider, and the provider only runs while something is subscribed to it.
# Sent before launch, the fix is silently dropped. Send it twice — once as the
# app starts requesting, once after it is definitely listening.
# Edit these coordinates (longitude first) to move the app elsewhere.
: ${GEO_LON:=-0.1278}
: ${GEO_LAT:=51.5074}
for _ in 1 2; do
  sleep 3
  "$ADB" emu geo fix "$GEO_LON" "$GEO_LAT" >/dev/null 2>&1
done

if curl -sf http://127.0.0.1:8000/health >/dev/null 2>&1; then
  print "\nBackend is up — the app will load live weather."
else
  print "\nNo backend on :8000. The app will run but show no weather."
  print "Start it with: cd backend/api && .venv/bin/python -m uvicorn app.main:app --port 8000"
fi

print "\nClimaAI is running in the emulator window."
print "Tap 'Skip' on the onboarding screen to reach the weather view."
print "\nLocation is set to ${GEO_LAT}, ${GEO_LON}. Override with:"
print "  GEO_LAT=40.7128 GEO_LON=-74.0060 ./scripts/run-android.sh"
