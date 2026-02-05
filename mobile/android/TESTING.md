# Android Testing Plan

## ✅ Pre-Testing Setup

### 1. Backend Status
- [ ] Backend running on http://localhost:8000
- [ ] Database migration applied
- [ ] Redis running
- [ ] OpenAI API key configured

### 2. Android Project Setup
- [ ] Android Studio project opens without errors
- [ ] `google-services.json` (if used) configured
- [ ] `local.properties` has correct SDK path
- [ ] Google Play Billing configured
- [ ] Android Manifest permissions correct

---

## 🧪 Testing Checklist

### Phase 1: Basic Build & Launch
- [ ] Project builds successfully (Build > Make Project)
- [ ] App launches in emulator/device
- [ ] No runtime crashes (ANR)
- [ ] Splash screen appears

### Phase 2: Authentication Flow
- [ ] **Onboarding**
  - [ ] Onboarding pager works (swipe left/right)
  - [ ] Page indicators update
  - [ ] "Get Started" button works
  - [ ] Location permission dialog appears
  - [ ] Navigates to Login/Register

- [ ] **Registration**
  - [ ] Form validation (email format, password length)
  - [ ] Error messages display (Toast or inline)
  - [ ] Can create new account
  - [ ] Token saved in DataStore (check logs or inspection)

- [ ] **Login**
  - [ ] Can login with valid credentials
  - [ ] Error messages for invalid credentials
  - [ ] Loading indicator shows during network request
  - [ ] Navigates to Main Activity

### Phase 3: Main Weather Features
- [ ] **Home Screen**
  - [ ] Location permission works (approximate vs precise)
  - [ ] Current weather loads (Temperature, Condition)
  - [ ] Weather details card shows (Humidity, Wind, etc.)
  - [ ] Dynamic background changes based on weather condition
  - [ ] Pull-to-refresh works

- [ ] **Hourly Forecast**
  - [ ] Horizontal scroll list loads
  - [ ] 24-hour forecast data visible
  - [ ] Icons match weather conditions
  - [ ] Time formatting correct (AM/PM or 24h)

- [ ] **Daily Forecast**
  - [ ] 7-day list displays
  - [ ] Min/Max temperatures shown
  - [ ] Click to expand (if implemented)
  - [ ] Premium upsell banner visible (for free users)

- [ ] **Air Quality (AQI)**
  - [ ] AQI gauge/value displays
  - [ ] Pollutant breakdown (PM2.5, etc.) shows
  - [ ] Color coding matches standard AQI levels
  - [ ] Health advice text visible

### Phase 4: AI Features (Premium)
- [ ] **Premium Paywall**
  - [ ] Triggered when accessing AI features as free user
  - [ ] Subscription plans display (Monthly/Annual)
  - [ ] Prices match Google Play Console
  - [ ] "Start Trial" button works
  - [ ] Terms & Privacy links clickable

- [ ] **AI Insights Tab**
  - [ ] Daily summary loads (Natural Language)
  - [ ] Outfit recommendations display
  - [ ] Activity suggestions (Hiking, Running, etc.)
  - [ ] Health insights (Allergies, UV)

- [ ] **AI Chat / Query** (if applicable)
  - [ ] Input field works
  - [ ] Response stream/load works
  - [ ] Error handling for API failures

### Phase 5: Settings & Profile
- [ ] **Settings Menu**
  - [ ] Profile info displays correctly
  - [ ] Subscription status (Free/Premium)
  - [ ] Units selection (Metric/Imperial) updates UI
  - [ ] Theme selection (Light/Dark/System) works
  - [ ] Sign out clears session and navigates to Login

### Phase 6: Edge Cases & Resilience
- [ ] **No Internet**
  - [ ] Cached data displays if available
  - [ ] "No Connection" Snackbar/Banner appears
  - [ ] Retry button works

- [ ] **Location Denied**
  - [ ] Default location (e.g., San Francisco) loads
  - [ ] Prompt to enable location in Settings
  - [ ] Manual location search works

- [ ] **Background Updates**
  - [ ] Widget (if implemented) updates
  - [ ] Notifications appear (if enabled)

---

## 🐛 Known Issues to Check

### Backend Integration
- [ ] API Base URL: ensure it points to `10.0.2.2` for Emulator or local IP for device
- [ ] Token expiration handling (auto-logout or refresh)

### UI/UX (Material Design)
- [ ] Ripple effects on buttons/list items
- [ ] Dark mode contrast checks
- [ ] Landscape mode behavior (or locked to portrait)
- [ ] Back button navigation stack behavior

### Performance
- [ ] Image loading (Coil) caching works
- [ ] Scroll smoothness in long lists (LazyColumn)
- [ ] Memory usage during graph rendering

---

## 📝 Test Results Log

### Build Information
- **Date/Time:** ___________
- **Device/Emulator:** ___________ (e.g., Pixel 7, Android 14)
- **Build Variant:** Debug / Release

### Critical Bugs Found
1. ___________
2. ___________
3. ___________

### Minor Issues Found
1. ___________
2. ___________
3. ___________

### Sign-Off
- [ ] **Ready for Internal Testing** (All Phase 1-3 pass)
- [ ] **Ready for Production** (All Phases pass + No crashes)
