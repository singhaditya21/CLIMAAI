# Build artifacts

Built apps land here, split by platform and then by build variant.

```
dist/
├── Android/
│   ├── debug/
│   │   └── 1.0.0-4b01248/
│   │       ├── app-debug.apk
│   │       └── wear-debug.apk
│   └── release/
│       └── 1.0.0-4b01248/
│           ├── app-release.aab      ← the Play Store upload
│           ├── app-release.apk      ← sideloadable equivalent
│           └── mapping.txt          ← keep this, see below
└── IPA/
    ├── debug/
    └── release/
        └── 1.0.0-<sha>/ClimaAI.ipa
```

**Variant first, then `<version>-<sha>`.** Variant first so "the latest release
build" is one directory listing. The commit sha second so any artifact can be
traced back to the code that produced it, and so a new build never silently
overwrites the one you shipped. Artifacts built from a modified working tree are
stamped `-dirty`, because otherwise the sha would be a lie.

## Producing artifacts

Build first, then collect — the script only copies:

```bash
cd android && ./gradlew assembleDebug assembleRelease bundleRelease
./scripts/collect-artifacts.sh android
```

Or everything that happens to exist:

```bash
./scripts/collect-artifacts.sh
```

CI uploads the same layout as a workflow artifact on every push to `main`.

## `mapping.txt` matters

Release builds are minified by R8, so crash reports come back obfuscated and
unreadable. `mapping.txt` is the only thing that translates them, and it is
regenerated on every build — **the one that shipped with a given AAB is the only
one that works for it.** That is why it is stored alongside the artifact rather
than left in `build/`.

## Not in version control

`dist/` contents are gitignored; only this file is tracked. Binaries do not
belong in git — they bloat history permanently and cannot be diffed. Use the CI
artifacts, or a release, for anything that needs sharing.

## iOS

`IPA/` is wired up but empty: the iOS build is on hold. See
[ios/XCODE_SETUP.md](../ios/XCODE_SETUP.md) for its current state.
