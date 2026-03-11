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
    let feelsLikeShade: Double?
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
        case feelsLikeShade = "feels_like_shade"
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
    let feelsLikeShade: Double?
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
        case feelsLikeShade = "feels_like_shade"
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
    let snowAccumulation: Double?
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
        case snowAccumulation = "snow_accumulation"
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

// MARK: - Pollen Models

struct PollenResponse: Codable {
    let date: String
    let location: Location
    let tree: PollenTypeData
    let grass: PollenTypeData
    let weed: PollenTypeData
    let overall: PollenSummary
    let healthRecommendations: [String]

    enum CodingKeys: String, CodingKey {
        case date, location, tree, grass, weed, overall
        case healthRecommendations = "health_recommendations"
    }
}

struct PollenTypeData: Codable {
    let level: String
    let index: Int
    let species: [String]
}

struct PollenSummary: Codable {
    let level: String
    let index: Int
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

// MARK: - Generic Response

struct MessageResponse: Codable {
    let message: String
}

// MARK: - Alerts Models

struct AlertsResponse: Codable {
    let location: String
    let latitude: Double
    let longitude: Double
    let alertCount: Int
    let alerts: [AlertData]
    
    enum CodingKeys: String, CodingKey {
        case location, latitude, longitude
        case alertCount = "alert_count"
        case alerts
    }
}

struct AlertData: Codable, Identifiable {
    var id: String { alertType + title }
    let alertType: String
    let severity: String
    let title: String
    let message: String
    let metadata: [String: AnyCodable]?
    let expiresAt: String?
    
    enum CodingKeys: String, CodingKey {
        case alertType = "alert_type"
        case severity, title, message, metadata
        case expiresAt = "expires_at"
    }
}

struct AlertHistoryResponse: Codable {
    let userId: String
    let alertCount: Int
    let alerts: [AlertData]
    
    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case alertCount = "alert_count"
        case alerts
    }
}

// MARK: - Health: Pollen Forecast

struct PollenForecastResponse: Codable {
    let location: [String: AnyCodable]?
    let forecastDays: Int
    let forecast: [PollenDayForecast]
    let recommendations: [String]?
    
    enum CodingKeys: String, CodingKey {
        case location
        case forecastDays = "forecast_days"
        case forecast, recommendations
    }
}

struct PollenDayForecast: Codable, Identifiable {
    var id: String { date }
    let date: String
    let tree: PollenTypeData?
    let grass: PollenTypeData?
    let weed: PollenTypeData?
    let overall: PollenSummary?
}

struct PollenTodayResponse: Codable {
    let date: String
    let location: [String: AnyCodable]?
    let tree: PollenTypeData?
    let grass: PollenTypeData?
    let weed: PollenTypeData?
    let overall: PollenSummary?
    let recommendations: [String]?
}

// MARK: - Health: Flu Risk

struct FluRiskResponse: Codable {
    let riskLevel: String
    let riskScore: Int
    let factors: [String]
    let recommendations: [String]
    let temperature: Double?
    let humidity: Int?
    
    enum CodingKeys: String, CodingKey {
        case riskLevel = "risk_level"
        case riskScore = "risk_score"
        case factors, recommendations, temperature, humidity
    }
}

// MARK: - Health: Migraine Risk

struct MigraineRiskResponse: Codable {
    let riskLevel: String
    let riskScore: Int
    let triggers: [String]
    let recommendations: [String]
    let pressureChange: Double?
    let humidityLevel: Int?
    
    enum CodingKeys: String, CodingKey {
        case riskLevel = "risk_level"
        case riskScore = "risk_score"
        case triggers, recommendations
        case pressureChange = "pressure_change"
        case humidityLevel = "humidity_level"
    }
}

// MARK: - Health: Activities

struct AllActivitiesResponse: Codable {
    let location: [String: AnyCodable]?
    let activities: [ActivityForecastItem]
}

struct ActivityForecastItem: Codable, Identifiable {
    var id: String { activity }
    let activity: String
    let todayScore: Int
    let icon: String?
    let bestTime: String?
    let summary: String?
    
    enum CodingKeys: String, CodingKey {
        case activity
        case todayScore = "today_score"
        case icon
        case bestTime = "best_time"
        case summary
    }
}

struct ActivityDetailResponse: Codable {
    let activity: String
    let location: [String: AnyCodable]?
    let forecast: [ActivityDayForecast]
}

