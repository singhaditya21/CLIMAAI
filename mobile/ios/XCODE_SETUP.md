# ClimaAI iOS - Xcode Project Setup Guide

## 🎯 Step-by-Step Instructions

### Step 1: Create New Xcode Project

1. **Open Xcode** (from Applications or Spotlight)

2. **Create New Project**:
   - Click "Create New Project" or File → New → Project
   - Choose **iOS** tab
   - Select **App** template
   - Click **Next**

3. **Configure Project**:
   - **Product Name**: `ClimaAI`
   - **Team**: Select your team (or None for now)
   - **Organization Identifier**: `com.yourname` (e.g., `com.aditya`)
   - **Bundle Identifier**: Will auto-generate as `com.yourname.ClimaAI`
   - **Interface**: **SwiftUI** ✅
   - **Language**: **Swift** ✅
   - **Storage**: None
   - **Include Tests**: ✅ (optional)
   - Click **Next**

4. **Save Location**:
   - Navigate to: `/Users/adityasingh/clima-ai/mobile/ios`
   - **IMPORTANT**: Uncheck "Create Git repository" (we already have one)
   - Click **Create**

---

### Step 2: Delete Default Files

Xcode created some default files we don't need:

1. In Project Navigator (left sidebar), **delete** these files:
   - `ContentView.swift` (we'll replace it)
   - `ClimaAIApp.swift` (we'll replace it)
   - Right-click → Delete → **Move to Trash**

---

### Step 3: Add Our Files to Project

#### 3A: Add ClimaAIApp.swift
1. Right-click on `ClimaAI` folder (blue icon) in Project Navigator
2. **Add Files to "ClimaAI"...**
3. Navigate to: `/Users/adityasingh/clima-ai/mobile/ios/ClimaAI/`
4. Select `ClimaAIApp.swift`
5. ✅ Check "Copy items if needed"
6. ✅ Check "Add to targets: ClimaAI"
7. Click **Add**

#### 3B: Add ViewModels Folder
1. Right-click on `ClimaAI` folder
2. **Add Files to "ClimaAI"...**
3. Navigate to: `/Users/adityasingh/clima-ai/mobile/ios/ClimaAI/`
4. Select the **`ViewModels`** folder
5. ✅ Check "Copy items if needed"
6. ✅ Check "Create groups" (not folder references)
7. ✅ Check "Add to targets: ClimaAI"
8. Click **Add**

#### 3C: Add Views Folder
1. Right-click on `ClimaAI` folder
2. **Add Files to "ClimaAI"...**
3. Select the **`Views`** folder (contains Auth, Home, Weather, AI, Settings, Subscription)
4. ✅ Check "Copy items if needed"
5. ✅ Check "Create groups"
6. ✅ Check "Add to targets: ClimaAI"
7. Click **Add**

#### 3D: Add Existing Models and Services
If these files already exist from your previous work:
1. Find `Models.swift`, `APIClient.swift`, `LocationManager.swift`, `SubscriptionManager.swift`
2. Make sure they're in the project
3. If not, add them the same way

---

### Step 4: Replace Info.plist

1. In Xcode, find `Info.plist` in Project Navigator
2. Right-click → **Show in Finder**
3. **Delete** the existing `Info.plist` from Finder
4. **Copy** our `Info.plist` from `/Users/adityasingh/clima-ai/mobile/ios/ClimaAI/Info.plist`
5. **Paste** it into the same location
6. Back in Xcode, clean and rebuild: **⌘⇧K** then **⌘B**

---

### Step 5: Configure Project Settings

1. Click on **ClimaAI** (blue icon) at the top of Project Navigator
2. Select **ClimaAI** target (under TARGETS)
3. Go to **Signing & Capabilities** tab:
   - ✅ Check "Automatically manage signing"
   - Select your **Team** (sign in with Apple ID if needed)

4. Go to **General** tab:
   - **Minimum Deployments**: iOS 16.0 or later
   - **Supports**: iPhone only (or iPhone & iPad)

---

### Step 6: Add Required Frameworks

Our app uses some iOS frameworks. Verify they're linked:

1. Select **ClimaAI** target
2. Go to **General** → **Frameworks, Libraries, and Embedded Content**
3. Click **+** and add these if not present:
   - **StoreKit.framework**
   - **CoreLocation.framework**

(SwiftUI, Combine, Charts are automatic in iOS 16+)

---

### Step 7: Create StoreKit Configuration (for testing)

1. **File → New → File**
2. Search for "StoreKit"
3. Select **StoreKit Configuration File**
4. Name it: `Configuration.storekit`
5. **Save** in project root

6. In the StoreKit editor:
   - Click **+** → Add Subscription
   - **Reference Name**: Monthly Premium
   - **Product ID**: `com.climaai.premium.monthly`
   - **Price**: $4.99
   - **Subscription Duration**: 1 month
   - Click **+** → Add Introductory Offer
   - Type: Free, Duration: 7 days
   
   - Repeat for Annual:
   - **Product ID**: `com.climaai.premium.annual`
   - **Price**: $39.99
   - **Subscription Duration**: 1 year
   - Same 7-day trial

7. **Product → Scheme → Edit Scheme**
   - Go to **Run** → **Options**
   - **StoreKit Configuration**: Select `Configuration.storekit`

---

### Step 8: Update API Base URL

1. Open `APIClient.swift`
2. Find the `baseURL` property
3. Change it to:
   ```swift
   private let baseURL = "http://localhost:8000"
   ```

---

### Step 9: Build & Run!

1. Select simulator: **iPhone 14 Pro** (or any iPhone)
2. Press **⌘R** or click ▶️ **Run** button
3. Wait for build...
4. App should launch in simulator! 🎉

---

## ✅ Expected Result

When the app launches:
1. **Onboarding screen** appears (4 pages)
2. Location permission dialog shows
3. Can navigate to **Login screen**
4. Can register or login

---

## 🐛 Troubleshooting

### Build Errors

**"Cannot find type 'Weather' in scope"**
- Make sure `Models.swift` is in the project
- Check it's added to target

**"No such module 'StoreKit'"**
- Add StoreKit framework (Step 6)

**"Module compiled with Swift X.X cannot be imported"**
- Clean build folder: **⌘⇧K**
- Rebuild: **⌘B**

### Runtime Errors

**"Failed to connect to localhost:8000"**
- Backend not running
- Start Docker and run `./start.sh`

**Location permission doesn't work**
- Check `Info.plist` has location keys
- Reset simulator: Device → Erase All Content and Settings

---

## 📝 Quick Command Reference

```bash
# Open Xcode from terminal
open /Users/adityasingh/clima-ai/mobile/ios/ClimaAI.xcodeproj

# Or create it first, then:
cd /Users/adityasingh/clima-ai/mobile/ios
open .
# Then create project in Xcode GUI
```

---

## 🎯 You're Ready!

After following these steps:
- ✅ Xcode project created
- ✅ All files added
- ✅ Configuration complete
- ✅ Ready to run

**Press ⌘R and see your app come to life!** 🚀

---

## 📌 Project Structure (Final)

```
ClimaAI.xcodeproj/
ClimaAI/
├── ClimaAIApp.swift
├── Info.plist
├── ViewModels/
│   ├── AuthViewModel.swift
│   ├── WeatherViewModel.swift
│   ├── AIInsightsViewModel.swift
│   └── SubscriptionViewModel.swift
├── Views/
│   ├── ContentView.swift
│   ├── Auth/ (3 files)
│   ├── Home/ (1 file)
│   ├── Weather/ (3 files)
│   ├── AI/ (2 files)
│   ├── Subscription/ (1 file)
│   └── Settings/ (1 file)
├── Models.swift
├── Services/
│   ├── APIClient.swift
│   ├── LocationManager.swift
│   └── SubscriptionManager.swift
└── Configuration.storekit
```

**Need help with any step? Just ask!** 👋
