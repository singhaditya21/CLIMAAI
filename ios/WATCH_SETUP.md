# Apple Watch App Setup Guide

## Prerequisites
- Xcode 15+
- watchOS 10.0+ target
- Paired Apple Watch or Simulator

## Step 1: Add Watch Target

1. In Xcode, go to **File → New → Target**
2. Select **watchOS → App**
3. Name it `ClimaAIWatch`
4. Set minimum deployment to **watchOS 10.0**
5. Uncheck "Include Notification Scene"
6. Click **Finish**

## Step 2: Configure App Group

Both iPhone and Watch apps need the same App Group for data sharing.

### Main App Target:
1. Select **ClimaAI** target
2. Go to **Signing & Capabilities**
3. Ensure App Group `group.com.climaai.shared` exists

### Watch Target:
1. Select **ClimaAIWatch** target
2. Go to **Signing & Capabilities**
3. Click **+ Capability → App Groups**
4. Add the same group: `group.com.climaai.shared`

## Step 3: Add Watch Files

Add these Swift files to your ClimaAIWatch target:

```
ClimaAIWatch/
├── ClimaAIWatchApp.swift    (app entry point)
├── ContentView.swift         (main weather view)
├── ComplicationViews.swift   (watch face complications)
└── Info.plist
```

## Step 4: Configure Info.plist

The Watch app needs basic configuration:

```xml
<key>WKCompanionAppBundleIdentifier</key>
<string>com.climaai.app</string>

<key>WKRunsIndependentlyOfCompanionApp</key>
<true/>
```

## Step 5: Add Complications (Optional)

To enable watch face complications:

1. Create a Widget extension target for watchOS
2. Add complication views for different families:
   - `accessoryCircular` - Small round complication
   - `accessoryRectangular` - Wide info complication
   - `accessoryCorner` - Corner gauge complication
   - `accessoryInline` - Single line text

## Data Flow

```
iPhone App (ClimaAI)
    ↓ (saves to App Group)
App Group: group.com.climaai.shared
    ↓ (reads from App Group)
Watch App (ClimaAIWatch)
```

The iPhone app saves weather data to the shared App Group container.
The Watch app reads this data on launch.

## Build & Test

1. Select **ClimaAIWatch** scheme
2. Choose Apple Watch simulator
3. Build and run (⌘R)
4. Verify weather data displays correctly

## Troubleshooting

### Watch shows placeholder data
- Ensure main app has fetched weather recently
- Verify App Group ID matches exactly
- Check that `weather_data.json` exists in App Group container

### Complications not appearing
- Ensure Widget extension is properly configured
- Clean build folder and rebuild
- Restart Watch simulator

### "Watch app not installed" error
- Make sure Watch simulator is paired
- Delete app from both iPhone and Watch simulators
- Reinstall from Xcode
