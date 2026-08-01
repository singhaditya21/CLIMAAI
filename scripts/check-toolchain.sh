#!/bin/zsh
# Reports whether the local toolchain can build each part of this project.
# Read-only: installs nothing, accepts no licences, changes no settings.
#
#   ./scripts/check-toolchain.sh

setopt NO_ERR_EXIT 2>/dev/null
ok()   { print -P "  %F{green}OK%f    $1" }
bad()  { print -P "  %F{red}MISSING%f $1" }
warn() { print -P "  %F{yellow}NOTE%f  $1" }

print "\n── Backend ─────────────────────────────────────────────"
if command -v psql >/dev/null 2>&1; then
  if pg_isready -q 2>/dev/null; then ok "Postgres running ($(psql --version | awk '{print $3}'))"
  else bad "Postgres installed but not accepting connections"; fi
else bad "Postgres (psql not on PATH)"; fi

if [[ -x backend/api/.venv/bin/python ]]; then
  ok "backend venv ($(backend/api/.venv/bin/python --version 2>&1))"
else
  bad "backend venv — run: cd backend/api && python3.11 -m venv .venv && .venv/bin/pip install -r requirements.txt"
fi

print "\n── Android ─────────────────────────────────────────────"
JDK_OK=0
if [[ -n "$JAVA_HOME" && -x "$JAVA_HOME/bin/java" ]]; then
  JV=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
  if [[ "$JV" == "17" || "$JV" == "21" ]]; then ok "JDK $JV via JAVA_HOME"; JDK_OK=1
  else warn "JAVA_HOME points at JDK $JV — Gradle 8.13 needs 17 or 21"; fi
else
  warn "JAVA_HOME unset — Gradle 8.13 needs JDK 17 or 21 (a newer JDK fails to parse class files)"
fi

[[ -f android/gradle/wrapper/gradle-wrapper.jar ]] && ok "gradle-wrapper.jar present" || bad "gradle-wrapper.jar"

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
[[ -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]] && ok "sdkmanager at $SDK" || bad "Android cmdline-tools"
[[ -d "$SDK/platforms/android-34" ]] && ok "platform android-34" || bad "platform android-34 (sdkmanager 'platforms;android-34')"
[[ -d "$SDK/build-tools" ]] && ok "build-tools ($(ls "$SDK/build-tools" 2>/dev/null | tr '\n' ' '))" || bad "build-tools"
[[ -f android/local.properties ]] && ok "android/local.properties" || bad "android/local.properties (sdk.dir=...)"

if [[ $JDK_OK == 1 && -d "$SDK/platforms/android-34" ]]; then
  print "  → ready: cd android && ./gradlew assembleDebug"
fi

print "\n── iOS ─────────────────────────────────────────────────"
DEV=$(xcode-select -p 2>/dev/null)
if [[ "$DEV" == *"Xcode.app"* ]]; then
  ok "Xcode selected ($DEV)"
  if xcodebuild -version >/dev/null 2>&1; then
    ok "xcodebuild ($(xcodebuild -version | head -1))"
    print "  → ready: cd ios && xcodebuild -scheme ClimaAI -destination 'platform=iOS Simulator,name=iPhone 16' build"
  else
    bad "xcodebuild refuses to run — try: sudo xcodebuild -license accept"
  fi
else
  bad "full Xcode (currently: ${DEV:-none}). Install Xcode, then:"
  print "        sudo xcode-select -s /Applications/Xcode.app/Contents/Developer"
fi
[[ -f ios/ClimaAI.xcodeproj/project.pbxproj ]] && ok "ClimaAI.xcodeproj present" || bad "ClimaAI.xcodeproj"

print "\n── Deploy (optional) ───────────────────────────────────"
if command -v gh >/dev/null 2>&1; then
  SECRETS=$(gh secret list 2>/dev/null | awk '{print $1}' | tr '\n' ' ')
  for s in GCP_SA_KEY GCP_PROJECT_ID; do
    [[ "$SECRETS" == *"$s"* ]] && ok "secret $s set" || bad "secret $s (the deploy job fails at auth without it)"
  done
else
  warn "gh CLI not found — cannot check repository secrets"
fi
print ""
