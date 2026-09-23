# Deployment status and release procedure

No weather backend or mobile store build is live. GitHub Pages currently serves
the older legal landing page; a live weather website has been prepared in
`docs/` on the local audit branch but is not published yet. Read the
[free-launch audit](CODEBASE_AUDIT.md) before choosing a release path.

## No-store-fee Android APK

The Android client can obtain basic forecasts directly from public weather
providers. A no-backend APK must hide or disable consensus, accounts and other
backend-only features and must not use an `.invalid` URL behind visible UI.
Then create a private release keystore using [SIGNING.md](../android/SIGNING.md),
build the release variant, install that exact artifact on devices, test data
freshness/error states, and distribute it with an accurate description and
privacy/attribution links. `assembleDebug` and CI's emulator smoke test are not
release validation. Sharing an APK outside a store does not provide the store's
update or review process.

## Public static web app

The first release target is the `docs/` website. It calls Open-Meteo directly
from the browser for a real forecast and place search, with no ClimaAI backend,
account, paid service or payment method. The old simulated `web-demo/` was
removed. See [WEBSITE.md](WEBSITE.md) for local checks, provider terms and the
publication procedure. Privacy, terms and account-deletion URLs remain in
place.

## Optional FastAPI backend

The backend is required for multi-source consensus and account features. Its
weather endpoints can start without Postgres; account/favorites routes then
return 503. Redis is optional. Configure a production deployment with
`backend/api/.env.production.example` as a **template**, replacing the JWT
secret with a unique value, setting exact CORS origins only if a browser client
will call the API, and providing a Postgres URL if accounts are enabled. Do not
commit a private env file. Pollen endpoints return 503 without a configured
provider key; they never generate substitute readings.

`.github/workflows/deploy.yml` can deploy to Google Cloud Run when the required
cloud credentials and Secret Manager values are configured. The workflow has
not deployed this project. Cloud Run's free usage tier is conditional on a
billing account and quotas; database, network and over-quota use may cost
money. If a payment method is unacceptable, do not make the backend part of
the first release. After deploying, verify `/health`, public weather,
consensus, authentication and deletion from the release client before pointing
`climaaiApiBaseUrl` or iOS `API_BASE_URL` at it.

Google also offers a separate AI Studio Starter Tier that can provision
limited Cloud Run resources without billing information. That is not the
deployment path implemented by this repository's GitHub Actions workflow; its
fit for this FastAPI service has not been established.

## Stores

Google Play requires developer registration and review; see
[the store copy](APP_STORE.md) only after validating every claim in the chosen
build. iOS App Store distribution requires Apple Developer membership, icons,
signing and App Review. The iOS app currently requires login and shows paid UI,
so it needs product changes before submission. Store enrollment and policy
requirements can change; verify them at submission time.
