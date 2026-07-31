# ClimaAI Roadmap

Replaces the former `SUMMARY.md`, `ANALYSIS_REPORT.md`, and `AUDIT_REPORT.md`, which
had drifted out of sync with the code and contradicted each other.

## Where the project actually stands

| Component | Stack | Status |
| :--- | :--- | :--- |
| `backend/api` | Python 3.11, FastAPI, SQLAlchemy, Postgres, Redis | Feature-complete; thin test coverage |
| `backend/payment-service` | Node.js, Express | Apple + Google webhooks implemented |
| `android/` | Kotlin, Jetpack Compose, Material 3 | Mobile app + Wear OS module; no tests |
| `ios/` | Swift 5.9, SwiftUI | Mobile app + Watch app + widgets; **no Xcode project** |
| `web-demo/` | Vanilla HTML/CSS/JS | Working prototype |

Features that older docs listed as "planned" but which are **already shipped**:
widgets, Apple Watch app, Wear OS app, radar overlay (RainViewer), multi-location
support, weather alert push notifications, and historical weather persistence
(`WeatherHistory`). The migraine risk index reads real stored pressure history —
the "fake data" finding in the old audit report was fixed and is no longer accurate.

## Blockers before a store release

These are hard blockers, not nice-to-haves:

1. **No `ios/ClimaAI.xcodeproj`.** Swift sources exist but there is no Xcode project
   or asset catalog; see `ios/XCODE_SETUP.md`. The iOS test suite cannot be run until
   this exists.
2. **`android/gradle/wrapper/gradle-wrapper.jar` is missing**, so `./gradlew` fails
   before anything else — the Android app cannot be built from a fresh clone at all.
   Regenerate with `cd android && gradle wrapper --gradle-version 8.13` and commit
   the jar. Signing itself is now wired up; see `android/SIGNING.md`.
3. **Android has no tests at all.** The backend now has a 57-test suite covering
   auth, weather parsing, locations, notifications and app wiring, running against a
   real Postgres in CI. Android remains uncovered, and the iOS suite cannot run
   until blocker 1 is resolved.
4. **CI covers the backend only.** `.github/workflows/deploy.yml` tests and deploys
   the API; nothing builds or tests either mobile app. The `build` and `deploy` jobs
   fail at Google Cloud authentication until `GCP_SA_KEY` and `GCP_PROJECT_ID` are
   configured as repository secrets — no deployment has ever run.
5. **Placeholder production URLs.** `https://api.climaai.com` is hardcoded in
   `ios/ClimaAI/Services/APIClient.swift` and the Android release build type.

## Phase 1 — Make it buildable and trustworthy

- [x] Backend test suite against real Postgres — auth, weather parsing, locations,
      notifications, app wiring (57 tests), wired into CI
- [ ] Extend backend coverage to `HealthIndexService`, `SubscriptionService`,
      `NowcastService` and the alerts/pollen routers
- [ ] Generate the Xcode project and asset catalog; get `ClimaAITests` running
- [ ] Add Android signing config and a documented keystore workflow
- [ ] Android JUnit tests for the repository and view-model layers
- [ ] CI jobs for the Android and iOS builds alongside the existing backend job
- [ ] Replace placeholder API hosts with a real domain

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

## Repository hygiene

- The `Palette` bot opens a near-duplicate "auth loading states" PR every day.
  Whatever schedules it should be disabled, or the PR backlog will keep regrowing.
- The 30 substantive PRs from 2026-02-26 predate work that later landed directly on
  `main` (notably the multi-source weather service). Rebase and re-review them before
  merging rather than trusting the diffs as-is.
