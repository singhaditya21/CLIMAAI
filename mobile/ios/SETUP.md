# iOS Integration & Setup Guide

## Database Migration

Apply the new database schema with favorite locations, device tokens, and weather alerts:

```bash
cd /Users/adityasingh/clima-ai/backend
psql -U postgres -d climaai_db -f 002_add_features.sql
```

## Backend Setup

1. **Ensure `.env` file has required keys**:
   ```bash
   cd /Users/adityasingh/clima-ai/backend/api
   # Edit .env file
   OPENAI_API_KEY=your_openai_api_key_here
   ```

2. **Start the backend**:
   ```bash
   cd /Users/adityasingh/clima-ai
   ./start.sh
   ```

3. **Verify endpoints**:
   ```bash
   curl http://localhost:8000/health
   # Should return: {"status":"healthy","app":"ClimaAI API","version":"1.0.0"}
   ```

## iOS Project Setup

### 1. Open in Xcode
```bash
cd /Users/adityasingh/clima-ai/ios
open ClimaAI.xcodeproj
```

### 2. Configure Bundle Identifier
- Select project in navigator
- Under "Signing & Capabilities"
- Set Bundle Identifier: `com.yourcompany.climaai`

### 3. Add StoreKit Configuration

Create `Configuration.storekit` file:
- File → New → File → StoreKit Configuration File
- Add Products:
  - **Monthly**: `com.climaai.premium.monthly` - $4.99/month
  - **Annual**: `com.climaai.premium.annual` - $39.99/year
- Set both with 7-day free trial

### 4. Update APIClient Base URL

Edit `APIClient.swift`:
```swift
private let baseURL = "http://localhost:8000"  // For simulator
// or
private let baseURL = "https://your-backend-url.com"  // For production
```

### 5. Build & Run
- Select target: iOS Simulator (iPhone 14 Pro or later)
- Press **⌘R** to build and run

## Testing Checklist

### Authentication Flow
- [ ] Onboarding appears on first launch
- [ ] Can register new account
- [ ] Can login with existing account
- [ ] Token saved correctly
- [ ] Logout works

### Weather Features
- [ ] Location permission requested
- [ ] Current weather loads
- [ ] Hourly forecast displays chart
- [ ] Daily forecast shows 7 days (free) / 14 days (premium)
- [ ] Air quality gauge displays correctly
- [ ] Pull-to-refresh works

### AI Features (Premium)
- [ ] AI insights tab shows paywall (free users)
- [ ] Paywall displays subscription plans
- [ ] Can start trial (sandbox)
- [ ] AI insights load after premium activated
- [ ] Travel risk analysis works

### Subscription
- [ ] Can purchase monthly plan (sandbox)
- [ ] Can purchase annual plan (sandbox)
- [ ] Trial status shows correctly
- [ ] Can restore purchases
- [ ] Can cancel subscription

### Settings
- [ ] Profile displays correctly
- [ ] Can update preferences
- [ ] Notification settings toggle
- [ ] Sign out works

## Troubleshooting

### Backend Connection Issues
```bash
# Check backend is running
curl http://localhost:8000/health

# Check Redis
redis-cli ping
# Should return: PONG

# Check PostgreSQL
psql -U postgres -d climaai_db -c "SELECT COUNT(*) FROM users;"
```

### StoreKit Sandbox Testing
1. Settings → App Store → Sandbox Account
2. Add test account from App Store Connect
3. Use test account in app for purchases

### Location Not Working
- Ensure Info.plist has location permissions
- Reset simulator: Device → Erase All Content and Settings
- Simulator → Features → Location → Custom Location (set coordinates)

## Next Steps

1. **Test all flows end-to-end**
2. **Fix any integration bugs**
3. **Add app icons** (Assets.xcassets/AppIcon)
4. **Configure signing** for TestFlight
5. **Submit for beta testing**

## Production Configuration

### Before App Store Submission

1. **Create production StoreKit products** in App Store Connect
2. **Update API base URL** to production backend
3. **Configure APNs** for push notifications
4. **Add app icon** (1024x1024 required)
5. **Update version number** and build number
6. **Create app listing** in App Store Connect
7. **Prepare screenshots** (required sizes)
8. **Write app description** and keywords
9. **Submit for review**

## Environment Variables

Backend `.env` required:
```
DATABASE_URL=postgresql://postgres:password@localhost:5432/climaai_db
REDIS_URL=redis://localhost:6379/0
OPENAI_API_KEY=sk-...
SECRET_KEY=your-secret-key
```

## Demo Account

For testing:
- Email: `demo@example.com`
- Password: `password`

(Create via register endpoint or database seed)
