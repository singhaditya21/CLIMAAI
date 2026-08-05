# ClimaAI Roadmap

Replaces the former `SUMMARY.md`, `ANALYSIS_REPORT.md`, and `AUDIT_REPORT.md`, which
had drifted out of sync with the code and contradicted each other.

## Where the project actually stands

| Component | Stack | Status |
| :--- | :--- | :--- |
| `backend/api` | Python 3.11, FastAPI, SQLAlchemy, Postgres, Redis | Feature-complete; 116-test suite in CI; **never deployed** |
| `backend/payment-service` | Node.js, Express | Apple + Google webhooks implemented; idle while monetization is off |
| `android/` | Kotlin, Jetpack Compose, Material 3 | Mobile + Wear OS; builds, runs on emulator, store blockers cleared |
| `ios/` | Swift 5.9, SwiftUI | Mobile + Watch + widgets; **never built against the iOS SDK** |
| `web-demo/` | Vanilla HTML/CSS/JS | Working prototype |

## What the 2026-08-05 round closed

The pre-launch audit ([docs/PRE_LAUNCH_AUDIT.md](docs/PRE_LAUNCH_AUDIT.md))
found the app unshippable. This round closed the audit's Android-side blockers,
verified on an emulator:

- **Play upload floor** — Billing 8.0.0 (dependency + manifest meta-data),
  targetSdk 36. The AAB is uploadable.
- **Reachability** — every registered screen now has an inbound navigation
  edge (Login, Radar, Air Quality, Pollen, Location Switcher, Appearance,
  Paywall), and the lifecycle `initialize()` calls are wired.
  `scripts/ci/check-reachability.sh` gates regressions.
- **No more invented data** — the audit's worst finding. Widgets and the Wear
  tile/complications now render the phone's last real synced reading
  (data-layer sync via `PhoneWeatherListenerService`) and show an explicit
  no-data state before first sync. The pollen screen renders real readings
  (CAMS, Europe-only domain) or an honest "no data for this location". Radar
  renders real timestamped RainViewer frames.
- **Notifications** — channels are created and `POST_NOTIFICATIONS` is
  actually requested, so opt-in daily summary and rain alerts can fire.
- **The consensus feature** — the differentiator the audit said was computed
  and discarded. The multi-source response now carries per-variable
  median/min/max/spread and a high/medium/low confidence rating, surfaced on
  Home; hidden when fewer than two sources respond.
- **Monetization compiled out** — `MONETIZATION_ENABLED=false` in both build
  types. The shipped app is free with no ads and no paywall, which also ends
  the store listing's dependence on features (AI insights, premium tiers)
  that are off in the shipped config.
- **Licensing posture** — with no charge and no ads, Open-Meteo's
  non-commercial free tier applies to us the same way it does to Breezy
  Weather. CC-BY attribution is shown in-app (Settings), and Nominatim search
  credits © OpenStreetMap contributors. See
  [docs/WEATHER_APIS.md](docs/WEATHER_APIS.md). The commercial switch is now
  wired on both sides: pasting a bought key into `OPEN_METEO_API_KEY`
  (backend) / `openMeteoApiKey` (gradle.properties) moves every Open-Meteo
  call to the licensed `customer-` host — procedure and gaps in
  [docs/LICENSING.md](docs/LICENSING.md).
- **Honest store metadata** — [docs/APP_STORE.md](docs/APP_STORE.md) rewritten
  to describe the app that exists; the Play-required
  privacy / terms / account-deletion pages exist as static HTML in `docs/`
  for GitHub Pages.
- **Static CI gates** — `check-reachability.sh`, `check-release-config.sh`,
  `check-api-contract.py` run before anything compiles.

## What genuinely remains before a store release

These are hard blockers, not nice-to-haves:

