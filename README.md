# ClimaAI - Multi-Source Weather Application

![ClimaAI Logo](docs/images/logo.png)

A weather application for iOS and Android that queries multiple independent
forecast providers and tells the user how much they agree — a per-variable
consensus with an honest confidence readout. Free as shipped: no ads, no
subscription, no account required. See [ROADMAP.md](ROADMAP.md) for what still
blocks a store release.

## 🌟 Features (as shipped — everything is free)

- ✅ Current conditions, hourly and daily forecasts
- ✅ **Multi-source consensus**: median, min–max range and a
  high/medium/low confidence rating computed from cross-provider disagreement;
  hidden when fewer than two sources respond
- ✅ Precipitation radar with real timestamped frames (RainViewer)
- ✅ Air Quality Index with pollutant breakdown (PM2.5, PM10, NO₂, O₃, …) and UV
- ✅ Pollen counts — **Europe only** (CAMS domain); an explicit no-data state
  elsewhere
- ✅ Home-screen widgets and a Wear OS app (tile + complications) that mirror
  the phone's last real synced reading — no-data state before first sync
- ✅ Optional notifications: daily summary, rain alerts
- ✅ Multi-location support with Nominatim search
- ✅ Location auto-detect, light/dark mode
- ✅ Optional account (syncs saved locations); deletable in-app

### Behind flags — OFF in the shipped configuration

- 💤 **AI insights** (outfit/activity/health text via OpenAI):
  `ENABLE_AI_INSIGHTS=false` and no API key in the shipped backend config. The
  code exists; the store listing must not claim it while it is off.
- 💤 **Monetization** (subscriptions, paywall):
  `MONETIZATION_ENABLED=false` is compiled into both Android build types.
  Flip conditions are listed in [ROADMAP.md](ROADMAP.md) — the Open-Meteo
  licensing constraint in [docs/WEATHER_APIS.md](docs/WEATHER_APIS.md) is the
  hard one.

## 🏗️ Architecture

```
clima-ai/
├── backend/                    # Backend services
│   ├── api/                   # FastAPI main service
│   │   ├── app/
│   │   │   ├── models/        # SQLAlchemy models
│   │   │   ├── routers/       # API endpoints
│   │   │   ├── services/      # Business logic
│   │   │   └── schemas/       # Pydantic schemas
│   │   ├── Dockerfile
│   │   └── requirements.txt
│   ├── payment-service/       # Node.js payment webhooks
│   │   ├── src/
│   │   │   ├── routes/        # Apple & Google webhooks
│   │   │   └── index.js
│   │   ├── Dockerfile
│   │   └── package.json
│   ├── docker-compose.yml     # Full stack orchestration
│   ├── init.sql               # Database schema (v1)
│   └── 002_add_features.sql   # Schema v2: locations, device tokens, alerts
├── android/                   # Android Jetpack Compose app + Wear OS module
├── ios/                       # iOS SwiftUI app + Watch app + widgets
├── web-demo/                  # Static browser demo
└── docs/                      # Documentation
```

## 🚀 Quick Start

### Prerequisites

- **Backend:**
  - Docker & Docker Compose
  - Python 3.11+
  - Node.js 18+
  - PostgreSQL 15+
  - Redis 7+

- **Mobile:**
  - iOS: Xcode 15+, Swift 5.9+
  - Android: Android Studio, Kotlin 1.9+

- **API Keys:**
  - Apple Developer Account (for iOS)
  - Google Play Developer Account (for Android)
  - Optional: OpenAI API key — only if enabling the flag-gated AI insights
  - Optional: extra weather-source keys (each source is skipped when blank)

### Backend Setup

1. **Clone the repository:**
```bash
git clone <repository-url>
cd clima-ai/backend
```

2. **Configure environment:**
```bash
cd api
cp .env.example .env
# Edit .env with your OpenAI API key and other settings
```

3. **Start all services:**
```bash
cd ..
docker-compose up -d
```

4. **Verify services:**
```bash
# API health check
curl http://localhost:8000/health

# Payment service health check
curl http://localhost:3000/health
```

