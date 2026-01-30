//
//  WidgetModels.swift
//  ClimaAIWidget
//
//  Shared data models for widgets
//

import Foundation

// MARK: - Widget Data Models

struct WidgetWeatherData: Codable {
    let temperature: Double
    let feelsLike: Double
    let weatherCode: Int
    let weatherDescription: String
    let humidity: Int
    let windSpeed: Double
    let locationName: String
    let sunrise: String?
    let sunset: String?
    let hourlyForecast: [WidgetHourlyData]?
    let lastUpdated: Date
    
    var weatherIcon: String {
        switch weatherCode {
        case 0: return "sun.max.fill"
        case 1, 2, 3: return "cloud.sun.fill"
        case 45, 48: return "cloud.fog.fill"
        case 51, 53, 55, 56, 57: return "cloud.drizzle.fill"
        case 61, 63, 65, 66, 67: return "cloud.rain.fill"
        case 71, 73, 75, 77: return "cloud.snow.fill"
        case 80, 81, 82: return "cloud.heavyrain.fill"
        case 85, 86: return "cloud.snow.fill"
        case 95, 96, 99: return "cloud.bolt.rain.fill"
        default: return "cloud.fill"
        }
    }
    
    static var placeholder: WidgetWeatherData {
        WidgetWeatherData(
            temperature: 22,
            feelsLike: 24,
            weatherCode: 0,
            weatherDescription: "Clear",
            humidity: 65,
            windSpeed: 12,
            locationName: "San Francisco",
            sunrise: "06:45",
            sunset: "18:30",
            hourlyForecast: [
                WidgetHourlyData(hour: "9AM", temperature: 18, weatherCode: 0),
                WidgetHourlyData(hour: "12PM", temperature: 22, weatherCode: 1),
                WidgetHourlyData(hour: "3PM", temperature: 25, weatherCode: 1),
                WidgetHourlyData(hour: "6PM", temperature: 21, weatherCode: 0),
            ],
            lastUpdated: Date()
        )
    }
}

struct WidgetHourlyData: Codable {
    let hour: String
    let temperature: Double
    let weatherCode: Int
    
    var weatherIcon: String {
        switch weatherCode {
        case 0: return "sun.max.fill"
        case 1, 2, 3: return "cloud.sun.fill"
        case 45, 48: return "cloud.fog.fill"
        case 51, 53, 55, 56, 57: return "cloud.drizzle.fill"
        case 61, 63, 65, 66, 67: return "cloud.rain.fill"
        case 71, 73, 75, 77: return "cloud.snow.fill"
        case 80, 81, 82: return "cloud.heavyrain.fill"
        case 85, 86: return "cloud.snow.fill"
        case 95, 96, 99: return "cloud.bolt.rain.fill"
        default: return "cloud.fill"
        }
    }
}

// MARK: - App Group Storage

struct WidgetDataManager {
    static let appGroupIdentifier = "group.com.climaai.shared"
    static let weatherDataKey = "currentWeatherData"
    
    static func saveWeatherData(_ data: WidgetWeatherData) {
        guard let userDefaults = UserDefaults(suiteName: appGroupIdentifier) else { return }
        
        if let encoded = try? JSONEncoder().encode(data) {
            userDefaults.set(encoded, forKey: weatherDataKey)
        }
    }
    
    static func loadWeatherData() -> WidgetWeatherData? {
        guard let userDefaults = UserDefaults(suiteName: appGroupIdentifier),
              let data = userDefaults.data(forKey: weatherDataKey),
              let weatherData = try? JSONDecoder().decode(WidgetWeatherData.self, from: data) else {
            return nil
        }
        return weatherData
    }
}
