#!/usr/bin/env bash
# Release-config gate — the things that build fine and then fail somewhere no
# test can see them.
#
#   ./scripts/ci/check-release-config.sh
#
# Two failure horizons, both invisible to Gradle:
#
#   at upload   Play rejects the bundle outright — targetSdk below the current
#               floor, a Billing Library version past its sunset. The build is
#               green, the release is blocked, and nobody finds out until the
#               tag is already cut.
#   in the user's hands
#               nothing rejects anything. A placeholder API key returns 401 and
#               the map tiles are blank. The release host does not resolve and
#               every request times out. A runtime permission is checked but
#               never requested, so the guard is permanently false and the
#               feature silently does nothing.
#
# Needs no JDK, no Android SDK and no Gradle: it reads config text and asks DNS
# one question, so it can gate a PR in seconds before any build starts.
#
# Deliberately bash, not zsh: this runs on the ubuntu-latest CI runners, which
# ship no zsh. A zsh shebang there fails as "cannot execute: required file not
# found" — an error naming neither zsh nor this script.

set -u
cd "$(dirname "$0")/../.." || exit 1

GRADLE=android/app/build.gradle
MANIFEST=android/app/src/main/AndroidManifest.xml

# Play's floor for new submissions and updates, bumped every August. 35 has
# applied since 2025-08-31; 36 takes over on 2026-08-31. Raise this together
# with targetSdk when that date passes — leaving it stale turns the gate into a
# rubber stamp.
PLAY_MIN_TARGET_SDK=35

# Play stopped accepting Billing Library 6 with the same 2025 deadline. 7 is the
# floor; the app may sit above it.
PLAY_MIN_BILLING_MAJOR=7

fail=0

# Reported, but does not fail the run.
warn() { printf '  WARN  %s\n' "$1"; }

err() { # <file> <line> <title> <message>
  printf '::error file=%s,line=%s,title=%s::%s\n' "$1" "$2" "$3" "$4"
  fail=1
}

gate_broken() { # <message> — the gate itself cannot run; never pass silently
  printf '::error title=Release-config gate::%s\n' "$1"
  exit 2
}

ok()  { printf '  OK    %s\n' "$1"; }
bad() { printf '  FAIL  %s\n' "$1"; }

# line_of <file> <fixed-string> — first matching line number, for the annotation
line_of() { grep -nF -m1 "$2" "$1" | cut -d: -f1; }

for f in "$GRADLE" "$MANIFEST"; do
  [[ -f $f ]] || gate_broken "$f not found — this gate is checking nothing."
done

# ---------------------------------------------------------------------------
# 1. Google Play Billing
# ---------------------------------------------------------------------------

printf '\nGoogle Play Billing\n'

BILLING_DEP=$(sed -n 's/.*com\.android\.billingclient:billing[a-z-]*:\([0-9][0-9.]*\).*/\1/p' "$GRADLE" | head -1)
if [[ -z $BILLING_DEP ]]; then
  gate_broken "no com.android.billingclient dependency found in $GRADLE"
fi

BILLING_LINE=$(line_of "$GRADLE" 'com.android.billingclient')
BILLING_MAJOR=${BILLING_DEP%%.*}

# Report the artifact actually declared. Either `billing` or `billing-ktx`
# satisfies Play, so printing a hardcoded "billing-ktx" names a dependency the
# build may not have.
BILLING_ARTIFACT=$(sed -n 's/.*com\.android\.billingclient:\(billing[a-z-]*\):[0-9][0-9.]*.*/\1/p' "$GRADLE" | head -1)

if [[ $BILLING_MAJOR -lt $PLAY_MIN_BILLING_MAJOR ]]; then
  bad "$BILLING_ARTIFACT $BILLING_DEP (Play requires ${PLAY_MIN_BILLING_MAJOR}.x or newer)"
  err "$GRADLE" "$BILLING_LINE" "Billing Library below Play's floor" \
    "com.android.billingclient is at $BILLING_DEP. Play refuses uploads built against Billing Library below ${PLAY_MIN_BILLING_MAJOR}.0.0, so this bundle is rejected at upload no matter how green the build is. Note the v${PLAY_MIN_BILLING_MAJOR} migration is not a version bump alone — queryPurchasesAsync and the launch flow changed shape."
else
  ok "$BILLING_ARTIFACT $BILLING_DEP (>= ${PLAY_MIN_BILLING_MAJOR}.0.0)"
