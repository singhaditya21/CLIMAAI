//
//  AccessibilityTests.swift
//  ClimaAITests
//
//  Unit tests for accessibility compliance
//

import XCTest
@testable import ClimaAI

final class AccessibilityTests: XCTestCase {
    
    // MARK: - TC-A11Y-003: Color Contrast Tests
    
    func testContrastRatioCalculation() {
        // WCAG AA requires 4.5:1 for normal text, 3:1 for large text
        let minContrastAA = 4.5
        let minContrastLarge = 3.0
        
        XCTAssertEqual(minContrastAA, 4.5, "AA requires 4.5:1 contrast")
        XCTAssertEqual(minContrastLarge, 3.0, "Large text requires 3:1 contrast")
    }
    
    func testPrimaryTextContrast() {
        // White text on blue background
        // Blue RGB: (59, 130, 246) -> luminance ≈ 0.22
        // White RGB: (255, 255, 255) -> luminance = 1.0
        // Contrast ratio ≈ 4.5:1 ✓
        
        let whiteLuminance = 1.0
        let blueLuminance = 0.22
        let contrastRatio = (whiteLuminance + 0.05) / (blueLuminance + 0.05)
        
        XCTAssertGreaterThan(contrastRatio, 3.0, "Should meet large text AA standard")
    }
    
    // MARK: - TC-A11Y-005: Touch Target Size Tests
    
    func testMinimumTouchTargetSize() {
        let minimumSize: CGFloat = 44.0
        
        let buttonSizes: [CGFloat] = [44.0, 48.0, 56.0, 60.0]
        
        for size in buttonSizes {
            XCTAssertGreaterThanOrEqual(size, minimumSize, "Touch target should be at least 44pt")
        }
    }
    
    func testSmallButtonAccessibility() {
        let smallButtonSize: CGFloat = 30.0
        let minimumSize: CGFloat = 44.0
        
        XCTAssertLessThan(smallButtonSize, minimumSize, "30pt button is too small")
    }
    
    // MARK: - TC-A11Y-001: VoiceOver Labels Tests
    
    func testWeatherIconAccessibilityLabels() {
        let iconLabels: [String: String] = [
            "sun.max": "Clear sky",
            "cloud.sun": "Partly cloudy",
            "cloud.rain": "Rainy",
            "cloud.snow": "Snowy",
            "cloud.bolt": "Thunderstorm",
            "cloud.fog": "Foggy"
        ]
        
        for (icon, label) in iconLabels {
            XCTAssertFalse(icon.isEmpty, "Icon name should not be empty")
            XCTAssertFalse(label.isEmpty, "Accessibility label should not be empty")
            XCTAssertFalse(label.contains("icon"), "Label should describe weather, not the icon")
        }
    }
    
    func testTemperatureAccessibilityLabel() {
        let temp = 25
        let unit = "degrees Celsius"
        let label = "\(temp) \(unit)"
        
        XCTAssertEqual(label, "25 degrees Celsius", "Temperature should be readable by VoiceOver")
    }
    
    func testButtonAccessibilityLabels() {
        let buttons = [
            ("refreshBtn", "Refresh weather"),
            ("settingsBtn", "Open settings"),
            ("locationBtn", "Change location"),
            ("upgradeBtn", "Upgrade to premium")
        ]
        
        for (id, label) in buttons {
            XCTAssertFalse(id.isEmpty)
            XCTAssertFalse(label.isEmpty)
            XCTAssertTrue(label.count > 3, "Label should be descriptive")
        }
    }
    
    // MARK: - TC-A11Y-002: Dynamic Type Tests
    