5. **Access API documentation:**
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

### Backend Setup without Docker

Runs the API against a native Postgres. Redis is optional — the services catch
connection errors and skip caching, so the API works with no Redis running.

1. **Create the role and database** (Postgres 15+ already running locally):
```bash
psql -d postgres -c "CREATE ROLE climaai WITH LOGIN PASSWORD 'climaai123';" -c "CREATE DATABASE climaai OWNER climaai;"
```

2. **Apply the schema, in order.** Both files are idempotent, so re-running them
   against an existing database is safe:
```bash
cd backend && PGPASSWORD=climaai123 psql -h localhost -U climaai -d climaai -v ON_ERROR_STOP=1 -f init.sql -f 002_add_features.sql
```

3. **Create the virtualenv and install dependencies:**
```bash
cd backend/api && python3.11 -m venv .venv && .venv/bin/pip install -r requirements.txt
```

4. **Configure and run:**
```bash
cp .env.example .env
```
Set `DATABASE_URL=postgresql+asyncpg://climaai:climaai123@localhost:5432/climaai`
(the default in `.env.example` points at the `postgres` Docker host), then:
```bash
cd backend/api && .venv/bin/python -m uvicorn app.main:app --reload --port 8000
```

5. **Verify:**
```bash
curl http://localhost:8000/health
```

### iOS Setup

1. **Navigate to iOS project:**
```bash
cd ios
```

2. **Open in Xcode:**
```bash
open ClimaAI.xcodeproj
```

3. **Configure:**
- Set your signing team and change the `com.climaai.*` bundle identifier prefix
  in [ios/project.yml](ios/project.yml), then `xcodegen generate`
- Configure StoreKit products in App Store Connect
- Update product IDs in `SubscriptionManager.swift`
- Add required capabilities: Location, In-App Purchase

4. **Run on simulator or device**

### Android Setup

1. **Navigate to Android project:**
```bash
cd android
```

2. **Open in Android Studio:**
```bash
studio .
```

3. **Configure:**
- Update `applicationId` in `build.gradle`
- Configure Google Play Billing products
- Update product IDs in billing configuration
- Add required permissions in `AndroidManifest.xml`

4. **Run on emulator or device**

## 🔧 Configuration

### Backend Environment Variables

```env
# Database
DATABASE_URL=postgresql+asyncpg://climaai:password@postgres:5432/climaai

# Redis
REDIS_URL=redis://redis:6379/0

# OpenAI
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4-turbo-preview

# JWT
JWT_SECRET=your-super-secret-key-min-32-chars

# App IDs
APPLE_BUNDLE_ID=com.climaai.app
GOOGLE_PACKAGE_NAME=com.climaai.app

# Optional extra weather sources — each is skipped when its key is blank
OPENWEATHERMAP_API_KEY=
WEATHERBIT_API_KEY=
STORMGLASS_API_KEY=
OPENUV_API_KEY=

# Mounts /demo endpoints backed by generated mock data. Leave false in production.
DEMO_MODE=false
```

See [backend/api/.env.example](backend/api/.env.example) for the full annotated list.

### iOS Configuration

Update the following in Xcode:
1. **Bundle Identifier**: `com.yourcompany.climaai`
2. **Team**: Your Apple Developer Team
3. **Product IDs**:
   - Monthly: `com.yourcompany.climaai.monthly`
   - Annual: `com.yourcompany.climaai.annual`

### Android Configuration

Update in `build.gradle`:
```gradle
applicationId "com.yourcompany.climaai"
```

Update product IDs in billing configuration to match Google Play Console.

## 📡 API Endpoints

### Weather (Public)
- `GET /api/weather/current` - Current weather
- `GET /api/weather/hourly` - Hourly forecast
- `GET /api/weather/daily` - Daily forecast
- `GET /api/weather/air-quality` - Air quality data
- `GET /api/weather/multi-source` - Multi-source forecast with the consensus block

### AI Insights (flag-gated, off in shipped config; prefix is `/api`, not `/api/ai`)
- `GET /api/insights` - Complete AI insights
- `GET /api/summary` - Daily summary