fi

# The manifest meta-data is what Play reads to decide which Billing version the
# app declares. Gradle never cross-checks it against the dependency, so the two
# drift apart silently and Play acts on the wrong one.
MANIFEST_BILLING=$(tr '\n' ' ' < "$MANIFEST" \
  | sed -n 's/.*com\.google\.android\.play\.billingclient\.version"[^>]*android:value="\([^"]*\)".*/\1/p')
if [[ -z $MANIFEST_BILLING ]]; then
  bad "no com.google.android.play.billingclient.version meta-data in the manifest"
  err "$MANIFEST" "1" "Billing meta-data missing" \
    "The manifest declares no com.google.android.play.billingclient.version meta-data, so Play cannot tell which Billing Library this app uses."
elif [[ $MANIFEST_BILLING != "$BILLING_DEP" ]]; then
  bad "manifest declares $MANIFEST_BILLING, dependency is $BILLING_DEP"
  err "$MANIFEST" "$(line_of "$MANIFEST" 'com.google.android.play.billingclient.version')" \
    "Billing meta-data does not match the dependency" \
    "The manifest declares Billing $MANIFEST_BILLING but the app is built against $BILLING_DEP. Play believes the manifest; nothing in the build reconciles the two."
else
  ok "manifest meta-data $MANIFEST_BILLING matches the dependency"
fi

# ---------------------------------------------------------------------------
# 2. targetSdk
# ---------------------------------------------------------------------------

printf '\ntargetSdk\n'

TARGET_SDK=$(sed -n 's/.*targetSdk[[:space:]]*\([0-9]\{2\}\).*/\1/p' "$GRADLE" | head -1)
[[ -n $TARGET_SDK ]] || gate_broken "no targetSdk found in $GRADLE"

if [[ $TARGET_SDK -lt $PLAY_MIN_TARGET_SDK ]]; then
  bad "targetSdk $TARGET_SDK (Play requires $PLAY_MIN_TARGET_SDK)"
  err "$GRADLE" "$(line_of "$GRADLE" 'targetSdk')" "targetSdk below Play's floor" \
    "targetSdk is $TARGET_SDK; Play requires at least $PLAY_MIN_TARGET_SDK for new submissions and updates. The bundle is rejected at upload. Raising it also opts the app into that release's behaviour changes, so raise compileSdk with it and re-test."
else
  ok "targetSdk $TARGET_SDK (>= $PLAY_MIN_TARGET_SDK)"
fi

# ---------------------------------------------------------------------------
# 3. Placeholder credentials
# ---------------------------------------------------------------------------

printf '\nCredentials in source\n'

# A placeholder key compiles, ships, and then returns 401 to a user — the
# feature just renders blank with no error anywhere in the build.
PLACEHOLDER_RE='(appid|api_?key|apikey|access_?token|client_?secret)[[:space:]]*[=:][[:space:]]*"?(demo|test|placeholder|xxx|changeme|change_me|your[_-])|YOUR[_-]?(API[_-]?)?KEY|<your[_-]?api[_-]?key>|(sk|pk|rk)_test_[A-Za-z0-9]|(CHANGE_?ME|REPLACE_?ME|INSERT_?KEY_?HERE)'

# Only shipped app sources. Backend .env.example is full of placeholders on
# purpose and is not compiled into anything. Roots are resolved rather than
# passed straight to find, so a moved directory is a loud failure instead of a
# scan that quietly covers less than it claims to.
SCAN_ROOTS=""
for root in android/app/src/main android/wear/src/main ios; do
  [[ -d $root ]] && SCAN_ROOTS="$SCAN_ROOTS $root"
done
case " $SCAN_ROOTS " in
  *" android/app/src/main "*) ;;
  *) gate_broken "android/app/src/main not found — nothing would be scanned for credentials." ;;
esac