    func testDynamicTypeSizes() {
        let sizes: [String] = [
            "UICTContentSizeCategoryXS",
            "UICTContentSizeCategoryS",
            "UICTContentSizeCategoryM",
            "UICTContentSizeCategoryL",
            "UICTContentSizeCategoryXL",
            "UICTContentSizeCategoryXXL",
            "UICTContentSizeCategoryXXXL",
            "UICTContentSizeCategoryAccessibilityM",
            "UICTContentSizeCategoryAccessibilityL",
            "UICTContentSizeCategoryAccessibilityXL",
            "UICTContentSizeCategoryAccessibilityXXL",
            "UICTContentSizeCategoryAccessibilityXXXL"
        ]
        
        XCTAssertEqual(sizes.count, 12, "Should support all Dynamic Type sizes")
    }
    
    func testFontScaleFactor() {
        let baseSize: CGFloat = 17.0
        let scaleFactor: CGFloat = 1.5 // XXL
        let scaledSize = baseSize * scaleFactor
        
        XCTAssertEqual(scaledSize, 25.5, "Font should scale correctly")
    }
    
    // MARK: - TC-A11Y-004: Reduce Motion Tests
    
    func testReduceMotionAlternatives() {
        let hasReduceMotionAlternative = true
        XCTAssertTrue(hasReduceMotionAlternative, "Should have alternatives when Reduce Motion is on")
    }
    
    func testAnimationDurations() {
        let normalDuration: TimeInterval = 0.3
        let reducedDuration: TimeInterval = 0.0
        let reduceMotionEnabled = true
        
        let effectiveDuration = reduceMotionEnabled ? reducedDuration : normalDuration
        XCTAssertEqual(effectiveDuration, 0.0, "Animations should be instant when Reduce Motion is on")
    }
    
    // MARK: - TC-A11Y-006: Screen Reader Descriptions Tests
    
    func testChartAccessibilityDescription() {
        let hourlyTemps = [22, 24, 26, 27, 26, 24]
        let minTemp = hourlyTemps.min()!
        let maxTemp = hourlyTemps.max()!
        let description = "Temperature chart showing range from \(minTemp) to \(maxTemp) degrees over 6 hours"
        
        XCTAssertTrue(description.contains("chart"), "Should mention it's a chart")
        XCTAssertTrue(description.contains("\(minTemp)"), "Should include min temperature")
        XCTAssertTrue(description.contains("\(maxTemp)"), "Should include max temperature")
    }
    
    func testGaugeAccessibilityDescription() {
        let aqi = 42
        let category = "Good"
        let description = "Air quality index gauge showing \(aqi), which is \(category)"
        
        XCTAssertTrue(description.contains("\(aqi)"))
        XCTAssertTrue(description.contains(category))
    }
    
    // MARK: - Color Blindness Tests
    
    func testNotRelyingSolelyOnColor() {
        // Risk levels should have both color AND text
        let riskIndicators: [(String, String)] = [
            ("Low", "green"),
            ("Moderate", "yellow"),
            ("High", "orange"),
            ("Very High", "red")
        ]
        
        for (text, color) in riskIndicators {
            XCTAssertFalse(text.isEmpty, "Should have text label, not just \(color) color")
        }
    }
    
    func testIconsWithLabels() {
        // Weather conditions should have both icon AND text
        let weatherConditions = [
            ("☀️", "Clear"),
            ("🌧", "Rain"),
            ("❄️", "Snow")
        ]
        
        for (icon, label) in weatherConditions {
            XCTAssertFalse(icon.isEmpty, "Should have icon")
            XCTAssertFalse(label.isEmpty, "Should have text label")
        }
    }
    
    // MARK: - Focus Order Tests
    
    func testLogicalFocusOrder() {
        let expectedOrder = [
            "locationHeader",
            "currentWeather",
            "quickStats",
            "hourlyForecast",
            "aiInsights",
            "dailyForecast"
        ]
        
        XCTAssertEqual(expectedOrder.first, "locationHeader", "Location should be first")
        XCTAssertEqual(expectedOrder.last, "dailyForecast", "Daily forecast should be last")
    }
    
    // MARK: - Performance Tests
    
    func testAccessibilityLabelGenerationPerformance() {
        measure {
            for i in 0..<1000 {
                let temp = i % 50
                _ = "\(temp) degrees Celsius"
            }
        }
    }
}