struct ActivityDayForecast: Codable, Identifiable {
    var id: String { date }
    let date: String
    let score: Int
    let bestTime: String?
    let conditions: String?
    let tips: [String]?
    
    enum CodingKeys: String, CodingKey {
        case date, score
        case bestTime = "best_time"
        case conditions, tips
    }
}

// MARK: - Notification Models

struct DeviceRegisterResponse: Codable {
    let message: String
    let platform: String?
}

struct NotificationPreferencesWrapper: Codable {
    let preferences: NotificationPreferences
}

struct NotificationPreferences: Codable {
    let weatherAlerts: Bool
    let dailySummary: Bool
    let severeWeather: Bool
    
    enum CodingKeys: String, CodingKey {
        case weatherAlerts = "weather_alerts"
        case dailySummary = "daily_summary"
        case severeWeather = "severe_weather"
    }
}

struct NotificationPreferencesUpdateResponse: Codable {
    let message: String
    let preferences: NotificationPreferences
}

struct TestNotificationResponse: Codable {
    let message: String
    let devicesNotified: Int
    
    enum CodingKeys: String, CodingKey {
        case message
        case devicesNotified = "devices_notified"
    }
}

// MARK: - Personalization Models

struct UserPreferenceProfileResponse: Codable {
    let userId: String
    let favoriteFeatures: [String]?
    let notificationPreferences: [String: AnyCodable]?
    let preferredActivities: [String]?
    let temperatureSensitivity: String?
    let lastUpdated: String?
    
    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case favoriteFeatures = "favorite_features"
        case notificationPreferences = "notification_preferences"
        case preferredActivities = "preferred_activities"
        case temperatureSensitivity = "temperature_sensitivity"
        case lastUpdated = "last_updated"
    }
}

struct PersonalizedContentResponse: Codable {
    let priorityFeatures: [String]
    let notifications: [[String: AnyCodable]]
    let activitySuggestions: [String]
    let outfitAdjustments: [String]
    let optimalNotificationTime: String
    
    enum CodingKeys: String, CodingKey {
        case priorityFeatures = "priority_features"
        case notifications
        case activitySuggestions = "activity_suggestions"
        case outfitAdjustments = "outfit_adjustments"
        case optimalNotificationTime = "optimal_notification_time"
    }
}

struct ShouldNotifyResponse: Codable {
    let shouldSend: Bool
    
    enum CodingKeys: String, CodingKey {
        case shouldSend = "should_send"
    }
}

// MARK: - Precipitation Nowcast

struct PrecipitationNowcastResponse: Codable {
    let hasPrecipitation: Bool
    let precipitationInMinutes: Int?
    let precipitationEndsInMinutes: Int?
    let intensity: String
    let precipitationType: String
    let probability: Int
    let summary: String
    
    enum CodingKeys: String, CodingKey {
        case hasPrecipitation = "has_precipitation"
        case precipitationInMinutes = "precipitation_in_minutes"
        case precipitationEndsInMinutes = "precipitation_ends_in_minutes"
        case intensity
        case precipitationType = "precipitation_type"
        case probability, summary
    }
}

// MARK: - NWS Weather Alerts

struct NWSAlertsResponse: Codable {
    let latitude: Double
    let longitude: Double
    let alertCount: Int
    let alerts: [NWSAlert]
    let source: String?
    
    enum CodingKeys: String, CodingKey {
        case latitude, longitude
        case alertCount = "alert_count"
        case alerts, source
    }
}

struct NWSAlert: Codable, Identifiable {
    var id: String { (self.alertId ?? event) + (headline ?? "") }
    let alertId: String?
    let event: String
    let headline: String?
    let severity: String
    let urgency: String?
    let areas: String?
    let description: String?
    let instruction: String?
    let onset: String?
    let expires: String?
    
    enum CodingKeys: String, CodingKey {
        case alertId = "id"
        case event, headline, severity, urgency, areas
        case description, instruction, onset, expires
    }
}

struct NWSStateAlertsResponse: Codable {
    let state: String
    let alertCount: Int
    let alerts: [NWSAlert]
    
    enum CodingKeys: String, CodingKey {
        case state
        case alertCount = "alert_count"
        case alerts
    }
}

// MARK: - Multi-Source Weather

