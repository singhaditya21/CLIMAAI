import Foundation

// MARK: - Weather Models

struct WeatherResponse: Codable {
    let current: CurrentWeather
    let hourly: [HourlyWeather]
    let daily: [DailyWeather]
    let airQuality: AirQuality?
    let location: Location
    let timezone: String
    let cached: Bool
    
    enum CodingKeys: String, CodingKey {
        case current, hourly, daily, location, timezone, cached
        case airQuality = "air_quality"
    }
}

struct CurrentWeather: Codable, Identifiable {
    var id: String { UUID().uuidString }
    let temperature: Double
    let feelsLike: Double
    let humidity: Int
    let windSpeed: Double
    let windDirection: Int
    let precipitation: Double
    let weatherCode: Int
    let weatherDescription: String
    let cloudCover: Int
    let pressure: Double
    let visibility: Double
    let uvIndex: Double
    let isDay: Bool
    let timestamp: Date
    
    enum CodingKeys: String, CodingKey {
        case temperature
        case feelsLike = "feels_like"
        case humidity
        case windSpeed = "wind_speed"
        case windDirection = "wind_direction"
        case precipitation
        case weatherCode = "weather_code"
        case weatherDescription = "weather_description"
        case cloudCover = "cloud_cover"
        case pressure, visibility
        case uvIndex = "uv_index"
        case isDay = "is_day"
        case timestamp
    }
}

struct HourlyWeather: Codable, Identifiable {
    var id: String { time.ISO8601Format() }
    let time: Date
    let temperature: Double
    let feelsLike: Double
    let precipitationProbability: Int
    let precipitation: Double
    let weatherCode: Int
    let weatherDescription: String
    let windSpeed: Double
    let windDirection: Int
    let humidity: Int
    let cloudCover: Int
    let uvIndex: Double
    
    enum CodingKeys: String, CodingKey {
        case time, temperature
        case feelsLike = "feels_like"
        case precipitationProbability = "precipitation_probability"
        case precipitation
        case weatherCode = "weather_code"
        case weatherDescription = "weather_description"
        case windSpeed = "wind_speed"
        case windDirection = "wind_direction"
        case humidity
        case cloudCover = "cloud_cover"
        case uvIndex = "uv_index"
    }
}

struct DailyWeather: Codable, Identifiable {
    var id: String { date }
    let date: String
    let temperatureMax: Double
    let temperatureMin: Double
    let sunrise: String
    let sunset: String
    let precipitationSum: Double
    let precipitationProbability: Int
    let weatherCode: Int
    let weatherDescription: String
    let windSpeedMax: Double
    let windDirection: Int
    let uvIndexMax: Double
    
    enum CodingKeys: String, CodingKey {
        case date
        case temperatureMax = "temperature_max"
        case temperatureMin = "temperature_min"
        case sunrise, sunset
        case precipitationSum = "precipitation_sum"
        case precipitationProbability = "precipitation_probability"
        case weatherCode = "weather_code"
        case weatherDescription = "weather_description"
        case windSpeedMax = "wind_speed_max"
        case windDirection = "wind_direction"
        case uvIndexMax = "uv_index_max"
    }
}

struct AirQuality: Codable {
    let aqi: Int
    let pm25: Double
    let pm10: Double
    let carbonMonoxide: Double
    let nitrogenDioxide: Double
    let ozone: Double
    let sulphurDioxide: Double
    let category: String
    let healthRecommendation: String
    
    enum CodingKeys: String, CodingKey {
        case aqi
        case pm25 = "pm2_5"
        case pm10
        case carbonMonoxide = "carbon_monoxide"
        case nitrogenDioxide = "nitrogen_dioxide"
        case ozone
        case sulphurDioxide = "sulphur_dioxide"
        case category
        case healthRecommendation = "health_recommendation"
    }
}

struct Location: Codable {
    let latitude: Double
    let longitude: Double
    let elevation: Double?
}

// MARK: - Nowcast Models (MinuteCast equivalent)