# A comment is not a credential. RadarMapScreen's KDoc explains that the
# OpenWeatherMap layers were deleted *because* they were requested with
# appid=demo; matching that sentence reports the commit that removed the key as
# though it were the key. Blank out comment text before matching, padding rather
# than deleting so the reported line numbers still point at the real line.
# Handles //, /* */ and <!-- -->; the // rule skips "https://" the same way the
# reachability gate does.
strip_comments_keeping_lines() { # <file>
  awk '
    {
      line = $0; out = ""; i = 1; n = length(line)
      while (i <= n) {
        c = substr(line, i, 1); d = substr(line, i, 2)
        if (inblock) {
          if (d == "*/") { inblock = 0; out = out "  "; i += 2 } else { out = out " "; i++ }
        } else if (inxml) {
          if (substr(line, i, 3) == "-->") { inxml = 0; out = out "   "; i += 3 } else { out = out " "; i++ }
        } else if (d == "/*") { inblock = 1; out = out "  "; i += 2 }
        else if (substr(line, i, 4) == "<!--") { inxml = 1; out = out "    "; i += 4 }
        else if (d == "//" && substr(line, i - 1, 1) != ":") { break }
        else { out = out c; i++ }
      }
      print out
    }
  ' "$1"
}

placeholders=0
while IFS= read -r hit; do
  f=${hit%%:*}
  rest=${hit#*:}
  ln=${rest%%:*}
  placeholders=$((placeholders + 1))
  err "$f" "$ln" "Placeholder credential" \
    "This line carries a placeholder credential, not a real one. It compiles and ships; the service answers 401 and the feature renders empty with nothing logged. Move the real key to a build config field or gradle.properties and fail the build when it is absent."
done < <(
  find $SCAN_ROOTS -type f \
       \( -name '*.kt' -o -name '*.swift' -o -name '*.java' -o -name '*.gradle' -o -name '*.xml' \) \
       -not -path '*/build/*' -print0 \
    | while IFS= read -r -d '' f; do
        strip_comments_keeping_lines "$f" | grep -nEi "$PLACEHOLDER_RE" | sed "s|^|$f:|"
      done
)

if [[ $placeholders -eq 0 ]]; then
  ok "no placeholder keys in the shipped sources"
else
  bad "$placeholders placeholder credential(s) — see annotations"
fi

# ---------------------------------------------------------------------------
# 4. Release API host
# ---------------------------------------------------------------------------

printf '\nRelease API host\n'

# The URL is nested inside two layers of quoting ('"https://..."'), so match the
# scheme directly rather than trying to count quote characters.
RELEASE_URL=$(sed -n '/release[[:space:]]*{/,/^[[:space:]]*}/p' "$GRADLE" \
  | sed -n "s/.*API_BASE_URL.*\(https\{0,1\}:\/\/[^\"']*\).*/\1/p" | head -1)
[[ -n $RELEASE_URL ]] || gate_broken "no release API_BASE_URL buildConfigField found in $GRADLE"

# `https*` rather than `https\{0,1\}`: BSD sed splits the s command on the comma
# inside the brace expression and dies with "braces not balanced".
RELEASE_HOST=$(printf '%s' "$RELEASE_URL" | sed -e 's|^https*://||' -e 's|[:/].*||')

resolves() { # <host>
  if command -v getent >/dev/null 2>&1 && getent hosts "$1" >/dev/null 2>&1; then
    return 0
  fi
  command -v python3 >/dev/null 2>&1 || return 2
  python3 - "$1" <<'PY'
import socket, sys
try:
    socket.getaddrinfo(sys.argv[1], None)
except OSError:
    sys.exit(1)
PY
}

resolves "$RELEASE_HOST"
case $? in
  0) ok "$RELEASE_HOST resolves ($RELEASE_URL)" ;;
  2) gate_broken "neither getent nor python3 is available to resolve $RELEASE_HOST" ;;
  *)
    # Deliberately a warning by default and an error only when a release
    # artifact is actually being produced (ENFORCE_RELEASE_HOST=1).
    #
    # The backend genuinely is not deployed yet, and that will stay true for a
    # while. Failing every push on it would put this gate — and therefore the
    # Android build that depends on it — permanently red, which is precisely
    # the state that taught everyone to ignore a red X. A finding nobody can
    # act on today must not outrank the findings they can.
    MSG="The release build points at $RELEASE_URL and $RELEASE_HOST has no DNS record. Debug builds talk to 10.0.2.2 so this never shows up in testing; in the release APK every request fails with UnknownHostException. (A DNS lookup can also fail because the runner has no network — check that before assuming the host is wrong.)"
    if [ "${ENFORCE_RELEASE_HOST:-0}" = "1" ]; then
      bad "$RELEASE_HOST does not resolve"
      err "$GRADLE" "$(line_of "$GRADLE" "$RELEASE_URL")" "Release API host does not resolve" "$MSG"
    else
      warn "$RELEASE_HOST does not resolve — not blocking, no release artifact here"
      printf '::warning file=%s,line=%s,title=%s::%s\n' \
        "$GRADLE" "$(line_of "$GRADLE" "$RELEASE_URL")" "Release API host does not resolve" "$MSG"
    fi
    ;;
