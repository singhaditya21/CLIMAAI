#!/bin/zsh
# Collect build outputs into the repo-level dist/ layout.
#
#   dist/<Platform>/<variant>/<version>-<sha>/<artifact>
#
# e.g. dist/Android/release/1.0.0-4b01248/app-release.aab
#
# Variant first so "give me the latest release build" is one directory listing;
# version-sha second so every artifact is traceable to the commit that produced
# it and older builds are not silently overwritten.
#
# Usage:
#   ./scripts/collect-artifacts.sh            # everything that exists
#   ./scripts/collect-artifacts.sh android
#   ./scripts/collect-artifacts.sh ios
#
# Copies only — it never builds. Run your build first, then this.

set -u
cd "$(dirname "$0")/.." || exit 1
ROOT=$PWD
TARGET=${1:-all}

# Version comes from the Android build file; both platforms share it so a given
# dist/ folder name means the same release on either side.
VERSION=$(grep -m1 'versionName' android/app/build.gradle | sed 's/.*"\(.*\)".*/\1/')
SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "nogit")
DIRTY=""
if ! git diff --quiet 2>/dev/null || ! git diff --cached --quiet 2>/dev/null; then
  # Mark artifacts built from a modified tree — otherwise the sha is a lie.
  DIRTY="-dirty"
fi
STAMP="${VERSION}-${SHA}${DIRTY}"

copied=0
skipped=0

# copy_artifact <platform> <variant> <source-path>
copy_artifact() {
  local platform=$1 variant=$2 src=$3
  if [[ ! -f "$src" ]]; then
    skipped=$((skipped + 1))
    return
  fi
  local dest="$ROOT/dist/$platform/$variant/$STAMP"
  mkdir -p "$dest"
  cp "$src" "$dest/"
  local size
  size=$(du -h "$src" | awk '{print $1}')
  printf "  %-10s %-8s %-28s %s\n" "$platform" "$variant" "$(basename "$src")" "$size"
  copied=$((copied + 1))
}

collect_android() {
  print "\nAndroid"
  copy_artifact Android debug   "android/app/build/outputs/apk/debug/app-debug.apk"
  copy_artifact Android debug   "android/wear/build/outputs/apk/debug/wear-debug.apk"
  copy_artifact Android release "android/app/build/outputs/apk/release/app-release.apk"
  copy_artifact Android release "android/app/build/outputs/bundle/release/app-release.aab"
  copy_artifact Android release "android/wear/build/outputs/apk/release/wear-release.apk"
  # Mapping files are needed to deobfuscate release crash reports; without the
  # one matching a given AAB, its stack traces are unreadable.
  copy_artifact Android release "android/app/build/outputs/mapping/release/mapping.txt"
}

collect_ios() {
  print "\nIPA"
  # Xcode archive/export locations. Nothing here yet — the iOS build is on hold
  # (see ios/XCODE_SETUP.md), so these are expected to be skipped for now.
  copy_artifact IPA release "ios/build/export/ClimaAI.ipa"
  copy_artifact IPA debug   "ios/build/export-debug/ClimaAI.ipa"
}

print "Collecting into dist/  (stamp: $STAMP)"
case "$TARGET" in
  android) collect_android ;;
  ios|ipa) collect_ios ;;
  all)     collect_android; collect_ios ;;
  *)       print "unknown target: $TARGET (expected android, ios or all)"; exit 2 ;;
esac

print "\n$copied copied, $skipped not present"
if (( copied > 0 )); then
  print "\ndist/ now contains:"
  find "$ROOT/dist" -mindepth 3 -maxdepth 3 -type d 2>/dev/null | sed "s|$ROOT/|  |"
fi
[[ -n "$DIRTY" ]] && print "\nNote: working tree is modified, so artifacts are stamped -dirty."
exit 0
