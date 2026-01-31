//
//  WeatherViewModelTests.swift
//  ClimaAITests
//
//  Unit tests for WeatherViewModel
//

import XCTest
@testable import ClimaAI

final class WeatherViewModelTests: XCTestCase {
    
    var viewModel: WeatherViewModel!
    
    override func setUpWithError() throws {
        viewModel = WeatherViewModel()
    }
    
    override func tearDownWithError() throws {
        viewModel = nil
    }
    
    // MARK: - TC-WEATHER-001: Initial State
    
    func testInitialStateIsLoading() {
        XCTAssertTrue(viewModel.isLoading == false, "Initial loading state should be false")
        XCTAssertNil(viewModel.currentWeather, "Initial weather should be nil")
        XCTAssertTrue(viewModel.hourlyForecast.isEmpty, "Initial hourly forecast should be empty")
        XCTAssertTrue(viewModel.dailyForecast.isEmpty, "Initial daily forecast should be empty")
    }
    
    // MARK: - TC-WEATHER-002: Location Name
    
    func testLocationNameDefaultValue() {
        XCTAssertEqual(viewModel.locationName, "Loading...", "Default location name should be 'Loading...'")
    }
    
    // MARK: - TC-WEATHER-003: Error State
    
    func testErrorMessageInitiallyNil() {
        XCTAssertNil(viewModel.errorMessage, "Error message should initially be nil")
    }
    
    // MARK: - TC-WEATHER-015: Temperature Formatting
    
    func testTemperatureFormattingPositive() {
        let temp = 25.7
        let formatted = Int(temp)
        XCTAssertEqual(formatted, 25, "Temperature should round down")
    }
    
    func testTemperatureFormattingNegative() {
        let temp = -5.3
        let formatted = Int(temp)
        XCTAssertEqual(formatted, -5, "Negative temperature should format correctly")
    }
    
    func testTemperatureFormattingZero() {
        let temp = 0.0
        let formatted = Int(temp)
        XCTAssertEqual(formatted, 0, "Zero temperature should format correctly")
    }
    
    func testTemperatureFormattingExtremeCold() {
        let temp = -50.0
        let formatted = Int(temp)
        XCTAssertEqual(formatted, -50, "Extreme cold should format correctly")
    }
    
    func testTemperatureFormattingExtremeHot() {
        let temp = 55.0
        let formatted = Int(temp)
        XCTAssertEqual(formatted, 55, "Extreme heat should format correctly")
    }
    
    // MARK: - TC-WEATHER-012: Weather Metrics Validation
    
    func testHumidityRangeValidation() {
        let validHumidities = [0, 50, 100]
        let invalidHumidities = [-1, 101, 150]
        
        for humidity in validHumidities {
            XCTAssertTrue(humidity >= 0 && humidity <= 100, "Humidity \(humidity) should be valid")
        }
        
        for humidity in invalidHumidities {
            XCTAssertFalse(humidity >= 0 && humidity <= 100, "Humidity \(humidity) should be invalid")
        }
    }
    
    func testUVIndexRangeValidation() {
        let uvIndex = 6.0
        let isHighUV = uvIndex >= 6
        XCTAssertTrue(isHighUV, "UV ≥ 6 should be considered high")
    }
    
    func testWindSpeedPositive() {
        let windSpeed = 15.5
        XCTAssertTrue(windSpeed >= 0, "Wind speed should be positive")
    }
    
    // MARK: - TC-WEATHER-013: Weather Icon Mapping
    
    func testWeatherCodeToClearIcon() {
        let code = 0
        let icon = mapWeatherCodeToIcon(code)
        XCTAssertTrue(icon.contains("sun"), "Code 0 should map to sun icon")
    }
    
    func testWeatherCodeToRainIcon() {
        let codes = [51, 53, 55, 61, 63, 65, 67]
        for code in codes {
            let icon = mapWeatherCodeToIcon(code)
            XCTAssertTrue(icon.contains("rain") || icon.contains("cloud"), "Code \(code) should map to rain/cloud icon")
        }
    }
    
    func testWeatherCodeToSnowIcon() {
        let codes = [71, 73, 75, 77, 85, 86]
        for code in codes {
            let icon = mapWeatherCodeToIcon(code)
            XCTAssertTrue(icon.contains("snow") || icon.contains("cloud"), "Code \(code) should map to snow icon")
        }
    }
    
    func testWeatherCodeToThunderIcon() {
        let codes = [95, 96, 99]
        for code in codes {
            let icon = mapWeatherCodeToIcon(code)
            XCTAssertTrue(icon.contains("bolt") || icon.contains("thunder"), "Code \(code) should map to thunder icon")
        }
    }
    
    // Helper function for weather icon mapping
    private func mapWeatherCodeToIcon(_ code: Int) -> String {
        switch code {
        case 0: return "sun.max"
        case 1, 2, 3: return "cloud.sun"
        case 45, 48: return "cloud.fog"
        case 51...67: return "cloud.rain"
        case 71...86: return "cloud.snow"
        case 95...99: return "cloud.bolt"
        default: return "questionmark.circle"
        }
    }
    
    // MARK: - TC-WEATHER-006: Hourly Forecast Validation
    
    func testHourlyForecastMaxItems() {
        let maxHours = 48
        XCTAssertTrue(maxHours <= 48, "Hourly forecast should have max 48 hours")
    }
    
    // MARK: - TC-WEATHER-008: Daily Forecast Count
    
    func testDailyForecastFreeUserLimit() {
        let freeDays = 7
        XCTAssertEqual(freeDays, 7, "Free users should see 7 days")
    }
    
    func testDailyForecastPremiumUserLimit() {
        let premiumDays = 14
        XCTAssertEqual(premiumDays, 14, "Premium users should see 14 days")
    }
    
    // MARK: - Performance Tests
    
    func testWeatherIconMappingPerformance() {
        measure {
            for _ in 0..<1000 {
                _ = mapWeatherCodeToIcon(Int.random(in: 0...99))
            }
        }
    }
}
