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

1. **The iOS project has never been compiled.** `ios/ClimaAI.xcodeproj` now exists,
   generated from `ios/project.yml` via XcodeGen and covering all four targets, but
   it was produced on a machine with only Command Line Tools — no `xcodebuild`. The
   first real build will likely surface compile errors, and `ClimaAITests` has still
   never run. See `ios/XCODE_SETUP.md`.
2. **`android/gradle/wrapper/gradle-wrapper.jar` is missing**, so `./gradlew` fails
   before anything else — the Android app cannot be built from a fresh clone at all.
   Regenerate with `cd android && gradle wrapper --gradle-version 8.13` and commit
   the jar. Signing itself is now wired up; see `android/SIGNING.md`.
3. **Android has no tests at all.** The backend now has a 116-test suite running
   against a real Postgres in CI. Android remains uncovered, and the iOS suite cannot
   run until blocker 1 is resolved.
4. **CI covers the backend only.** `.github/workflows/deploy.yml` tests and deploys
   the API; nothing builds or tests either mobile app. The `build` and `deploy` jobs
   fail at Google Cloud authentication until `GCP_SA_KEY` and `GCP_PROJECT_ID` are
   configured as repository secrets — no deployment has ever run.
5. **Placeholder production URLs.** `https://api.climaai.com` is hardcoded in
   `ios/ClimaAI/Services/APIClient.swift` and the Android release build type.

## Phase 1 — Make it buildable and trustworthy

- [x] Backend test suite against real Postgres, wired into CI — auth, weather
      parsing, locations, notifications, health indices, subscriptions, nowcast,
      alerts, pollen, personalization and app wiring (116 tests)
- [x] Generate the Xcode project and asset catalogs (`ios/project.yml`)
- [x] Add Android signing config and a documented keystore workflow
      (`android/SIGNING.md`)
- [ ] **Commit `android/gradle/wrapper/gradle-wrapper.jar`** — nothing Android can
      be built or tested until this exists
- [ ] Build the iOS project for the first time and fix what falls out; get
      `ClimaAITests` running
- [ ] Android JUnit tests for the repository and view-model layers
- [ ] CI jobs for the Android and iOS builds alongside the existing backend job
- [ ] Replace placeholder API hosts with a real domain (PRs #14, #15 are ready and
      deliberately parked until there is a deployed API to point at)

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

- **Blocked on tooling** — #17, #23, #27, #32 (Android). Nothing Android can be
  compiled until `gradle-wrapper.jar` is restored, so these cannot be verified.
  #23 also wires in Google's *test* AdMob ID, which must be swapped before release.
- **Parked until release** — #14 (production API URL), #15 (disable Apple sandbox).
  Both correct, both make development worse if merged early.
- **Needs rebase and review** — #36 (Apple JWS verification, a real security gap in
  the webhook), #39 (email verification), #41 (Google OAuth).

The `Palette` bot that produced 144 duplicate PRs stopped on its own after
2026-07-08; no action needed unless it restarts.