esac

# ---------------------------------------------------------------------------
# 5. Runtime permissions: checked but never requested
# ---------------------------------------------------------------------------

printf '\nRuntime permissions\n'

# Android's dangerous protection level — the ones that need a runtime grant.
DANGEROUS='READ_CALENDAR WRITE_CALENDAR CAMERA READ_CONTACTS WRITE_CONTACTS
GET_ACCOUNTS ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION
RECORD_AUDIO READ_PHONE_STATE READ_PHONE_NUMBERS READ_BASIC_PHONE_STATE CALL_PHONE
ANSWER_PHONE_CALLS READ_CALL_LOG WRITE_CALL_LOG ADD_VOICEMAIL USE_SIP
BODY_SENSORS BODY_SENSORS_BACKGROUND ACTIVITY_RECOGNITION
SEND_SMS RECEIVE_SMS READ_SMS RECEIVE_WAP_PUSH RECEIVE_MMS
READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE READ_MEDIA_IMAGES READ_MEDIA_VIDEO
READ_MEDIA_AUDIO READ_MEDIA_VISUAL_USER_SELECTED POST_NOTIFICATIONS
NEARBY_WIFI_DEVICES BLUETOOTH_SCAN BLUETOOTH_CONNECT BLUETOOTH_ADVERTISE UWB_RANGING'

# Both a permission check and a permission request are routinely written over
# several lines:
#
#     ContextCompat.checkSelfPermission(
#         this,
#         Manifest.permission.ACCESS_FINE_LOCATION
#     )
#     launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, ...))
#
# so a line-oriented grep finds the check and misses the request, and reports
# every correctly-wired permission as broken. Flatten first.
PERM_FLAT=$(mktemp)
trap 'rm -f "$PERM_FLAT"' EXIT
find android/app/src/main -name '*.kt' -not -path '*/build/*' -exec cat {} + \
  | sed -e 's,^[[:space:]]*//.*,,' -e 's,\([^:]\)//.*,\1,' \
  | tr '\n' ' ' | tr -s '[:space:]' ' ' > "$PERM_FLAT"

[[ -s $PERM_FLAT ]] || gate_broken "no Kotlin sources found under android/app/src/main"

while IFS= read -r perm; do
  [[ -n $perm ]] || continue
  case " $(printf '%s' "$DANGEROUS" | tr '\n' ' ') " in
    *" $perm "*) ;;
    *) continue ;;             # normal permission: granted at install, no gate
  esac

  # `(\([^)]*\)|[^)])*` walks the argument list tolerating one level of nested
  # parentheses, which is what arrayOf( ... ) needs.
  checked=$(grep -cE "checkSelfPermission\((\([^)]*\)|[^)])*Manifest\.permission\.$perm" "$PERM_FLAT")
  launched=$(grep -cE "\.launch\((\([^)]*\)|[^)])*Manifest\.permission\.$perm" "$PERM_FLAT")

  if [[ $checked -gt 0 && $launched -eq 0 ]]; then
    bad "$perm checked but never requested"
    err "$MANIFEST" "$(line_of "$MANIFEST" "android.permission.$perm")" \
      "Permission is checked but never requested" \
      "$perm is declared in the manifest and guarded with checkSelfPermission, but no permission launcher ever asks for it. Nothing grants it, so the guard is false forever and the code behind it never runs — no crash, no log, the feature simply does nothing. Register a launcher and launch($perm) before the guard."
  elif [[ $checked -gt 0 ]]; then
    ok "$perm checked and requested"
  else
    ok "$perm declared, not runtime-guarded"
  fi
done < <(sed -n 's/.*uses-permission[^>]*android:name="android\.permission\.\([A-Z_]*\)".*/\1/p' "$MANIFEST")

if [[ $fail -eq 0 ]]; then
  printf '\nRelease config is shippable.\n\n'
else
  printf '\nRelease config would be rejected or would fail in the field — see the annotations above.\n\n'
fi
exit $fail