struct MultiSourceWeatherResponse: Codable {
    let latitude: Double
    let longitude: Double
    let sources: [String]
    let current: MultiSourceCurrent?
    let forecast: [MultiSourceForecast]?
    let uvIndex: UVIndexData?
    let marine: MarineWeatherData?
    let historical: HistoricalWeatherData?
    let metadata: [String: AnyCodable]?
    
    enum CodingKeys: String, CodingKey {
        case latitude, longitude, sources, current, forecast
        case uvIndex = "uv_index"
        case marine, historical, metadata
    }
}

struct MultiSourceCurrent: Codable {
    let temperature: Double?
    let feelsLike: Double?
    let humidity: Int?
    let windSpeed: Double?
    let windDirection: Int?
    let pressure: Double?
    let visibility: Double?
    let weatherDescription: String?
    let source: String?
    
    enum CodingKeys: String, CodingKey {
        case temperature
        case feelsLike = "feels_like"
        case humidity
        case windSpeed = "wind_speed"
        case windDirection = "wind_direction"
        case pressure, visibility
        case weatherDescription = "weather_description"
        case source
    }
}

struct MultiSourceForecast: Codable, Identifiable {
    var id: String { date + (source ?? "") }
    let date: String
    let tempMax: Double?
    let tempMin: Double?
    let precipitationProbability: Int?
    let weatherDescription: String?
    let source: String?
    
    enum CodingKeys: String, CodingKey {
        case date
        case tempMax = "temp_max"
        case tempMin = "temp_min"
        case precipitationProbability = "precipitation_probability"
        case weatherDescription = "weather_description"
        case source
    }
}

struct UVIndexData: Codable {
    let uvIndex: Double
    let uvIndexMax: Double?
    let safeExposureTime: [String: Int]?
    let sunInfo: [String: String]?
    let source: String?
    
    enum CodingKeys: String, CodingKey {
        case uvIndex = "uv_index"
        case uvIndexMax = "uv_index_max"
        case safeExposureTime = "safe_exposure_time"
        case sunInfo = "sun_info"
        case source
    }
}

struct MarineWeatherData: Codable {
    let waveHeight: Double?
    let waveDirection: Int?
    let wavePeriod: Double?
    let waterTemperature: Double?
    let seaLevelPressure: Double?
    let windWaveHeight: Double?
    let swellHeight: Double?
    let source: String?
    
    enum CodingKeys: String, CodingKey {
        case waveHeight = "wave_height"
        case waveDirection = "wave_direction"
        case wavePeriod = "wave_period"
        case waterTemperature = "water_temperature"
        case seaLevelPressure = "sea_level_pressure"
        case windWaveHeight = "wind_wave_height"
        case swellHeight = "swell_height"
        case source
    }
}

struct HistoricalWeatherData: Codable {
    let dates: [String]?
    let temperaturesMax: [Double]?
    let temperaturesMin: [Double]?
    let precipitationSum: [Double]?
    let source: String?
    
    enum CodingKeys: String, CodingKey {
        case dates
        case temperaturesMax = "temperatures_max"
        case temperaturesMin = "temperatures_min"
        case precipitationSum = "precipitation_sum"
        case source
    }
}

// MARK: - AnyCodable Helper

/// A type-erased Codable value for handling dynamic JSON fields.
struct AnyCodable: Codable {
    let value: Any
    
    init(_ value: Any) {
        self.value = value
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let intVal = try? container.decode(Int.self) {
            value = intVal
        } else if let doubleVal = try? container.decode(Double.self) {
            value = doubleVal
        } else if let boolVal = try? container.decode(Bool.self) {
            value = boolVal
        } else if let stringVal = try? container.decode(String.self) {
            value = stringVal
        } else if let arrayVal = try? container.decode([AnyCodable].self) {
            value = arrayVal.map { $0.value }
        } else if let dictVal = try? container.decode([String: AnyCodable].self) {
            value = dictVal.mapValues { $0.value }
        } else {
            value = NSNull()
        }
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        if let intVal = value as? Int {
            try container.encode(intVal)
        } else if let doubleVal = value as? Double {
            try container.encode(doubleVal)
        } else if let boolVal = value as? Bool {
            try container.encode(boolVal)
        } else if let stringVal = value as? String {
            try container.encode(stringVal)
        } else {
            try container.encodeNil()
        }
    }
}