### User Management (`/api/auth`)
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login
- `GET /api/auth/me` - Get current user
- `PUT /api/auth/me` - Update user
- `DELETE /api/auth/me` - Delete account and, by cascade, all attached data
  (see [docs/delete-account.html](docs/delete-account.html))

### Subscriptions (inert while monetization is compiled out)
- `GET /api/subscriptions/status` - Check subscription
- `POST /api/subscriptions/activate` - Activate subscription
- `GET /api/subscriptions/plans` - Get available plans

The authoritative list is the OpenAPI schema at `/docs` on a running API.

## 📱 Mobile Features

### iOS (SwiftUI)
- Native SwiftUI interface
- StoreKit 2 integration
- CoreLocation for geolocation
- Offline caching
- Background weather updates
- Push notifications support
- Dark mode support
- Accessibility (VoiceOver, Dynamic Type)

### Android (Jetpack Compose)
- Material 3 design
- Google Play Billing Library 5
- FusedLocationProvider
- Room database for caching
- WorkManager for background updates
- Firebase Cloud Messaging
- Dark theme support
- TalkBack accessibility

## 🧪 Testing

> ⚠️ The backend has a 116-test suite; Android has no test sources, and the iOS suite
> cannot run until an Xcode project exists. See [ROADMAP.md](ROADMAP.md).

### Backend Tests

The suite runs against a real Postgres rather than SQLite, because several
handlers use Postgres-only SQL (jsonb casts, `= ANY(:array)`) that SQLite cannot
execute — a SQLite run would pass while testing something other than production.

The test database is created and migrated automatically; it is separate from your
development database and its tables are truncated between tests.

```bash
cd backend/api && .venv/bin/python -m pytest tests/ -v
```

Point it elsewhere with `TEST_DATABASE_URL` (default
`postgresql://climaai:climaai123@localhost:5432/climaai_test`). The role needs
`CREATEDB`:

```bash
psql -d postgres -c "ALTER ROLE climaai CREATEDB;"
```

Tests needing no database (weather parsing, for instance) run without Postgres.
Database-backed tests skip locally when it is unreachable, but fail rather than
skip when `CI` is set.

### iOS Tests
`ios/ClimaAI.xcodeproj` is committed; see [ios/XCODE_SETUP.md](ios/XCODE_SETUP.md).
The suite has never been executed — expect to fix compile errors on the first run.
```bash
cd ios && xcodebuild test -scheme ClimaAI -destination 'platform=iOS Simulator,name=iPhone 15'
```

### Running the app on your Mac (no phone needed)

```bash
./scripts/run-android.sh
```

Boots an Android emulator **with a window**, builds and installs the debug app,
grants its permissions and launches it. Re-running reuses a running emulator.

Start the backend first or the app runs with no weather:

```bash
cd backend/api && .venv/bin/python -m uvicorn app.main:app --port 8000
```

The debug build targets `http://10.0.2.2:8000`, which is how the emulator reaches
a server on the host.

### Build artifacts

Built apps are collected into `dist/`, split by platform and variant:

```bash
cd android && ./gradlew assembleDebug bundleRelease
./scripts/collect-artifacts.sh android
```

Layout and rationale: [dist/README.md](dist/README.md). CI publishes the same
structure as workflow artifacts.

### Android Tests
```bash
cd android && ./gradlew test
```

## 🚢 Deployment

### Backend Deployment

**Docker Compose (Production):**
```bash
docker-compose -f docker-compose.prod.yml up -d
```

**Individual Services:**
```bash
# API
cd backend/api
docker build -t climaai-api .
docker run -p 8000:8000 --env-file .env climaai-api

# Payment Service
cd backend/payment-service
docker build -t climaai-payment .
docker run -p 3000:3000 --env-file .env climaai-payment
```

**Cloud Deployment:**
- AWS: ECS/Fargate, RDS PostgreSQL, ElastiCache Redis
- Google Cloud: Cloud Run, Cloud SQL, Memorystore
- Azure: Container Apps, Azure Database, Azure Cache

### iOS Deployment

