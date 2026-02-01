package com.climaai.app.data

import com.google.gson.annotations.SerializedName
import java.util.Date

// Weather Models
data class WeatherResponse(
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,
    val daily: List<DailyWeather>,
    @SerializedName("air_quality") val airQuality: AirQuality?,
    val location: Location,
    val timezone: String,
    val cached: Boolean = false
)

data class CurrentWeather(
    val temperature: Double,
    @SerializedName("feels_like") val feelsLike: Double,
    val humidity: Int,
    @SerializedName("wind_speed") val windSpeed: Double,
    @SerializedName("wind_direction") val windDirection: Int,
    val precipitation: Double,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("weather_description") val weatherDescription: String,
    @SerializedName("cloud_cover") val cloudCover: Int,
    val pressure: Double,
    val visibility: Double,
    @SerializedName("uv_index") val uvIndex: Double,
    @SerializedName("is_day") val isDay: Boolean,
    val timestamp: Date
)

data class HourlyWeather(
    val time: Date,
    val temperature: Double,
    @SerializedName("feels_like") val feelsLike: Double,
    @SerializedName("precipitation_probability") val precipitationProbability: Int,
    val precipitation: Double,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("weather_description") val weatherDescription: String,
    @SerializedName("wind_speed") val windSpeed: Double,
    @SerializedName("wind_direction") val windDirection: Int,
    val humidity: Int,
    @SerializedName("cloud_cover") val cloudCover: Int,
    @SerializedName("uv_index") val uvIndex: Double
)

data class DailyWeather(
    val date: String,
    @SerializedName("temperature_max") val temperatureMax: Double,
    @SerializedName("temperature_min") val temperatureMin: Double,
    val sunrise: String,
    val sunset: String,
    @SerializedName("precipitation_sum") val precipitationSum: Double,
    @SerializedName("precipitation_probability") val precipitationProbability: Int,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("weather_description") val weatherDescription: String,
    @SerializedName("wind_speed_max") val windSpeedMax: Double,
    @SerializedName("wind_direction") val windDirection: Int,
    @SerializedName("uv_index_max") val uvIndexMax: Double
)

data class AirQuality(
    val aqi: Int,
    @SerializedName("pm2_5") val pm25: Double,
    val pm10: Double,
    @SerializedName("carbon_monoxide") val carbonMonoxide: Double,
    @SerializedName("nitrogen_dioxide") val nitrogenDioxide: Double,
    val ozone: Double,
    @SerializedName("sulphur_dioxide") val sulphurDioxide: Double,
    val category: String,
    @SerializedName("health_recommendation") val healthRecommendation: String
)

data class Location(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    val name: String? = null
)

// AI Models
data class AIInsightsResponse(
    @SerializedName("daily_summary") val dailySummary: DailySummary,
    val outfit: OutfitRecommendation,
    val activities: List<ActivityRecommendation>,
    val health: HealthInsight,
    val travel: TravelRiskAnalysis?,
    val cached: Boolean = false
)

data class DailySummary(
    val title: String,
    val summary: String,
    val highlights: List<String>,
    val warnings: List<String>,
    @SerializedName("best_times") val bestTimes: Map<String, String>
)

data class OutfitRecommendation(
    val summary: String,
    val details: String,
    val accessories: List<String>,
    @SerializedName("layer_recommendation") val layerRecommendation: String
)

data class ActivityRecommendation(
    val activity: String,
    @SerializedName("suitability_score") val suitabilityScore: Int,
    @SerializedName("best_time") val bestTime: String,
    val reasoning: String,
    val precautions: List<String>
)

data class HealthInsight(
    @SerializedName("uv_risk") val uvRisk: RiskLevel,
    @SerializedName("uv_advice") val uvAdvice: String,
    @SerializedName("air_quality_risk") val airQualityRisk: RiskLevel,
    @SerializedName("air_quality_advice") val airQualityAdvice: String,
    @SerializedName("heat_stress_risk") val heatStressRisk: RiskLevel,
    @SerializedName("heat_stress_advice") val heatStressAdvice: String,
    @SerializedName("allergy_risk") val allergyRisk: RiskLevel?,
    @SerializedName("allergy_advice") val allergyAdvice: String?,
    @SerializedName("general_health_tips") val generalHealthTips: List<String>
)

data class TravelRiskAnalysis(
    @SerializedName("overall_risk") val overallRisk: RiskLevel,
    val summary: String,
    @SerializedName("severe_weather_alerts") val severeWeatherAlerts: List<String>,
    @SerializedName("travel_tips") val travelTips: List<String>,
    @SerializedName("best_travel_times") val bestTravelTimes: List<String>,
    @SerializedName("worst_travel_times") val worstTravelTimes: List<String>
)

enum class RiskLevel {
    @SerializedName("low") LOW,
    @SerializedName("moderate") MODERATE,
    @SerializedName("high") HIGH,
    @SerializedName("very_high") VERY_HIGH
}

// User Models
data class User(
    val id: String,
    val email: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("is_verified") val isVerified: Boolean,
    val platform: String?,
    val preferences: UserPreferences,
    @SerializedName("default_location_name") val defaultLocationName: String?,
    @SerializedName("created_at") val createdAt: Date
)

data class UserPreferences(
    @SerializedName("temperature_unit") val temperatureUnit: String = "celsius",
    @SerializedName("wind_speed_unit") val windSpeedUnit: String = "kmh",
    @SerializedName("precipitation_unit") val precipitationUnit: String = "mm",
    @SerializedName("time_format") val timeFormat: String = "24h",
    @SerializedName("notifications_enabled") val notificationsEnabled: Boolean = true,
    val theme: String = "auto"
)

data class UserLogin(
    val email: String,
    val password: String
)

data class UserRegister(
    val email: String,
    val password: String,
    @SerializedName("full_name") val fullName: String?,
    val platform: String = "android",
    @SerializedName("device_token") val deviceToken: String?
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    val user: User
)

// Subscription Models
data class SubscriptionStatus(
    @SerializedName("has_active_subscription") val hasActiveSubscription: Boolean,
    @SerializedName("is_premium") val isPremium: Boolean,
    val subscription: Subscription?,
    val features: SubscriptionFeatures
)

data class Subscription(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val platform: String,
    val plan: String,
    val status: String,
    @SerializedName("is_trial_used") val isTrialUsed: Boolean,
    @SerializedName("trial_start_date") val trialStartDate: Date?,
    @SerializedName("trial_end_date") val trialEndDate: Date?,
    @SerializedName("subscription_start_date") val subscriptionStartDate: Date?,
    @SerializedName("subscription_end_date") val subscriptionEndDate: Date?,
    @SerializedName("auto_renew") val autoRenew: Boolean,
    @SerializedName("is_active") val isActive: Boolean
)

data class SubscriptionFeatures(
    @SerializedName("extended_forecast") val extendedForecast: Boolean,
    @SerializedName("ai_insights") val aiInsights: Boolean,
    @SerializedName("minute_rain") val minuteRain: Boolean,
    @SerializedName("severe_alerts") val severeAlerts: Boolean,
    @SerializedName("air_quality_detailed") val airQualityDetailed: Boolean,
    @SerializedName("health_insights") val healthInsights: Boolean,
    @SerializedName("travel_analysis") val travelAnalysis: Boolean
)

data class SubscriptionPlan(
    val id: String,
    val name: String,
    val price: Double,
    val currency: String,
    @SerializedName("billing_period") val billingPeriod: String,
    @SerializedName("trial_days") val trialDays: Int,
    val savings: String?,
    val features: List<String>
)

data class APIError(
    val detail: String
)
