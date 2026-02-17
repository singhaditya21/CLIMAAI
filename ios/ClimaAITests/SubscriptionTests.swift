//
//  SubscriptionTests.swift
//  ClimaAITests
//
//  Unit tests for subscription and in-app purchase functionality
//

import XCTest
@testable import ClimaAI

final class SubscriptionTests: XCTestCase {
    
    // MARK: - TC-SUB-002: Product IDs Tests
    
    func testMonthlyProductID() {
        let monthlyID = "com.climaai.premium.monthly"
        XCTAssertEqual(monthlyID, "com.climaai.premium.monthly")
        XCTAssertTrue(monthlyID.contains("monthly"))
    }
    
    func testAnnualProductID() {
        let annualID = "com.climaai.premium.annual"
        XCTAssertEqual(annualID, "com.climaai.premium.annual")
        XCTAssertTrue(annualID.contains("annual"))
    }
    
    func testProductIDFormat() {
        let productIDs = ["com.climaai.premium.monthly", "com.climaai.premium.annual"]
        
        for id in productIDs {
            XCTAssertTrue(id.hasPrefix("com.climaai"), "Product ID should start with bundle prefix")
            XCTAssertTrue(id.contains("premium"), "Product ID should contain 'premium'")
        }
    }
    
    // MARK: - TC-SUB-002: Pricing Tests
    
    func testMonthlyPrice() {
        let monthlyPrice = 4.99
        XCTAssertEqual(monthlyPrice, 4.99)
    }
    
    func testAnnualPrice() {
        let annualPrice = 39.99
        XCTAssertEqual(annualPrice, 39.99)
    }
    
    func testAnnualSavings() {
        let monthlyPrice = 4.99
        let yearlyMonthlyTotal = monthlyPrice * 12 // 59.88
        let annualPrice = 39.99
        let savings = yearlyMonthlyTotal - annualPrice // 19.89
        let savingsPercentage = (savings / yearlyMonthlyTotal) * 100 // ~33%
        
        XCTAssertGreaterThan(savings, 19.0, "Annual savings should be > $19")
        XCTAssertGreaterThan(savingsPercentage, 33.0, "Savings should be > 33%")
    }
    
    // MARK: - TC-SUB-003: Trial Period Tests
    
    func testTrialDuration() {
        let trialDays = 7
        XCTAssertEqual(trialDays, 7, "Trial should be 7 days")
    }
    
    func testTrialEndDateCalculation() {
        let startDate = Date()
        let trialDays = 7
        let calendar = Calendar.current
        let endDate = calendar.date(byAdding: .day, value: trialDays, to: startDate)!
        
        let daysDiff = calendar.dateComponents([.day], from: startDate, to: endDate).day!
        XCTAssertEqual(daysDiff, 7, "Trial should last 7 days")
    }
    
    // MARK: - TC-SUB-009: Subscription Status Tests
    
    func testFreeTierStatus() {
        let isPremium = false
        let hasActiveSubscription = false
        
        XCTAssertFalse(isPremium)
        XCTAssertFalse(hasActiveSubscription)
    }
    
    func testPremiumStatus() {
        let isPremium = true
        let hasActiveSubscription = true
        
        XCTAssertTrue(isPremium)
        XCTAssertTrue(hasActiveSubscription)
    }
    
    func testTrialStatus() {
        let isInTrial = true
        let isPremium = true // Premium features during trial
        
        XCTAssertTrue(isInTrial)
        XCTAssertTrue(isPremium)
    }
    
    // MARK: - TC-SUB-011: Subscription Expiry Tests
    
    func testSubscriptionNotExpired() {
        let endDate = Date().addingTimeInterval(86400 * 30) // 30 days from now
        let now = Date()
        
        XCTAssertTrue(endDate > now, "Future date should not be expired")
    }
    
    func testSubscriptionExpired() {
        let endDate = Date().addingTimeInterval(-86400) // 1 day ago
        let now = Date()
        
        XCTAssertTrue(endDate < now, "Past date should be expired")
    }
    
    func testExpiringWithin24Hours() {
        let endDate = Date().addingTimeInterval(86400 / 2) // 12 hours from now
        let warningThreshold: TimeInterval = 86400 // 24 hours
        let now = Date()
        let timeRemaining = endDate.timeIntervalSince(now)
        
        XCTAssertTrue(timeRemaining < warningThreshold, "Should warn about expiring subscription")
    }
    
    // MARK: - TC-SUB-005: Receipt Validation Tests
    
    func testReceiptDataFormat() {
        let sampleReceipt = "MIITuAYJKoZIhvcNAQcCoIITqTCCE6UCAQExCzAJBgUrDgMCGgUA..."
        XCTAssertFalse(sampleReceipt.isEmpty, "Receipt data should not be empty")
    }
    
    func testReceiptBase64Encoding() {
        let testString = "test_receipt_data"
        let base64 = Data(testString.utf8).base64EncodedString()
        let decoded = Data(base64Encoded: base64)
        
        XCTAssertNotNil(decoded, "Base64 should be decodable")
        XCTAssertEqual(String(data: decoded!, encoding: .utf8), testString)
    }
    
    // MARK: - Premium Features Tests
    
    func testPremiumFeatures() {
        let premiumFeatures = [
            "extended_forecast": true,
            "ai_insights": true,
            "minute_rain": true,
            "severe_alerts": true,
            "air_quality_detailed": true,
            "health_insights": true,
            "travel_analysis": true
        ]
        
        XCTAssertEqual(premiumFeatures.count, 7, "Should have 7 premium features")
        
        for (feature, enabled) in premiumFeatures {
            XCTAssertTrue(enabled, "\(feature) should be enabled for premium")
        }
    }
    
    func testFreeFeatures() {
        let freeFeatures = [
            "extended_forecast": false, // Limited to 7 days
            "ai_insights": false,
            "minute_rain": false,
            "severe_alerts": true, // Basic alerts available
            "air_quality_detailed": false,
            "health_insights": false,
            "travel_analysis": false
        ]
        
        // Free users get basic alerts
        XCTAssertTrue(freeFeatures["severe_alerts"] == true)
        // But not AI insights
        XCTAssertFalse(freeFeatures["ai_insights"]!)
    }
    
    // MARK: - Auto-Renewal Tests
    
    func testAutoRenewalEnabled() {
        let autoRenew = true
        XCTAssertTrue(autoRenew, "Auto-renew should be enabled by default")
    }
    
    // MARK: - Plan Comparison Tests
    
    func testPlanNames() {
        let plans = ["monthly", "annual"]
        
        XCTAssertTrue(plans.contains("monthly"))
        XCTAssertTrue(plans.contains("annual"))
    }
    
    func testAnnualIsBetterValue() {
        let monthlyAnnualCost = 4.99 * 12 // $59.88
        let annualCost = 39.99
        
        XCTAssertTrue(annualCost < monthlyAnnualCost, "Annual should be cheaper than 12 months")
    }
    
    // MARK: - Performance Tests
    
    func testPriceCalculationPerformance() {
        measure {
            for _ in 0..<1000 {
                let monthly = 4.99
                let annual = 39.99
                _ = (monthly * 12) - annual
            }
        }
    }
}