struct NowcastMinute: Codable, Identifiable {
    var id: String { time.ISO8601Format() }
    let time: Date
    let precipitation: Double      // mm
    let precipitationProbability: Int  // 0-100
    let intensity: String          // none, light, moderate, heavy
    let isPrecipitation: Bool
    
    enum CodingKeys: String, CodingKey {
        case time, precipitation
        case precipitationProbability = "precipitation_probability"
        case intensity
        case isPrecipitation = "is_precipitation"
    }
}

struct NowcastResponse: Codable {
    let location: Location
    let timezone: String
    let summary: String            // "Rain starting in 15 minutes"
    let precipitationStart: Date?  // When rain starts
    let precipitationEnd: Date?    // When rain stops
    let totalPrecipitation: Double // Total mm expected
    let minutes: [NowcastMinute]   // 120 minute-by-minute data points
    let lastUpdated: Date
    
    enum CodingKeys: String, CodingKey {
        case location, timezone, summary, minutes
        case precipitationStart = "precipitation_start"
        case precipitationEnd = "precipitation_end"
        case totalPrecipitation = "total_precipitation"
        case lastUpdated = "last_updated"
    }
}

// MARK: - AI Models

struct AIInsightsResponse: Codable {
    let dailySummary: DailySummary
    let outfit: OutfitRecommendation
    let activities: [ActivityRecommendation]
    let health: HealthInsight
    let travel: TravelRiskAnalysis?
    let cached: Bool
    
    enum CodingKeys: String, CodingKey {
        case dailySummary = "daily_summary"
        case outfit, activities, health, travel, cached
    }
}

struct DailySummary: Codable {
    let title: String
    let summary: String
    let highlights: [String]
    let warnings: [String]
    let bestTimes: [String: String]
    
    enum CodingKeys: String, CodingKey {
        case title, summary, highlights, warnings
        case bestTimes = "best_times"
    }
}

struct OutfitRecommendation: Codable {
    let summary: String
    let details: String
    let accessories: [String]
    let layerRecommendation: String
    
    enum CodingKeys: String, CodingKey {
        case summary, details, accessories
        case layerRecommendation = "layer_recommendation"
    }
}

struct ActivityRecommendation: Codable, Identifiable {
    var id: String { activity }
    let activity: String
    let suitabilityScore: Int
    let bestTime: String
    let reasoning: String
    let precautions: [String]
    
    enum CodingKeys: String, CodingKey {
        case activity
        case suitabilityScore = "suitability_score"
        case bestTime = "best_time"
        case reasoning, precautions
    }
}

struct HealthInsight: Codable {
    let uvRisk: RiskLevel
    let uvAdvice: String
    let airQualityRisk: RiskLevel
    let airQualityAdvice: String
    let heatStressRisk: RiskLevel
    let heatStressAdvice: String
    let allergyRisk: RiskLevel?
    let allergyAdvice: String?
    let generalHealthTips: [String]
    
    enum CodingKeys: String, CodingKey {
        case uvRisk = "uv_risk"
        case uvAdvice = "uv_advice"
        case airQualityRisk = "air_quality_risk"
        case airQualityAdvice = "air_quality_advice"
        case heatStressRisk = "heat_stress_risk"
        case heatStressAdvice = "heat_stress_advice"
        case allergyRisk = "allergy_risk"
        case allergyAdvice = "allergy_advice"
        case generalHealthTips = "general_health_tips"
    }
}

struct TravelRiskAnalysis: Codable {
    let overallRisk: RiskLevel
    let summary: String
    let severeWeatherAlerts: [String]
    let travelTips: [String]
    let bestTravelTimes: [String]
    let worstTravelTimes: [String]
    
    enum CodingKeys: String, CodingKey {
        case overallRisk = "overall_risk"
        case summary
        case severeWeatherAlerts = "severe_weather_alerts"
        case travelTips = "travel_tips"
        case bestTravelTimes = "best_travel_times"
        case worstTravelTimes = "worst_travel_times"
    }
}

enum RiskLevel: String, Codable {
    case low, moderate, high
    case veryHigh = "very_high"
    
