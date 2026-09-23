# ClimaAI

ClimaAI's first release target is a **free weather website on GitHub Pages**.
The reviewed website code is in `docs/` on the local `audit/free-launch-cleanup`
branch; the public [Pages URL](https://singhaditya21.github.io/CLIMAAI/) still
serves the older legal landing page until this branch is published. Android,
iOS and the backend remain unreleased.

The planned differentiator is a multi-provider consensus readout showing the
median, range and confidence of independent forecasts. That feature needs the
FastAPI backend; it is unavailable in a release build until a backend URL is
configured. Android can fetch basic forecasts directly from Open-Meteo without
the backend. The new browser site deliberately offers a smaller feature set:
live Open-Meteo current, hourly and daily forecasts, place search and optional
browser geolocation. It makes no multi-provider consensus or AI claim.

## What is where

| Directory | Purpose | Free-launch state |
| --- | --- | --- |
| `docs/` | Browser weather site and legal/support pages | First-release candidate; not yet published |
| `android/app/` | Kotlin phone app, widgets and direct weather providers | Core client; release URL and device testing remain |
| `android/wear/` | Wear OS app, tile and complications | Optional companion |
| `ios/ClimaAI/` | SwiftUI phone app | Backend-dependent; login and paid UI still need free-release work |
| `ios/ClimaAIWatch/`, `ios/ClimaAIWidget/` | Apple companions | Optional; unsigned store builds unverified |
| `backend/api/` | FastAPI weather, consensus and account API | Local/CI only; not deployed |
| `backend/payment-service/` | Store payment webhooks | Deferred; no free-launch purpose |

Every other versioned file is classified in [the codebase catalog](docs/CODEBASE_CATALOG.csv).
The [architecture](docs/ARCHITECTURE.md), [audit](docs/CODEBASE_AUDIT.md), and
[roadmap](ROADMAP.md) describe current behavior and remaining work.

The website runs without a ClimaAI server or API key. To preview it, run
`python3 -m http.server 8080 --directory docs` and open
`http://localhost:8080/`. See [the website release guide](docs/WEBSITE.md).

## Local development

Android: `cd android && ./gradlew assembleDebug`. The debug app expects a local
backend on the Android emulator's `10.0.2.2:8000` for consensus and account
features. Its direct forecast features do not require that backend. To build or
sign a release artifact, read [android/SIGNING.md](android/SIGNING.md) and set a
real `climaaiApiBaseUrl` in `android/gradle.properties` if shipping backend
features. Do not distribute the debug-signed release fallback as a store build.

Backend: see [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for the existing deployment
workflow and [backend/api/.env.example](backend/api/.env.example) for settings.
Postgres is required for accounts and saved locations; Redis is optional. AI
insights default to off, and there is no need for an OpenAI key for the free
release. Production startup rejects a placeholder JWT secret and wildcard
CORS. The API has not yet been deployed. Pollen data is unavailable without a
configured provider key.

iOS: open `ios/ClimaAI.xcodeproj` with full Xcode. The current Mac environment
has only Command Line Tools, so a local iOS build cannot be verified here.
GitHub Actions has completed simulator build/tests, but the app still requires
login and presents premium UI. It is not ready for submission.

The Android and iOS API paths can be checked without running the backend:
`python3 scripts/ci/check-ios-routes.py` checks iOS path existence, while
`scripts/ci/check-api-contract.py` checks Android against a running backend's
OpenAPI schema.

## Release position

The selected no-payment-method release is the static website. It needs final
browser review and publication to the existing Pages source branch. Store
distribution and a hosted backend have separate requirements and possible
charges; see [the audit](docs/CODEBASE_AUDIT.md).

No weather app or API deployment is live yet. The website should not advertise
AI insights, premium purchases, or cross-provider consensus.

Support: singhaditya21@gmail.com. [Privacy](https://singhaditya21.github.io/CLIMAAI/privacy.html),
[terms](https://singhaditya21.github.io/CLIMAAI/terms.html), and
[account deletion](https://singhaditya21.github.io/CLIMAAI/delete-account.html).
