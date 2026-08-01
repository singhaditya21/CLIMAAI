# ClimaAI iOS — Xcode project

> **Requires Xcode 16 or newer.** The generated project uses `objectVersion = 77`,
> the Xcode 16 pbxproj format. Xcode 15 reports "Unable to read project" and will
> not open it — confirmed on a CI runner with Xcode 15.4.

`ClimaAI.xcodeproj` is committed, so you can clone and open:

```bash
open ios/ClimaAI.xcodeproj
```

This replaces the previous version of this guide, which walked through building
the project by hand in Xcode — that is no longer necessary.

## Targets

| Target | Type | Bundle identifier |
| :--- | :--- | :--- |
| `ClimaAI` | iOS app (iOS 16+) | `com.climaai.app` |
| `ClimaAIWidget` | WidgetKit extension | `com.climaai.app.widget` |
| `ClimaAIWatch` | watchOS app (watchOS 9+) | `com.climaai.app.watchkitapp` |
| `ClimaAITests` | Unit tests | `com.climaai.app.tests` |

The `ClimaAI` scheme builds the app with its widget and watch app embedded, and
runs `ClimaAITests` on test.

## Before it will build and run on a device

1. **Signing team.** Set `DEVELOPMENT_TEAM` in `project.yml`, or pick a team in
   Xcode's Signing & Capabilities pane for each of the four targets.
2. **Bundle identifiers.** `com.climaai.*` is a placeholder. Change the prefix in
   `project.yml` to something you own and regenerate.
3. **Capabilities.** The app needs Location and In-App Purchase; the widget and
   watch app need an App Group if you want them to share cached weather.
4. **App icons.** `Assets.xcassets/AppIcon.appiconset` exists but has no image.
   Drop a 1024×1024 PNG in before submitting to App Store Connect.

## Changing the project

The project is generated from [`project.yml`](project.yml) by
[XcodeGen](https://github.com/yonaskolb/XcodeGen):

```bash
brew install xcodegen
cd ios && xcodegen generate
```

Adding or removing **source files** needs no spec change — sources are globbed by
directory, so a regenerate picks them up.

Changing **targets, build settings, or which target a file belongs to** should be
done in `project.yml` and regenerated. Editing those in Xcode works until the
next `xcodegen generate`, which will discard them.

## What has and has not been verified

**Not built against the iOS SDK.** That needs full Xcode; only the Command Line
Tools are installed here, which ship no iOS platform SDK.

**Type-checked against the macOS SDK**, per target, compiled as whole modules
(SwiftUI, Foundation and WidgetKit exist on both platforms, so most code
resolves). Results:

| Target | Files | Outcome |
| :--- | :--- | :--- |
| `ClimaAI` | 30 | One error: `no such module 'UIKit'` — iOS-only, resolves on iOS |
| `ClimaAIWidget` | 6 | Only `#Preview` macro plugin missing (ships with Xcode) |
| `ClimaAIWatch` | 3 | `#Preview`, plus `widgetLabel` which is watchOS-only |

All 39 files also parse cleanly. **No genuine code defects were found** — every
error above is a macOS-versus-iOS platform artifact or an Xcode-only macro
plugin. The single UIKit use is `UIApplication.shared.registerForRemoteNotifications()`
in `Services/NotificationService.swift`, which is correct iOS API.

This is meaningful evidence but not a guarantee: iOS-only API usage is unchecked,
and linking, resources and code signing are untested. Expect *some* first-build
friction, just less than "never compiled" would suggest.

`ClimaAITests` still has not run — it needs a simulator, so full Xcode.

## Known rough edges
- `ClimaAIWidget/WidgetModels.swift` is a byte-identical copy of
  `ClimaAI/Models/WidgetModels.swift`. They are separate modules so this compiles
  fine, but the two have to be kept in step by hand. Worth collapsing into a
  shared framework target.
- `ClimaAITests/run_tests.swift` is a standalone `swift` script with top-level
  code, deliberately excluded from the test bundle — it cannot compile inside
  one. Run it directly with `swift ClimaAITests/run_tests.swift`.
