# Licensing: the commercial flip, end to end

Last reviewed 2026-08-05.

Open-Meteo — the primary data source on every platform — licenses its free
tier for **non-commercial use only**. The app ships free
(`MONETIZATION_ENABLED=false` in both Android build types), which is the
compliant footing. The moment money enters, a commercial licence must already
be in place. The switch is wired so that the entire procedure is:
**buy licence → paste key → flip flag.**

## 1. Buy

An [Open-Meteo API subscription](https://open-meteo.com/en/pricing): Standard
is 1M calls/month, Professional 5M, Enterprise above 50M. Exact pricing only
appears at Stripe checkout, so budget it before committing. One key covers
every host the code uses — forecast, air-quality, archive and geocoding. The
paid API lives on the `customer-` twin of each free host
(`api.open-meteo.com` → `customer-api.open-meteo.com`) and authenticates with
an `apikey` query parameter; the code derives both from the key's presence, so
no URL is ever edited by hand.

## 2. Paste the key — backend

The setting is `OPEN_METEO_API_KEY` (`backend/api/app/config.py`). Empty means
free tier; set, it routes every backend Open-Meteo call through
`Settings.open_meteo_request()`, which swaps in the `customer-` host and
attaches the key. `backend/api/tests/test_commercial_key.py` pins the URL
construction both ways and proves each call site routes through the switch.

- **Local / dev:** put it in `backend/api/.env`.
- **Production (Cloud Run):** the key is a Secret Manager secret, never a
  committed value. After `gcloud auth login`:

  ```sh
  printf '%s' 'THE_KEY' | gcloud secrets create OPEN_METEO_API_KEY --data-file=-
  ```

  then add one line to the `secrets:` block of the deploy step in
  `.github/workflows/deploy.yml`, alongside `OPENAI_API_KEY`:

  ```yaml
  OPEN_METEO_API_KEY=OPEN_METEO_API_KEY:latest
  ```

  (The mapping cannot be pre-wired: Cloud Run refuses to deploy while the
  referenced secret does not exist.)

## 3. Paste the key — Android

`gradle.properties` already carries the empty property:

```properties
openMeteoApiKey=THE_KEY
```

It surfaces as `BuildConfig.OPEN_METEO_API_KEY`, and the Open-Meteo endpoints
in `FreeApis.kt` (see `OpenMeteoLicence`) switch host and attach the key from
it. `OpenMeteoRepository` and every caller above it need no change.

## 4. Flip the flag — and the gate that enforces the order

`MONETIZATION_ENABLED` lives in `android/app/build.gradle`.
`scripts/ci/check-release-config.sh` **fails the build** when
`MONETIZATION_ENABLED` is `true` while `openMeteoApiKey` is empty, and that
check has no override flag: charging users for data licensed as non-commercial
is made structurally impossible, not merely documented. Flip the flag only
after steps 1–3.

The alternative to buying — moving the primary source to one whose free tier
permits commercial use (WeatherAPI.com, 1M calls/month) — is weighed in
[WEATHER_APIS.md](WEATHER_APIS.md).

## What a paid licence does NOT remove

- **CC BY 4.0 attribution.** Open-Meteo data requires credit on the free tier
  and on every paid tier alike. The in-app attribution (the tappable
  "Open-Meteo (CC BY 4.0)" row in Settings, plus the Home screen credit) stays
  regardless of licence status.
- **Third-party attributions.** Nominatim/OSM ("© OpenStreetMap contributors")
  and RainViewer credits are separate obligations and unaffected by an
  Open-Meteo purchase. See [WEATHER_APIS.md](WEATHER_APIS.md).

## The domain is not ours

**climaai.com belongs to a third party.** whois: created 2024-06-18, registrar
joker.com, nameservers netafraz.com. It was never this project's property, so:

- nothing in the repo may treat `api.climaai.com` (or any `climaai.com` name)
  as the future production host;
- the backend's public URL is the Cloud Run `*.run.app` URL until a domain is
  actually bought — a **separate purchase and a separate decision** from the
  data licence, possibly under a different brand;
- the release build's unconfigured sentinel is `https://unconfigured.invalid/`
  (RFC 2606 TLD): parseable everywhere, fails DNS instantly, impossible to
  mistake for a real host.

## Known gaps (as of 2026-08-05)

Two Open-Meteo call sites are not yet behind the switch:

- `backend/api/app/routers/locations.py` — city search hits the free
  `geocoding-api.open-meteo.com` host directly; one line to route it through
  `settings.open_meteo_request()`.
- `android/wear/.../WearWeatherApi.kt` — the watch's standalone fetch is
  pinned to the free host, and the `wear` module does not yet receive
  `openMeteoApiKey` as a BuildConfig field.

Both must be closed before, or together with, the flag flip.
