# Android release signing

> **Toolchain note.** Gradle 8.13's bundled Groovy cannot parse class files from
> JDK 24+, so a modern JDK fails with `Unsupported class file major version`.
> Build with **JDK 17 or 21**; Temurin 21 is what this was verified against.
>
> ```bash
> export JAVA_HOME=/path/to/jdk-21
> ```

Google Play requires every upload to be signed with a key you control and can
never rotate on your own. Losing it means you cannot ship updates to existing
installs, so treat the keystore as unrecoverable-if-lost.

## One-time: create the keystore

```bash
keytool -genkeypair -v \
  -keystore android/climaai-release.jks \
  -alias climaai \
  -keyalg RSA -keysize 2048 -validity 10000
```

`android/*.jks` is gitignored. Back the file up somewhere durable and private —
a password manager or an encrypted vault, not this repository.

## Local builds

Compiling also needs the **Android SDK** (platform 34 and matching build-tools).
Install it through Android Studio, or via `sdkmanager` — either way you have to
accept Google's SDK licence terms yourself. Then point the build at it with
`android/local.properties`:

```
sdk.dir=/Users/you/Library/Android/sdk
```

```bash
cp android/keystore.properties.example android/keystore.properties
```

Fill in the passwords and alias, then:

```bash
cd android && ./gradlew bundleRelease
```

The AAB lands at `android/app/build/outputs/bundle/release/app-release.aab`.

## CI builds

Do not commit the keystore. Provide it as a base64 secret and decode it during
the job, then set the four environment variables the build reads:

- `ANDROID_KEYSTORE_FILE` — path relative to `android/`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

```yaml
- name: Decode keystore
  run: echo "${{ secrets.ANDROID_KEYSTORE_BASE64 }}" | base64 -d > android/release.jks
  env:
    ANDROID_KEYSTORE_FILE: release.jks
```

## Verifying which branch is active

The build picks one of three states. All three are verified working:

| State | `signingConfigs` | release uses |
| :--- | :--- | :--- |
| Neither properties file nor env vars | `[debug]` | `debug` (not uploadable) |
| `keystore.properties` present | `[debug, release]` | `release` |
| `ANDROID_KEYSTORE_*` env vars set | `[debug, release]` | `release` |

## When signing is not configured

`assembleRelease` and `bundleRelease` still run, but fall back to the debug
signing key so local release builds are not blocked. **Play will reject that
artifact.** Check which key was used before uploading:

```bash
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
```

A debug-signed artifact shows `CN=Android Debug`.
