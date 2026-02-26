# iOS Widgets Setup Guide

## Prerequisites
- Xcode 15+
- iOS 17.0+ target

## Step 1: Add Widget Extension Target

1. In Xcode, go to **File → New → Target**
2. Select **Widget Extension**
3. Name it `ClimaAIWidget`
4. Uncheck "Include Configuration App Intent" (we use StaticConfiguration)
5. Click **Finish**

## Step 2: Configure App Group

Both the main app and widget need to share data via App Groups.

### Main App Target:
1. Select **ClimaAI** target
2. Go to **Signing & Capabilities**
3. Click **+ Capability → App Groups**
4. Add group: `group.com.climaai.shared`

### Widget Target:
1. Select **ClimaAIWidget** target  
2. Go to **Signing & Capabilities**
3. Click **+ Capability → App Groups**
4. Add the same group: `group.com.climaai.shared`

## Step 3: Add Widget Files

Copy or add these files to your ClimaAIWidget target:

```
ClimaAIWidget/
├── ClimaAIWidgetBundle.swift    (main entry point)
├── WidgetModels.swift           (shared data models)
├── SmallWeatherWidget.swift     (small widget)
├── MediumWeatherWidget.swift    (medium widget)
├── LargeWeatherWidget.swift     (large widget)
├── LockScreenWeatherWidget.swift (iOS 16+ lock screen)
└── Info.plist
```

## Step 4: Add Shared Models to Main App

Copy `WidgetModels.swift` to the main app's Models folder, or create a **Shared Framework** containing:
- `WidgetWeatherData`
- `WidgetHourlyData`  
- `WidgetDataManager`

## Step 5: Update Bundle Identifier

Set the widget bundle ID to: `com.climaai.app.ClimaAIWidget`

## Step 6: Build & Test

1. Build the main app
2. Run on device or simulator
3. Long press home screen → Add Widget → ClimaAI
4. Select widget size

## Widget Sizes

| Widget | Family | Description |
|--------|--------|-------------|
| Small | systemSmall | Temp + icon + location |
| Medium | systemMedium | Temp + 4-hour forecast |
| Large | systemLarge | Full day + 6-hour forecast + AI insight |
| Lock Screen | accessoryCircular/Rectangular/Inline | iOS 16+ compact views |

## Troubleshooting

### Widget shows placeholder data
- Ensure App Group ID matches exactly
- Verify WidgetDataManager is saving data
- Check that WidgetCenter.shared.reloadAllTimelines() is called

### Widget not appearing in gallery
- Clean build folder (Cmd+Shift+K)
- Delete app from simulator and reinstall
- Restart simulator

### "Failed to get app container" error
- App Group capability not properly configured
- Bundle ID mismatch with entitlements
