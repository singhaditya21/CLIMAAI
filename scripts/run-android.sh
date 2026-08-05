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
# zsh aborts on a glob that matches nothing, and the JDK search below globs
# directories that legitimately may not exist on a given machine.
setopt NULL_GLOB
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

# The emulator has no GPS radio. It reports only coordinates injected into it,
# so it can never discover where you actually are — set them here.
: ${GEO_LAT:=28.6720}   # Chander Nagar, Ghaziabad
: ${GEO_LON:=77.3560}

# Injection is fiddly, and the reasons are worth writing down:
#
#   1. `geo fix` reaches the GPS provider only while that provider is running,
#      and it runs only while an app is subscribed. The app subscribes for a few
#      seconds at launch and then stops. Injecting outside that window is
#      accepted — the console still answers OK — and silently discarded.
#   2. That window cannot be predicted precisely, so rather than guess, inject
#      once a second across it and let one land.
#   3. A fix already held by the provider survives app restarts and is handed
#      straight back, so a stale one from an earlier session wins over anything
#      injected later. Only rebooting the emulator clears it.
#
# Hence: launch first, then inject repeatedly, in the background so the script
# stays responsive.
( for _ in $(seq 1 25); do
    "$ADB" emu geo fix "$GEO_LON" "$GEO_LAT" >/dev/null 2>&1
    sleep 1
  done ) &

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
