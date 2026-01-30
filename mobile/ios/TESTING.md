# iOS Testing Plan

## ✅ Pre-Testing Setup

### 1. Backend Status
- [ ] Backend running on http://localhost:8000
- [ ] Database migration applied
- [ ] Redis running
- [ ] OpenAI API key configured

### 2. iOS Project Setup
- [ ] Xcode project opens without errors
- [ ] Bundle identifier configured
- [ ] Signing configured
- [ ] StoreKit configuration added
- [ ] Info.plist permissions correct

---

## 🧪 Testing Checklist

### Phase 1: Basic Build & Launch
- [ ] Project builds successfully (⌘B)
- [ ] App launches in simulator (⌘R)
- [ ] No runtime crashes
- [ ] Onboarding screen appears

### Phase 2: Authentication Flow
- [ ] **Onboarding**
  - [ ] 4 pages display correctly
  - [ ] Page indicators work
  - [ ] Location permission dialog appears
  - [ ] Can skip or continue
  - [ ] Navigates to login

- [ ] **Registration**
  - [ ] Form validation works
  - [ ] Password strength indicator updates
  - [ ] Email validation shows errors
  - [ ] Can create new account
  - [ ] Token saved correctly

- [ ] **Login**
  - [ ] Can login with credentials
  - [ ] Error messages display
  - [ ] Loading state shows
  - [ ] Navigates to main app

### Phase 3: Main Weather Features
- [ ] **HomeView**
  - [ ] Location permission works
  - [ ] Current weather loads
  - [ ] Temperature displays
  - [ ] Weather details show (6 metrics)
  - [ ] Dynamic background changes with weather
  - [ ] Pull-to-refresh works
  - [ ] Navigation to other screens works

- [ ] **Hourly Forecast**
  - [ ] Temperature chart displays
  - [ ] 48-hour list loads
  - [ ] Weather icons show correctly
  - [ ] Time labels accurate

- [ ] **Daily Forecast**
  - [ ] 7-day forecast shows (free)
  - [ ] Temperature ranges display
  - [ ] Precipitation probability shows
  - [ ] Premium upsell visible (free users)

- [ ] **Air Quality**
  - [ ] AQI gauge displays
  - [ ] Pollutant breakdown shows
  - [ ] Color coding correct
  - [ ] Health advice displays

### Phase 4: AI Features (Premium)
- [ ] **AI Insights Paywall**
  - [ ] Shows for free users
  - [ ] Premium badge visible
  - [ ] Can navigate to paywall

- [ ] **PaywallView**
  - [ ] Features list displays
  - [ ] Subscription plans show
  - [ ] Prices correct
  - [ ] Can select plan
  - [ ] Trial CTA visible
  - [ ] Terms/Privacy links work

- [ ] **AI Insights (after premium)**
  - [ ] Tab navigation works
  - [ ] Daily summary loads
  - [ ] Outfit recommendations show
  - [ ] Activity suggestions display
  - [ ] Health insights appear

- [ ] **Travel Risk**
  - [ ] Input field works
  - [ ] Risk analysis loads
  - [ ] Gauge displays correctly
  - [ ] Risk factors show
  - [ ] Recommendations appear

### Phase 5: Settings & Profile
- [ ] **Settings**
  - [ ] Profile displays correctly
  - [ ] Subscription status shows
  - [ ] Preferences navigation works
  - [ ] Can sign out

- [ ] **Preferences**
  - [ ] Unit toggles work
  - [ ] Display settings save

- [ ] **Notifications**
  - [ ] Toggle switches work
  - [ ] Settings persist

### Phase 6: Edge Cases & Errors
- [ ] **No Internet**
  - [ ] Graceful error messages
  - [ ] No crashes

- [ ] **Invalid Credentials**
  - [ ] Error displayed correctly
  - [ ] Can retry

- [ ] **Location Denied**
  - [ ] App handles gracefully
  - [ ] Can manually enter location

- [ ] **AI API Errors**
  - [ ] Fallback responses work
  - [ ] Error states shown

---

## 🐛 Known Issues to Check

### Backend Integration
- [ ] API base URL correct (localhost:8000)
- [ ] CORS configured for iOS
- [ ] Auth tokens working
- [ ] Premium features gated properly

### UI/UX
- [ ] No layout issues on different devices
- [ ] Dark mode works
- [ ] Animations smooth
- [ ] Loading states appropriate

### Performance
- [ ] Image loading efficient
- [ ] No memory leaks
- [ ] Scroll performance good
- [ ] API calls optimized

---

## 📝 Test Results Log

### Build Status
- Date/Time: ___________
- Build Success: ☐ Yes ☐ No
- Warnings: ___________
- Errors: ___________

### Critical Bugs Found
1. ___________
2. ___________
3. ___________

### Minor Issues Found
1. ___________
2. ___________
3. ___________

### Performance Notes
- Launch time: ___________
- Memory usage: ___________
- API response times: ___________

---

## ✅ Sign-Off Criteria

**Ready for TestFlight** when:
- [x] All Phase 1-3 tests pass
- [x] No critical bugs
- [x] Performance acceptable
- [x] Auth flow complete
- [x] Weather features work

**Ready for App Store** when:
- [x] All phases pass
- [x] No crashes
- [x] Premium features work
- [x] Settings functional
- [x] 10+ beta testers approved
- [x] App icon added
- [x] Screenshots prepared
