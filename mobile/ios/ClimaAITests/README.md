# ClimaAI iOS Test Suite

This directory contains XCTest unit tests for the ClimaAI iOS app.

## Test Files

| File | Tests | Coverage |
|------|-------|----------|
| `WeatherViewModelTests.swift` | 15 | Temperature formatting, weather codes, metrics |
| `AuthenticationTests.swift` | 12 | Email/password validation, tokens, rate limiting |
| `ModelsTests.swift` | 15 | JSON parsing, model decoding, edge cases |
| `APIClientTests.swift` | 14 | URL construction, HTTP codes, coordinates |
| `SubscriptionTests.swift` | 16 | Products, pricing, trial, expiry |
| `AccessibilityTests.swift` | 12 | VoiceOver, Dynamic Type, contrast |
| `SecurityTests.swift` | 14 | Token storage, HTTPS, input sanitization |

**Total: 98 unit tests**

## Running Tests

### Option 1: Xcode (Recommended)

1. Open Xcode project: `ClimaAI.xcodeproj`
2. Add test target if not exists:
   - File → New → Target → Unit Testing Bundle
   - Name: `ClimaAITests`
3. Add test files to target
4. Run tests: `⌘U` or Product → Test

### Option 2: Command Line (After Xcode Setup)

```bash
# Navigate to project
cd /Users/adityasingh/clima-ai/ios

# Run all tests
xcodebuild test \
  -scheme ClimaAI \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro'

# Run specific test class
xcodebuild test \
  -scheme ClimaAI \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' \
  -only-testing:ClimaAITests/WeatherViewModelTests
```

## Setup Instructions

### 1. Create Test Target in Xcode

1. Open your ClimaAI project in Xcode
2. File → New → Target
3. Select "Unit Testing Bundle"
4. Product Name: `ClimaAITests`
5. Click Finish

### 2. Add Test Files

1. Right-click `ClimaAITests` folder in Navigator
2. Add Files to "ClimaAITests"
3. Select all `.swift` files from this directory
4. Ensure "Add to targets: ClimaAITests" is checked

### 3. Import Main Target

Ensure each test file has:
```swift
@testable import ClimaAI
```

### 4. Run Tests

- Press `⌘U` to run all tests
- Click diamond icons in gutter to run individual tests

## Test Categories

### Unit Tests (These Files)
- Fast, isolated tests
- No network dependencies
- Mock data where needed

### UI Tests (To Be Added)
- XCUITest for user flows
- Accessibility testing
- Screenshot testing

### Integration Tests (To Be Added)
- API contract testing
- End-to-end flows

## Code Coverage

To enable code coverage:
1. Product → Scheme → Edit Scheme
2. Select "Test" 
3. Check "Gather coverage for: ClimaAI"
4. Run tests
5. View in Report Navigator (⌘9)

## Continuous Integration

For CI/CD (e.g., GitHub Actions):

```yaml
- name: Run iOS Tests
  run: |
    xcodebuild test \
      -scheme ClimaAI \
      -destination 'platform=iOS Simulator,name=iPhone 15 Pro' \
      -resultBundlePath TestResults.xcresult
```
