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
        case airQuality
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
        case feelsLike
        case feelsLikeShade
        case humidity
        case windSpeed
        case windDirection
        case precipitation
        case weatherCode
        case weatherDescription
        case cloudCover
        case pressure, visibility
        case uvIndex
        case isDay
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
        case feelsLike
        case feelsLikeShade
        case precipitationProbability
        case precipitation
        case weatherCode
        case weatherDescription
        case windSpeed
        case windDirection
        case humidity
        case cloudCover
        case uvIndex
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
        case temperatureMax
        case temperatureMin
        case sunrise, sunset
        case precipitationSum
        case snowAccumulation
        case precipitationProbability
        case weatherCode
        case weatherDescription
        case windSpeedMax
        case windDirection
        case uvIndexMax
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
        case carbonMonoxide
        case nitrogenDioxide
        case ozone
        case sulphurDioxide
        case category
        case healthRecommendation
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
        case healthRecommendations
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
        case precipitationProbability
        case intensity
        case isPrecipitation
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
        case precipitationStart
        case precipitationEnd
        case totalPrecipitation
        case lastUpdated
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
        case dailySummary
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
        case bestTimes
    }
}

struct OutfitRecommendation: Codable {
    let summary: String
    let details: String
    let accessories: [String]
    let layerRecommendation: String
    
    enum CodingKeys: String, CodingKey {
        case summary, details, accessories
        case layerRecommendation
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
        case suitabilityScore
        case bestTime
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
        case uvRisk
        case uvAdvice
        case airQualityRisk
        case airQualityAdvice
        case heatStressRisk
        case heatStressAdvice
        case allergyRisk
        case allergyAdvice
        case generalHealthTips
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
        case overallRisk
        case summary
        case severeWeatherAlerts
        case travelTips
        case bestTravelTimes
        case worstTravelTimes
    }
}

enum RiskLevel: String, Codable {
    case low, moderate, high
    case veryHigh
    
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
        case fullName
        case isActive
        case isVerified
        case platform, preferences
        case defaultLocationName
        case createdAt
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
        case temperatureUnit
        case windSpeedUnit
        case precipitationUnit
        case timeFormat
        case notificationsEnabled
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
        case fullName
        case platform
        case deviceToken
    }
}

struct TokenResponse: Codable {
    let accessToken: String
    let tokenType: String
    let user: User
    
    enum CodingKeys: String, CodingKey {
        case accessToken
        case tokenType
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
        case hasActiveSubscription
        case isPremium
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
        case userId
        case platform, plan, status
        case isTrialUsed
        case trialStartDate
        case trialEndDate
        case subscriptionStartDate
        case subscriptionEndDate
        case autoRenew
        case isActive
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
        case extendedForecast
        case aiInsights
        case minuteRain
        case severeAlerts
        case airQualityDetailed
        case healthInsights
        case travelAnalysis
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
        case billingPeriod
        case trialDays
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
        case alertCount
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
        case alertType
        case severity, title, message, metadata
        case expiresAt
    }
}

struct AlertHistoryResponse: Codable {
    let userId: String
    let alertCount: Int
    let alerts: [AlertData]
    
    enum CodingKeys: String, CodingKey {
        case userId
        case alertCount
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
        case forecastDays
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
        case riskLevel
        case riskScore
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
        case riskLevel
        case riskScore
        case triggers, recommendations
        case pressureChange
        case humidityLevel
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
        case todayScore
        case icon
        case bestTime
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
        case bestTime
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
        case weatherAlerts
        case dailySummary
        case severeWeather
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
        case devicesNotified
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
        case userId
        case favoriteFeatures
        case notificationPreferences
        case preferredActivities
        case temperatureSensitivity
        case lastUpdated
    }
}

struct PersonalizedContentResponse: Codable {
    let priorityFeatures: [String]
    let notifications: [[String: AnyCodable]]
    let activitySuggestions: [String]
    let outfitAdjustments: [String]
    let optimalNotificationTime: String
    
    enum CodingKeys: String, CodingKey {
        case priorityFeatures
        case notifications
        case activitySuggestions
        case outfitAdjustments
        case optimalNotificationTime
    }
}

struct ShouldNotifyResponse: Codable {
    let shouldSend: Bool
    
    enum CodingKeys: String, CodingKey {
        case shouldSend
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
        case hasPrecipitation
        case precipitationInMinutes
        case precipitationEndsInMinutes
        case intensity
        case precipitationType
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
        case alertCount
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
        case alertCount
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
        case uvIndex
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
        case feelsLike
        case humidity
        case windSpeed
        case windDirection
        case pressure, visibility
        case weatherDescription
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
        case tempMax
        case tempMin
        case precipitationProbability
        case weatherDescription
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
        case uvIndex
        case uvIndexMax
        case safeExposureTime
        case sunInfo
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
        case waveHeight
        case waveDirection
        case wavePeriod
        case waterTemperature
        case seaLevelPressure
        case windWaveHeight
        case swellHeight
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
        case temperaturesMax
        case temperaturesMin
        case precipitationSum
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
