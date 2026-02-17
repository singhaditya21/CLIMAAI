# ClimaAI Deployment Guide

This guide covers deployment of the ClimaAI backend and mobile applications to production.

## Table of Contents

1. [Backend Deployment](#backend-deployment)
2. [iOS App Deployment](#ios-app-deployment)
3. [Android App Deployment](#android-app-deployment)
4. [Environment Configuration](#environment-configuration)
5. [Monitoring & Maintenance](#monitoring--maintenance)

---

## Backend Deployment

### Prerequisites

- Docker & Docker Compose
- PostgreSQL 15+ database (hosted)
- Redis instance (hosted)
- Domain with SSL certificate
- OpenAI API key

### Option 1: Docker Compose (VPS/EC2)

**1. Prepare server:**

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

**2. Clone repository:**

```bash
git clone <your-repo-url>
cd clima-ai/backend
```

**3. Configure environment:**

```bash
# API configuration
cd api
cp .env.example .env
nano .env  # Edit with production values

# Payment service configuration
cd ../payment-service
cp .env.example .env
nano .env
```

**4. Update docker-compose for production:**

Create `docker-compose.prod.yml`:

```yaml
version: '3.8'

services:
  api:
    build: ./api
    restart: always
    environment:
      - DEBUG=false
      - DATABASE_URL=${DATABASE_URL}
      - REDIS_URL=${REDIS_URL}
      - OPENAI_API_KEY=${OPENAI_API_KEY}
    depends_on:
      - postgres
      - redis
    networks:
      - clima-network

  payment-service:
    build: ./payment-service
    restart: always
    environment:
      - NODE_ENV=production
    networks:
      - clima-network

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./ssl:/etc/nginx/ssl
    depends_on:
      - api
      - payment-service
    networks:
      - clima-network

networks:
  clima-network:
    driver: bridge
```

**5. Configure Nginx:**

Create `nginx.conf`:

```nginx
http {
    upstream api {
        server api:8000;
    }

    upstream payment {
        server payment-service:3000;
    }

    server {
        listen 80;
        server_name api.climaai.com;
        return 301 https://$server_name$request_uri;
    }

    server {
        listen 443 ssl http2;
        server_name api.climaai.com;

        ssl_certificate /etc/nginx/ssl/cert.pem;
        ssl_certificate_key /etc/nginx/ssl/key.pem;

        location / {
            proxy_pass http://api;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        location /webhooks/ {
            proxy_pass http://payment;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
    }
}
```

**6. Deploy:**

```bash
docker-compose -f docker-compose.prod.yml up -d
```

**7. Verify:**

```bash
curl https://api.climaai.com/health
```

### Option 2: AWS Deployment

**Services:**
- **ECS/Fargate**: Container orchestration
- **RDS PostgreSQL**: Managed database
- **ElastiCache Redis**: Managed Redis
- **Application Load Balancer**: Traffic distribution  
- **Route 53**: DNS management
- **ACM**: SSL certificates

**Architecture:**

```
Internet → ALB (HTTPS) → ECS Service (API + Payment) → RDS + ElastiCache
```

**Deploy steps:**

1. **Create RDS PostgreSQL instance:**

```bash
aws rds create-db-instance \
    --db-instance-identifier climaai-db \
    --db-instance-class db.t3.micro \
    --engine postgres \
    --engine-version 15.4 \
    --master-username climaai \
    --master-user-password <password> \
    --allocated-storage 20
```

2. **Create ElastiCache Redis cluster:**

```bash
aws elasticache create-cache-cluster \
    --cache-cluster-id climaai-redis \
    --cache-node-type cache.t3.micro \
    --engine redis \
    --num-cache-nodes 1
```

3. **Create ECS cluster:**

```bash
aws ecs create-cluster --cluster-name climaai-cluster
```

4. **Push Docker images to ECR:**

```bash
# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Build and push API
cd backend/api
docker build -t climaai-api .
docker tag climaai-api:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/climaai-api:latest
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/climaai-api:latest

# Build and push payment service
cd ../payment-service
docker build -t climaai-payment .
docker tag climaai-payment:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/climaai-payment:latest
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/climaai-payment:latest
```

5. **Create task definitions and services** via AWS Console or CLI

### Option 3: Google Cloud Run

**1. Build and push images:**

```bash
# Enable services
gcloud services enable run.googleapis.com
gcloud services enable cloudbuild.googleapis.com

# Build API
cd backend/api
gcloud builds submit --tag gcr.io/PROJECT_ID/climaai-api

# Build payment service
cd ../payment-service
gcloud builds submit --tag gcr.io/PROJECT_ID/climaai-payment
```

**2. Create Cloud SQL PostgreSQL:**

```bash
gcloud sql instances create climaai-db \
    --database-version=POSTGRES_15 \
    --tier=db-f1-micro \
    --region=us-central1
```

**3. Create Memorystore Redis:**

```bash
gcloud redis instances create climaai-redis \
    --size=1 \
    --region=us-central1
```

**4. Deploy to Cloud Run:**

```bash
# Deploy API
gcloud run deploy climaai-api \
    --image gcr.io/PROJECT_ID/climaai-api \
    --platform managed \
    --region us-central1 \
    --allow-unauthenticated \
    --set-env-vars DATABASE_URL=<connection-string>,REDIS_URL=<redis-url>

# Deploy payment service
gcloud run deploy climaai-payment \
    --image gcr.io/PROJECT_ID/climaai-payment \
    --platform managed \
    --region us-central1
```

---

## iOS App Deployment

### Prerequisites

- Active Apple Developer Program membership ($99/year)
- Xcode 15+
- Mac with macOS 14+
- App Store Connect access

### Step 1: Prepare App

**1. Update configuration:**

```swift
// Update Bundle Identifier in Xcode project settings
// com.yourcompany.climaai

// Update API base URL in APIClient.swift
private let baseURL = "https://api.climaai.com"
```

**2. Configure capabilities:**

- App Identifier: Register in Apple Developer portal
- Enable: In-App Purchase, Push Notifications
- Create App ID with explicit identifier

**3. Setup In-App Purchases:**

In App Store Connect:
- Create subscription group: "ClimaAI Premium"
- Add products:
  - `com.yourcompany.climaai.monthly` - $4.99/month
  - `com.yourcompany.climaai.annual` - $39.99/year
- Configure 7-day free trial
- Set up pricing in all regions

**4. Update product IDs in code:**

```swift
// SubscriptionManager.swift
private let monthlyProductID = "com.yourcompany.climaai.monthly"
private let annualProductID = "com.yourcompany.climaai.annual"
```

### Step 2: Build for Release

**1. Update version:**

- Version: 1.0.0
- Build: 1

**2. Archive:**

- Product → Archive in Xcode
- Wait for archive to complete

**3. Validate:**

- Select archive → Validate App
- Fix any issues

### Step 3: Upload to App Store Connect

**1. Distribute:**

- Click "Distribute App"
- Choose "App Store Connect"
- Select "Upload"
- Choose automatic signing
- Upload

**2. Wait for processing:**

- Processing can take 15-60 minutes
- You'll receive email when ready

### Step 4: Configure in App Store Connect

**1. App Information:**

```
Name: ClimaAI - AI Weather
Subtitle: Smart Weather Forecasts
Category: Weather
Content Rights: Yes (if using own content)
```

**2. Pricing:**

```
Price: Free
In-App Purchases: Added (monthly & annual)
```

**3. App Privacy:**

Privacy Policy URL: https://climaai.com/privacy

Data Collection:
- Location: Used for weather forecasts
- Email: For account creation
- Purchase History: For subscription management

**4. Prepare screenshots:**

Required sizes:
- iPhone 6.7" (iPhone 15 Pro Max): 1290 x 2796
- iPhone 6.5" (iPhone 14 Plus): 1284 x 2778
- iPhone 5.5" (iPhone 8 Plus): 1242 x 2208

Recommended: 5-10 screenshots showing:
- Home screen with weather
- Hourly forecast
- Daily forecast
- AI insights (premium)
- Subscription paywall

**5. App Preview video (optional but recommended)**

**6. Description:**

See [APP_STORE.md](APP_STORE.md) for marketing copy

### Step 5: Submit for Review

**1. Select build**

**2. Fill submission checklist:**

- Export compliance: No
- Advertising identifier: If using analytics
- Content rights: Confirm

**3. Submit**

**Typical review time: 24-48 hours**

### Step 6: Release

Options:
- **Manual release**: Release after approval
- **Automatic release**: Release immediately upon approval
- **Scheduled release**: Release at specific date/time

---

## Android App Deployment

### Prerequisites

- Google Play Developer account ($25 one-time)
- Android Studio
- Signing keystore

### Step 1: Prepare App

**1. Update configuration:**

```kotlin
// build.gradle (app)
applicationId "com.yourcompany.climaai"
versionCode 1
versionName "1.0.0"

// Update API URL
buildConfigField "String", "API_BASE_URL", '"https://api.climaai.com"'
```

**2. Create signing keystore:**

```bash
keytool -genkey -v -keystore climaai-release-key.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias climaai-key
```

**3. Configure signing:**

Create `keystore.properties`:

```properties
storePassword=<password>
keyPassword=<password>
keyAlias=climaai-key
storeFile=../climaai-release-key.jks
```

Update `build.gradle`:

```gradle
android {
    signingConfigs {
        release {
            storeFile file(keystoreProperties['storeFile'])
            storePassword keystoreProperties['storePassword']
            keyAlias keystoreProperties['keyAlias']
            keyPassword keystoreProperties['keyPassword']
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            ...
        }
    }
}
```

### Step 2: Setup Google Play Billing

**1. In Google Play Console:**

- Create app
- Setup products & subscriptions:
  - `climaai_monthly` - $4.99/month
  - `climaai_annual` - $39.99/year
- Add 7-day free trial
- Configure pricing in all countries

**2. Update product IDs in code**

### Step 3: Build Release APK/AAB

```bash
cd android
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

### Step 4: Upload to Google Play Console

**1. Create release:**

- Production track (or internal testing first)
- Upload AAB
- Fill release notes

**2. Store Listing:**

```
App name: ClimaAI - AI Weather
Short description: AI-powered weather forecasts with intelligent insights
Full description: See APP_STORE.md
```

**3. Screenshots:**

Required for each device type:
- Phone: minimum 2, recommended 8
- 7" Tablet: minimum 1
- 10" Tablet: minimum 1

Sizes:
- Minimum: 320px
- Maximum: 3840px
- Aspect ratio: 16:9 or 9:16

**4. App icon:**

- 512 x 512 PNG
- 32-bit with alpha

**5. Feature graphic:**

- 1024 x 500 PNG/JPEG

**6. Category & Content:**

- Category: Weather
- Content rating: Everyone
- Target audience: 13+

**7. Privacy Policy:**

URL: https://climaai.com/privacy

Data Safety:
- Location collected
- Personal info (email) collected
- Data encrypted in transit
- Data can be deleted

### Step 5: Submit

**1. Complete all sections**

**2. Review and rollout**

**Typical review time: 1-7 days (first app can take longer)**

---

## Environment Configuration

### Production Environment Variables

#### Backend API (.env)

```env
DEBUG=false
APP_NAME=ClimaAI API
APP_VERSION=1.0.0

# Database
DATABASE_URL=postgresql+asyncpg://user:pass@host:5432/db

# Redis
REDIS_URL=redis://host:6379/0
WEATHER_CACHE_TTL=1800
AI_CACHE_TTL=3600

# OpenAI
OPENAI_API_KEY=sk-proj-xxx
OPENAI_MODEL=gpt-4-turbo-preview

# JWT
JWT_SECRET=<strong-random-string-min-32-chars>
JWT_ALGORITHM=HS256
JWT_EXPIRATION_HOURS=720

# CORS
CORS_ORIGINS=["https://climaai.com"]

# App IDs
APPLE_BUNDLE_ID=com.yourcompany.climaai
GOOGLE_PACKAGE_NAME=com.yourcompany.climaai

# Features
ENABLE_AI_INSIGHTS=true
```

#### Payment Service (.env)

```env
PORT=3000
NODE_ENV=production

# Database
DB_HOST=<host>
DB_PORT=5432
DB_USER=<user>
DB_PASSWORD=<password>
DB_NAME=climaai

# Apple
APPLE_BUNDLE_ID=com.yourcompany.climaai
APPLE_SHARED_SECRET=<from-app-store-connect>

# Google
GOOGLE_PACKAGE_NAME=com.yourcompany.climaai
```

### SSL Certificates

**1. Let's Encrypt (Free):**

```bash
sudo apt install certbot
sudo certbot certonly --standalone -d api.climaai.com
```

**2. Copy certificates:**

```bash
sudo cp /etc/letsencrypt/live/api.climaai.com/fullchain.pem ./ssl/cert.pem
sudo cp /etc/letsencrypt/live/api.climaai.com/privkey.pem ./ssl/key.pem
```

**3. Auto-renewal:**

```bash
sudo crontab -e
# Add: 0 0 1 * * certbot renew --quiet
```

---

## Monitoring & Maintenance

### Backend Monitoring

**1. Health checks:**

```bash
# Automated monitoring
*/5 * * * * curl -f https://api.climaai.com/health || alert
```

**2. Logs:**

```bash
# Docker logs
docker-compose logs -f api
docker-compose logs -f payment-service

# Application logs
tail -f /var/log/climaai/api.log
```

**3. Metrics (Prometheus + Grafana recommended)**

**4. Error tracking (Sentry recommended)**

### Database Backups

```bash
# Automated daily backups
0 2 * * * pg_dump -U climaai -h localhost climaai > /backups/climaai-$(date +\%Y\%m\%d).sql

# Retention: Keep 30 days
find /backups -name "*.sql" -mtime +30 -delete
```

### Mobile App Updates

**iOS:**
- Increment build number for each submission
- Major version (1.x.0) for features
- Minor version (x.1.x) for improvements
- Patch version (x.x.1) for fixes

**Android:**
- Increment versionCode for each release
- Follow semver for versionName

### Update Process

1. Develop and test changes
2. Version bump
3. Build and archive/bundle
4. Upload to stores
5. Submit for review
6. Release after approval

---

## Scaling Considerations

### Backend

- **Horizontal scaling**: Add more API containers
- **Database**: Read replicas, connection pooling
- **Redis**: Redis Cluster for high availability
- **CDN**: CloudFlare for static assets
- **Load balancer**: Distribute traffic

### Cost Optimization

- **OpenAI**: Use GPT-3.5-turbo instead of GPT-4
- **Caching**: Aggressive caching to reduce API calls
- **Database**: Proper indexing, query optimization
- **Infrastructure**: Start small, scale as needed

---

## Security Checklist

- [ ] HTTPS everywhere
- [ ] Environment variables secured
- [ ] Database credentials rotated
- [ ] JWT secret strong and secret
- [ ] Rate limiting enabled
- [ ] API key for Open-Meteo (if needed)
- [ ] Logging enabled (no sensitive data)
- [ ] Firewall configured
- [ ] Regular security updates
- [ ] Penetration testing (recommended)

---

## Support & Troubleshooting

### Common Issues

**Backend won't start:**
- Check database connection
- Verify environment variables
- Check Docker logs

**Subscription not activating:**
- Verify receipt in sandbox/production
- Check webhook configuration
- Validate App Store Connect/Play Console setup

**Weather data not loading:**
- Check Open-Meteo API status
- Verify network connectivity
- Check Redis cache

### Getting Help

- Documentation: https://docs.climaai.com
- Email: support@climaai.com
- GitHub Issues: (if open source)

---

**Deployment complete! 🚀**