1. **Archive the app:**
   - Product → Archive in Xcode

2. **Upload to App Store Connect:**
   - Distribute App → App Store Connect

3. **Configure in App Store Connect:**
   - App metadata
   - Screenshots
   - Privacy information
   - In-App Purchase products

4. **Submit for review**

### Android Deployment

1. **Generate signed APK/AAB:**
```bash
cd android
./gradlew bundleRelease
```

2. **Upload to Google Play Console:**
   - Production track or Internal testing

3. **Configure:**
   - Store listing
   - Content rating
   - Pricing & distribution
   - In-app products

4. **Submit for review**

## 📊 Database Schema

```sql
users
├── id (UUID, PK)
├── email (VARCHAR, UNIQUE)
├── password_hash (VARCHAR)
├── full_name (VARCHAR)
├── preferences (JSONB)
└── timestamps

subscriptions
├── id (UUID, PK)
├── user_id (UUID, FK)
├── platform (VARCHAR)
├── plan (VARCHAR)
├── status (VARCHAR)
├── trial_dates
├── subscription_dates
└── platform_specific_ids
```

## 🔐 Security

- JWT-based authentication
- Bcrypt password hashing
- HTTPS only in production
- Receipt validation (Apple & Google)
- Rate limiting
- SQL injection prevention (SQLAlchemy)
- XSS protection
- CORS configuration

## 📈 Monitoring & Analytics

### Backend Metrics
- Request latency
- Error rates
- Cache hit rates
- API usage by endpoint

### Mobile Analytics
- User acquisition
- Subscription conversion
- Feature usage
- Crash reports

**Recommended Tools:**
- Backend: Prometheus + Grafana, Sentry
- Mobile: Firebase Analytics, Crashlytics

## 💰 Monetization

**Compiled out.** `MONETIZATION_ENABLED=false` in both Android build types: the
shipped app is free, with no ads, no paywall, and nothing purchasable. The
subscription stack (backend plans, payment-service webhooks, billing client)
exists but is inert. Do not flip the flag before the conditions in
[ROADMAP.md](ROADMAP.md) are met — the Open-Meteo licensing constraint in
[docs/WEATHER_APIS.md](docs/WEATHER_APIS.md) is the hard one.

## 📜 License

Proprietary - All rights reserved

## 🤝 Contributing

This is a production application. For contributions, please contact the development team.

## 📞 Support

- Email: singhaditya21@gmail.com
- Legal & policy pages (GitHub Pages, served from `docs/`):
  [privacy](https://singhaditya21.github.io/CLIMAAI/privacy.html) ·
  [terms](https://singhaditya21.github.io/CLIMAAI/terms.html) ·
  [account deletion](https://singhaditya21.github.io/CLIMAAI/delete-account.html)

## 🔄 Version History

### v1.0.0 (unreleased — see [ROADMAP.md](ROADMAP.md) for blockers)
- ✅ Multi-source weather with consensus confidence readout
- ✅ Radar, air quality, Europe-only pollen
- ✅ Widgets, Apple Watch and Wear OS apps with synced real data
- ✅ Offline caching and background updates
- 💤 AI insights and subscriptions present in code, off by configuration

## 🗺️ Roadmap

See **[ROADMAP.md](ROADMAP.md)** for current status, release blockers, and the
competitor-parity backlog.

Widgets, the Apple Watch app, the Wear OS app, the radar overlay, multi-location
support, alert push notifications, and historical weather data are **already
implemented** — earlier versions of this file listed them as planned.

## 🙏 Credits

- **Weather Data**: Open-Meteo (CC BY 4.0), MET Norway, US NWS and others —
  see [docs/WEATHER_APIS.md](docs/WEATHER_APIS.md) for the full list and the
  attribution obligations
- **Radar**: RainViewer
- **Geocoding**: Nominatim / © OpenStreetMap contributors (ODbL)
- **Icons**: SF Symbols (iOS), Material Icons (Android)
- **Backend**: FastAPI, Express.js
- **Mobile**: SwiftUI, Jetpack Compose

---

**Built with ❤️ by the ClimaAI Team**
