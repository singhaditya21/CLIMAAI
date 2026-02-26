# iOS App Store Deployment Guide

*ClimaAI v1.0 → App Store*

---

## Pre-Flight Checklist

| Requirement | Status | Action |
|-------------|:------:|--------|
| Apple Developer Account | ❓ | Required ($99/year) |
| Xcode Project | ❓ | Create per `XCODE_SETUP.md` |
| Bundle ID Registered | ❌ | Register in Developer Portal |
| App Icons (1024x1024) | ❌ | Create in Asset Catalog |
| Privacy Policy URL | ❌ | Host on website |
| App Store Screenshots | ❌ | Capture from simulator |
| App Description | ❌ | Write marketing copy |

---

## Step 1: Apple Developer Account

> [!IMPORTANT]
> **You need an Apple Developer Program membership ($99/year)**

1. Go to [developer.apple.com/programs](https://developer.apple.com/programs/)
2. Enroll as Individual or Organization
3. Complete payment (~24-48hrs for approval)

---

## Step 2: Create Xcode Project

```bash
# Open the iOS directory in Finder
open /Users/adityasingh/clima-ai/ios/

# Then follow XCODE_SETUP.md to create the project
```

**Key Settings:**
- **Bundle ID:** `com.climaai.app`
- **Version:** 1.0
- **Build:** 1
- **iOS Deployment Target:** 16.0+

---

## Step 3: Register Bundle ID

1. Go to [App Store Connect](https://appstoreconnect.apple.com)
2. **Certificates, Identifiers & Profiles** → **Identifiers**
3. Click **+** to register new identifier
4. Select **App IDs** → **App**
5. Configure:
   - **Description:** ClimaAI Weather
   - **Bundle ID:** `com.climaai.app` (Explicit)
   - **Capabilities:** ✅ Push Notifications, ✅ App Groups

---

## Step 4: App Icons

Create `AppIcon.appiconset` with these sizes:

| Size | Scale | Filename |
|------|-------|----------|
| 20pt | 2x | Icon-40.png |
| 20pt | 3x | Icon-60.png |
| 29pt | 2x | Icon-58.png |
| 29pt | 3x | Icon-87.png |
| 40pt | 2x | Icon-80.png |
| 40pt | 3x | Icon-120.png |
| 60pt | 2x | Icon-120.png |
| 60pt | 3x | Icon-180.png |
| 1024pt | 1x | Icon-1024.png (App Store) |

**Quick solution:** Use [appicon.co](https://appicon.co) - upload 1024x1024, get all sizes

---

## Step 5: Create App in App Store Connect

1. [App Store Connect](https://appstoreconnect.apple.com) → **My Apps**
2. Click **+** → **New App**
3. Fill in:
   - **Platform:** iOS
   - **Name:** ClimaAI - AI Weather
   - **Primary Language:** English (U.S.)
   - **Bundle ID:** Select `com.climaai.app`
   - **SKU:** `climaai-ios-1`
   - **User Access:** Full Access

---

## Step 6: App Store Listing

### 6.1 App Information

| Field | Value |
|-------|-------|
| Category | Weather |
| Content Rights | Uses third-party weather data (Open-Meteo, NWS) |
| Age Rating | 4+ |

### 6.2 App Description (Ready to Use)

**Name:** `ClimaAI - AI Weather`

**Subtitle:** `Smart Forecasts with GPT-4`

**Promotional Text:**
```
The first AI-native weather app. Get personalized insights, not just data.
```

**Description:**
```
ClimaAI is the intelligent weather app that understands your lifestyle. Powered by GPT-4, it provides personalized weather insights, activity recommendations, and health alerts tailored to you.

⛅ AI-POWERED INSIGHTS
• Natural language weather summaries
• Smart outfit recommendations  
• Activity forecasts for running, cycling, golf & more
• Personalized daily briefings

🌧️ ACCURATE FORECASTS
• Minute-by-minute rain predictions
• 16-day extended forecast
• Animated radar maps
• Severe weather alerts

🏃 ACTIVITY FORECASTS
• Know the best time to run, hike, or golf
• Air quality impact on outdoor activities
• UV exposure warnings

🌿 HEALTH FEATURES
• Pollen forecasts for allergy sufferers
• Flu risk index
• Migraine trigger alerts
• Air Quality Index (AQI)

⌚ APPLE WATCH
• Weather complications
• Quick glance at conditions
• Hourly forecasts on your wrist

📱 BEAUTIFUL WIDGETS
• Home screen weather at a glance
• Lock screen widgets
• StandBy mode support

Premium unlocks:
• Extended 16-day forecasts
• All activity forecasts
• Priority AI insights
• Ad-free experience

Download ClimaAI and experience weather intelligence.
```

**Keywords:**
```
weather,forecast,rain,radar,AI,pollen,allergy,alerts,widgets,apple watch
```

### 6.3 Screenshots Required

| Device | Size | Count |
|--------|------|:-----:|
| iPhone 6.7" | 1290 x 2796 | 3-10 |
| iPhone 6.5" | 1284 x 2778 | 3-10 |
| iPhone 5.5" | 1242 x 2208 | 3-10 |
| iPad Pro 12.9" | 2048 x 2732 | Optional |

**Recommended screens to capture:**
1. Home screen with current weather
2. AI Insights page
3. Hourly forecast
4. Radar map
5. Activity recommendations
6. Apple Watch face

---

## Step 7: Privacy Policy

> [!CAUTION]
> **Apple REQUIRES a Privacy Policy URL**

Host at: `https://climaai.com/privacy`

**Template content:**
```
ClimaAI Privacy Policy

Last updated: January 31, 2026

ClimaAI ("we") collects:
- Location data (to provide local weather)
- Usage analytics (to improve the app)

We do NOT sell your data.

Data is processed by:
- OpenAI (AI insights)
- Open-Meteo (weather data)

Contact: privacy@climaai.com
```

---

## Step 8: Build & Archive

### 8.1 Configure Signing

1. Xcode → Project → **Signing & Capabilities**
2. Team: Select your Apple Developer team
3. Signing Certificate: **Apple Distribution**
4. Provisioning Profile: Automatic

### 8.2 Archive

```bash
# 1. Select "Any iOS Device (arm64)" as target
# 2. Product → Archive (or ⌘⇧K then Product → Archive)
```

In Xcode:
1. **Product** → **Archive**
2. Wait for build...
3. Organizer window opens

### 8.3 Validate & Upload

1. In Organizer, select your archive
2. Click **Distribute App**
3. Select **App Store Connect**
4. Select **Upload**
5. Follow prompts
6. Wait for processing (~10-30 minutes)

---

## Step 9: Submit for Review

1. Go to App Store Connect → Your App
2. Prepare for Submission:
   - ✅ Add screenshots
   - ✅ Add app description
   - ✅ Set pricing (Free with IAP)
   - ✅ Privacy policy URL
   - ✅ Select build from uploaded archives

3. Click **Submit for Review**

---

## Step 10: App Review

| Typical Timeline | |
|-----------------|---|
| Processing | 10-30 min |
| Review Queue | 24-48 hours |
| Review | 24-48 hours |
| **Total** | **1-3 days** |

### Common Rejection Reasons

1. **Missing Privacy Policy** - Must be accessible URL
2. **Broken functionality** - Test all features
3. **Placeholder content** - No lorem ipsum
4. **Login required** - Provide demo account
5. **Incomplete IAP** - Configure all products

---

## Quick Commands

```bash
# Open Xcode project (after creating)
open /Users/adityasingh/clima-ai/ios/ClimaAI.xcodeproj

# Validate Info.plist
plutil -lint /Users/adityasingh/clima-ai/ios/ClimaAI/Info.plist

# Check provisioning profiles
security find-identity -v -p codesigning
```

---

## What's Missing Summary

| Item | Priority | Time | Status |
|------|:--------:|:----:|:------:|
| Apple Developer Account | P0 | - | ❓ Check |
| Xcode .xcodeproj | P0 | 15 min | Not created |
| App Icons | P0 | 30 min | Missing |
| Screenshots | P0 | 1 hr | Missing |
| Privacy Policy | P0 | 30 min | Missing |
| App Store Listing | P0 | 1 hr | Missing |
| Terms of Service | P1 | 30 min | Missing |
| StoreKit Config | P1 | 15 min | In guide |

**Total estimated time: 4-5 hours** (assuming Developer account ready)
