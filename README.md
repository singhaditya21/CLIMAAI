# ClimaAI - AI-Powered Weather Application

![ClimaAI Logo](docs/images/logo.png)

A complete, production-ready, subscription-based AI weather application for iOS and Android with intelligent weather insights powered by OpenAI.

## 🌟 Features

### Free Tier
- ✅ Current temperature & conditions
- ✅ Today's hourly forecast (24 hours)
- ✅ 7-day daily forecast
- ✅ Sunrise / sunset times
- ✅ Basic wind & precipitation
- ✅ Location auto-detect
- ✅ Light/Dark mode

### Premium Features (💎 $4.99/month or $39.99/year)
- 🌩 **Advanced Forecasting**
  - 16-day weather forecast
  - Minute-level rain prediction
  - Severe weather alerts
  - Storm tracking

- 🧠 **AI Weather Intelligence**
  - "What should I wear today?" outfit recommendations
  - Travel weather risk analysis
  - Outdoor activity recommendations with best times
  - Health insights (heat stress, pollen risk, air quality impact)
  - AI daily weather summary in natural language

- 🌬 **Environment & Health**
  - Air Quality Index (AQI) with detailed breakdown
  - UV exposure risk analysis
  - Pollution component breakdown (PM2.5, PM10, NO₂, O₃, etc.)
  - Weather impact on asthma/allergies

- 🧳 **Lifestyle & Planning**
  - Best time to go outside
  - Best time to exercise
  - Farming/weather advisory (rainfall, humidity)
  - Marine & beach weather (coastal locations)

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
│   └── init.sql              # Database schema
├── android/                   # Android Jetpack Compose app
├── ios/                       # iOS SwiftUI app
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
  - OpenAI API key (for AI insights)
  - Apple Developer Account (for iOS)
  - Google Play Developer Account (for Android)

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
- Update `Bundle Identifier` in project settings
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
```

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
- `GET /weather/current` - Current weather
- `GET /weather/hourly` - Hourly forecast
- `GET /weather/daily` - Daily forecast
- `GET /weather/air-quality` - Air quality data

### AI Insights (Premium)
- `GET /ai/insights` - Complete AI insights
- `GET /ai/summary` - Daily summary
- `GET /ai/outfit` - Outfit recommendation
- `GET /ai/activities` - Activity suggestions
- `GET /ai/health` - Health insights

### User Management
- `POST /users/register` - Register new user
- `POST /users/login` - Login
- `GET /users/me` - Get current user
- `PUT /users/me` - Update user

### Subscriptions
- `GET /subscriptions/status` - Check subscription
- `POST /subscriptions/trial` - Start 7-day trial
- `POST /subscriptions/activate` - Activate subscription
- `GET /subscriptions/plans` - Get available plans

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

### Backend Tests
```bash
cd backend/api
pytest tests/ -v
```

### iOS Tests
```bash
cd ios
xcodebuild test -scheme ClimaAI -destination 'platform=iOS Simulator,name=iPhone 15'
```

### Android Tests
```bash
cd android
./gradlew test
./gradlew connectedAndroidTest
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

### Subscription Tiers
- **Monthly Premium**: $4.99/month
- **Annual Premium**: $39.99/year (33% savings)
- **Free Trial**: 7 days

### Revenue Sharing
- Apple: 30% for first year, 15% after
- Google: 15% for subscriptions

## 📜 License

Proprietary - All rights reserved

## 🤝 Contributing

This is a production application. For contributions, please contact the development team.

## 📞 Support

- Email: support@climaai.com
- Website: https://climaai.com
- Documentation: https://docs.climaai.com

## 🔄 Version History

### v1.0.0 (Initial Release)
- ✅ Complete weather data integration (Open-Meteo)
- ✅ AI-powered insights (OpenAI GPT-4)
- ✅ Subscription management (Apple & Google)
- ✅ iOS & Android apps
- ✅ 7-day free trial
- ✅ Offline caching
- ✅ Background updates

## 🗺️ Roadmap

### v1.1 (Planned)
- [ ] Weather widgets
- [ ] Apple Watch support
- [ ] Android Wear OS support
- [ ] Multi-location support
- [ ] Weather alerts push notifications

### v1.2 (Planned)
- [ ] Historical weather data
- [ ] Weather radar overlay
- [ ] Share weather snapshots
- [ ] Voice assistant integration
- [ ] Weather-based automations

### v2.0 (Future)
- [ ] Hyperlocal forecasting
- [ ] AR weather visualization
- [ ] Community weather reports
- [ ] Advanced AI with GPT-5
- [ ] Gamification features

## 🙏 Credits

- **Weather Data**: Open-Meteo API
- **AI**: OpenAI GPT-4
- **Icons**: SF Symbols (iOS), Material Icons (Android)
- **Backend**: FastAPI, Express.js
- **Mobile**: SwiftUI, Jetpack Compose

---

**Built with ❤️ by the ClimaAI Team**