1. **Backend deployment and DNS.** `api.climaai.com` does not resolve, and no
   deployment has ever run (`GCP_SA_KEY` / `GCP_PROJECT_ID` repository secrets
   were never configured). Until a real host exists and the placeholder URLs in
   `ios/ClimaAI/Services/APIClient.swift` and the Android release build type
   are replaced (PRs #14, #15 are parked ready), every backend feature —
   multi-source consensus, accounts, favourites sync — is dead in a release
   build. The client-direct features (Open-Meteo forecasts, radar, air
   quality, pollen, search) work regardless.
2. **GitHub Pages must be enabled** (repo Settings → Pages → `main` branch,
   `/docs` folder) so the privacy / terms / delete-account URLs in the listing
   actually resolve. Play rejects listings with dead policy links.
3. **A Play Console account.** Registration, identity verification, the Data
   safety form (the answers are in
   [privacy.html](https://singhaditya21.github.io/CLIMAAI/privacy.html)),
   content rating questionnaire, and real screenshots captured from the
   release build.
4. **Conditions for flipping `MONETIZATION_ENABLED` back on.** The flag stays
   `false` until **all** of the following hold, in order:
   - the data-licensing conflict is resolved — either a paid Open-Meteo
     commercial plan (the key switch is wired; the release gate fails
     `MONETIZATION_ENABLED=true` with an empty `openMeteoApiKey`, and the
     wear module + backend geocoding call sites listed in
     [docs/LICENSING.md](docs/LICENSING.md) are closed), or the primary
     source moved to one whose free tier permits commercial use
     (WeatherAPI.com, 1M calls/month);
   - a paid tier exists that is worth money (the audit: charging for days
     8–16 of a free API is not it);
   - the purchase path actually grants entitlements end-to-end
     (`validateReceipt` wired, `/api/subscriptions/activate` called, plans
     loaded) and Apple webhook JWS verification (PR #36) is merged;
   - products are configured in Play Console / App Store Connect.
5. **iOS has still never been built against the iOS SDK.** Full Xcode,
   simulator test run, signing. See `ios/XCODE_SETUP.md`. No iOS submission
   until then.
6. **Android test depth.** One emulator-verified smoke pass is not a suite;
   JUnit coverage for repository and view-model layers and a CI emulator job
   are still missing.

## Phase 2 — Competitor parity

Carried over from the AccuWeather gap analysis; this is the genuine feature backlog.

| Gap | Competitor feature | Current state | Effort |
| :--- | :--- | :--- | :--- |
| RealFeel Shade | Feels-like split for sun vs. shade | Generic apparent temperature | Low — algorithmic, uses solar radiation + cloud cover already fetched |
| WinterCast | Snow accumulation probability ranges | Precip probability and amount only | Low — probabilistic bucketing over `snowfall_sum` |
| Extended MinuteCast | 4-hour minute-level precipitation with type | 2-hour nowcast, no type split | Medium — depends on data source |
| Hurricane tracker | Interactive storm cones and paths | Text-only NWS alerts | High — new data source + map layers on both platforms |
| Stargazing | Visibility index, satellite passes | Sunrise/sunset and moon phase | Medium |
| Community reports | User-submitted hazard and sky reports | None | High — needs moderation and abuse handling |
| Niche health indices | Arthritis, dust, dander, hair frizz | Migraine, flu, pollen | Low each |

## Phase 3 — Beyond parity

- [ ] Hyperlocal forecasting
- [ ] AR weather visualization
- [ ] Voice assistant integration
- [ ] Weather-based automations
- [ ] B2B API offering

## Open pull requests

The backlog went from 174 to 11. Everything still open is annotated on the PR itself
with why it is open and what it needs.

- **Parked until release** — #14 (production API URL), #15 (disable Apple sandbox).
  Both correct, both make development worse if merged early.
- **Needs rebase and review** — #36 (Apple JWS verification, a real security gap in
  the webhook — a flag-flip precondition, see above), #39 (email verification),
  #41 (Google OAuth).
- **Monetization-blocked** — #23 wires in Google's *test* AdMob ID; irrelevant
  while `MONETIZATION_ENABLED=false`, wrong if merged before it flips.

The `Palette` bot that produced 144 duplicate PRs stopped on its own after
2026-07-08; no action needed unless it restarts.