    var color: String {
        switch self {
        case .low: return "green"
        case .moderate: return "yellow"
        case .high: return "orange"
        case .veryHigh: return "red"
        }
    }
}

// MARK: - User Models

struct User: Codable, Identifiable {
    let id: String
    let email: String
    let fullName: String?
    let isActive: Bool
    let isVerified: Bool
    let platform: String?
    let preferences: UserPreferences
    let defaultLocationName: String?
    let createdAt: Date
    
    enum CodingKeys: String, CodingKey {
        case id, email
        case fullName = "full_name"
        case isActive = "is_active"
        case isVerified = "is_verified"
        case platform, preferences
        case defaultLocationName = "default_location_name"
        case createdAt = "created_at"
    }
}

struct UserPreferences: Codable {
    var temperatureUnit: String = "celsius"
    var windSpeedUnit: String = "kmh"
    var precipitationUnit: String = "mm"
    var timeFormat: String = "24h"
    var notificationsEnabled: Bool = true
    var theme: String = "auto"
    
    enum CodingKeys: String, CodingKey {
        case temperatureUnit = "temperature_unit"
        case windSpeedUnit = "wind_speed_unit"
        case precipitationUnit = "precipitation_unit"
        case timeFormat = "time_format"
        case notificationsEnabled = "notifications_enabled"
        case theme
    }
}

struct UserLogin: Codable {
    let email: String
    let password: String
}

struct UserRegister: Codable {
    let email: String
    let password: String
    let fullName: String?
    let platform: String
    let deviceToken: String?
    
    enum CodingKeys: String, CodingKey {
        case email, password
        case fullName = "full_name"
        case platform
        case deviceToken = "device_token"
    }
}

struct TokenResponse: Codable {
    let accessToken: String
    let tokenType: String
    let user: User
    
    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case tokenType = "token_type"
        case user
    }
}

// MARK: - Subscription Models

struct SubscriptionStatus: Codable {
    let hasActiveSubscription: Bool
    let isPremium: Bool
    let subscription: Subscription?
    let features: SubscriptionFeatures
    
    enum CodingKeys: String, CodingKey {
        case hasActiveSubscription = "has_active_subscription"
        case isPremium = "is_premium"
        case subscription, features
    }
}

struct Subscription: Codable {
    let id: String
    let userId: String
    let platform: String
    let plan: String
    let status: String
    let isTrialUsed: Bool
    let trialStartDate: Date?
    let trialEndDate: Date?
    let subscriptionStartDate: Date?
    let subscriptionEndDate: Date?
    let autoRenew: Bool
    let isActive: Bool
    
    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case platform, plan, status
        case isTrialUsed = "is_trial_used"
        case trialStartDate = "trial_start_date"
        case trialEndDate = "trial_end_date"
        case subscriptionStartDate = "subscription_start_date"
        case subscriptionEndDate = "subscription_end_date"
        case autoRenew = "auto_renew"
        case isActive = "is_active"
    }
}

struct SubscriptionFeatures: Codable {
    let extendedForecast: Bool
    let aiInsights: Bool
    let minuteRain: Bool
    let severeAlerts: Bool
    let airQualityDetailed: Bool
    let healthInsights: Bool
    let travelAnalysis: Bool
    
    enum CodingKeys: String, CodingKey {
        case extendedForecast = "extended_forecast"
        case aiInsights = "ai_insights"
        case minuteRain = "minute_rain"
        case severeAlerts = "severe_alerts"
        case airQualityDetailed = "air_quality_detailed"
        case healthInsights = "health_insights"
        case travelAnalysis = "travel_analysis"
    }
}

struct SubscriptionPlan: Codable, Identifiable {
    let id: String
    let name: String
    let price: Double
    let currency: String
    let billingPeriod: String
    let trialDays: Int
    let savings: String?
    let features: [String]
    
    enum CodingKeys: String, CodingKey {
        case id, name, price, currency
        case billingPeriod = "billing_period"
        case trialDays = "trial_days"
        case savings, features
    }
}

// MARK: - API Error

struct APIError: Codable, LocalizedError {
    let detail: String
    
    var errorDescription: String? {
        return detail
    }
}
