# Android release signing

> ⚠️ **`gradle/wrapper/gradle-wrapper.jar` is missing from this repository**, so
> `./gradlew` fails before any of the below can run. It is not gitignored — it
> was simply never committed. Regenerate it once with a local Gradle install:
>
> ```bash
> cd android && gradle wrapper --gradle-version 8.13
> ```
>
> Then commit the jar. Every instruction on this page assumes a working wrapper.

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

## When signing is not configured

`assembleRelease` and `bundleRelease` still run, but fall back to the debug
signing key so local release builds are not blocked. **Play will reject that
artifact.** Check which key was used before uploading:

```bash
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
```

A debug-signed artifact shows `CN=Android Debug`.
