package com.climaai.app.data.api

import com.google.gson.annotations.SerializedName

// ============================================================
// Alerts Response Models
// ============================================================

data class AlertsResponse(
    val location: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("alert_count") val alertCount: Int,
    val alerts: List<AlertData>
)

data class AlertData(
    @SerializedName("alert_type") val alertType: String,
    val severity: String,
    val title: String,
    val message: String,
    val metadata: Map<String, Any>?,
    @SerializedName("expires_at") val expiresAt: String?
)

data class AlertHistoryResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("alert_count") val alertCount: Int,
    val alerts: List<AlertData>
)

// ============================================================
// Health / Pollen Response Models
// ============================================================

data class PollenForecastResponse(
    val location: Map<String, Any>,
    @SerializedName("forecast_days") val forecastDays: Int,
    val forecast: List<PollenDayForecast>,
    val recommendations: List<String>?
)

data class PollenDayForecast(
    val date: String,
    val tree: PollenTypeData?,
    val grass: PollenTypeData?,
    val weed: PollenTypeData?,
    val overall: PollenSummary?
)

data class PollenTypeData(
    val level: String,
    val index: Int,
    val species: List<String>?
)

data class PollenSummary(
    val level: String,
    val index: Int
)

data class PollenTodayResponse(
    val date: String,
    val location: Map<String, Any>?,
    val tree: PollenTypeData?,
    val grass: PollenTypeData?,
    val weed: PollenTypeData?,
    val overall: PollenSummary?,
    val recommendations: List<String>?
)

data class FluRiskResponse(
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("risk_score") val riskScore: Int,
    val factors: List<String>,
    val recommendations: List<String>,
    val temperature: Double?,
    val humidity: Int?
)

data class MigraineRiskResponse(
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("risk_score") val riskScore: Int,
    val triggers: List<String>,
    val recommendations: List<String>,
    @SerializedName("pressure_change") val pressureChange: Double?,
    @SerializedName("humidity_level") val humidityLevel: Int?
)

// ============================================================
// Activity Forecast Models
// ============================================================

data class AllActivitiesResponse(
    val location: Map<String, Any>?,
    val activities: List<ActivityForecastItem>
)

data class ActivityForecastItem(
    val activity: String,
    @SerializedName("today_score") val todayScore: Int,
    val icon: String?,
    @SerializedName("best_time") val bestTime: String?,
    val summary: String?
)

data class ActivityDetailResponse(
    val activity: String,
    val location: Map<String, Any>?,
    val forecast: List<ActivityDayForecast>
)

data class ActivityDayForecast(
    val date: String,
    val score: Int,
    @SerializedName("best_time") val bestTime: String?,
    val conditions: String?,
    val tips: List<String>?
)

// ============================================================
// Location Models
// ============================================================

data class LocationSearchResponse(
    val query: String,
    val count: Int,
    val results: List<LocationSearchResult>
)

data class LocationSearchResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val admin1: String?,
    val timezone: String?,
    @SerializedName("display_name") val displayName: String?
)

data class FavoriteLocationsResponse(
    val count: Int,
    val favorites: List<FavoriteLocationData>
)

data class FavoriteLocationData(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("is_default") val isDefault: Boolean,
    @SerializedName("created_at") val createdAt: String?
)

data class AddFavoriteResponse(
    val message: String,
    val location: FavoriteLocationData
)

// ============================================================
// Notification Models
// ============================================================

data class DeviceRegisterResponse(
    val message: String,
    val platform: String?
)

data class NotificationPreferencesResponse(
    val preferences: NotificationPreferences
)

data class NotificationPreferences(
    @SerializedName("weather_alerts") val weatherAlerts: Boolean,
    @SerializedName("daily_summary") val dailySummary: Boolean,
    @SerializedName("severe_weather") val severeWeather: Boolean
)

data class NotificationPreferencesUpdateResponse(
    val message: String,
    val preferences: NotificationPreferences
)

data class TestNotificationResponse(
    val message: String,
    @SerializedName("devices_notified") val devicesNotified: Int
)

// ============================================================
// Personalization Models
// ============================================================

data class TrackEventRequest(
    @SerializedName("event_type") val eventType: String,
    @SerializedName("event_data") val eventData: Map<String, Any>,
    @SerializedName("weather_context") val weatherContext: Map<String, Any>? = null
)

data class UserPreferenceProfile(
    @SerializedName("user_id") val userId: String,
    @SerializedName("favorite_features") val favoriteFeatures: List<String>?,
    @SerializedName("notification_preferences") val notificationPreferences: Map<String, Any>?,
    @SerializedName("preferred_activities") val preferredActivities: List<String>?,
    @SerializedName("temperature_sensitivity") val temperatureSensitivity: String?,
    @SerializedName("last_updated") val lastUpdated: String?
)

data class PersonalizedContentResponse(
    @SerializedName("priority_features") val priorityFeatures: List<String>,
    val notifications: List<Map<String, Any>>,
    @SerializedName("activity_suggestions") val activitySuggestions: List<String>,
    @SerializedName("outfit_adjustments") val outfitAdjustments: List<String>,
    @SerializedName("optimal_notification_time") val optimalNotificationTime: String
)

data class ShouldNotifyResponse(
    @SerializedName("should_send") val shouldSend: Boolean
)

// ============================================================
// Precipitation Nowcast Model
// ============================================================

data class PrecipitationNowcast(
    @SerializedName("has_precipitation") val hasPrecipitation: Boolean,
    @SerializedName("precipitation_in_minutes") val precipitationInMinutes: Int?,
    @SerializedName("precipitation_ends_in_minutes") val precipitationEndsInMinutes: Int?,
    val intensity: String,
    @SerializedName("precipitation_type") val precipitationType: String,
    val probability: Int,
    val summary: String
)

// ============================================================
// NWS Weather Alerts Models
// ============================================================

data class NWSAlertsResponse(
    val latitude: Double,
    val longitude: Double,
    @SerializedName("alert_count") val alertCount: Int,
    val alerts: List<NWSAlert>,
    val source: String?
)

data class NWSAlert(
    val id: String?,
    val event: String,
    val headline: String?,
    val severity: String,
    val urgency: String?,
    // The backend sends a JSON array of area names, not a string. Declaring it
    // as String? made Gson fail the whole response with
    // "Expected a string but was BEGIN_ARRAY at path $.alerts[0].areas",
    // so NWS alerts never reached the UI.
    val areas: List<String>?,
    val description: String?,
    val instruction: String?,
    val onset: String?,
    val expires: String?
)

data class NWSStateAlertsResponse(
    val state: String,
    @SerializedName("alert_count") val alertCount: Int,
    val alerts: List<NWSAlert>
)

// ============================================================
// Multi-Source Weather Models
// ============================================================

data class MultiSourceWeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val sources: List<String>,
    val current: MultiSourceCurrent?,
    val forecast: List<MultiSourceForecast>?,
    @SerializedName("uv_index") val uvIndex: UVIndexData?,
    val marine: MarineWeatherData?,
    val historical: HistoricalWeatherData?,
    val metadata: Map<String, Any>?,
    // Nullable: older backends don't send this field, and current ones send
    // null when fewer than 2 sources responded. Absent either way in Gson.
    val consensus: WeatherConsensus?
)

/**
 * Cross-source agreement stats computed by the backend from the raw source
 * values (plain arithmetic — median/range/spread — not model output).
 */
data class WeatherConsensus(
    val temperature: ConsensusVariable?,
    @SerializedName("precipitation_probability") val precipitationProbability: ConsensusVariable?,
    @SerializedName("wind_speed") val windSpeed: ConsensusVariable?,
    /** "high" | "medium" | "low", derived from normalized spread. */
    val confidence: String?,
    val sources: List<String>?,
    /** Plain-English one-liner generated from the numbers. */
    val summary: String?
)

data class ConsensusVariable(
    val median: Double,
    val min: Double,
    val max: Double,
    val spread: Double,
    @SerializedName("source_count") val sourceCount: Int
)

data class MultiSourceCurrent(
    val temperature: Double?,
    @SerializedName("feels_like") val feelsLike: Double?,
    val humidity: Int?,
    @SerializedName("wind_speed") val windSpeed: Double?,
    @SerializedName("wind_direction") val windDirection: Int?,
    val pressure: Double?,
    val visibility: Double?,
    @SerializedName("weather_description") val weatherDescription: String?,
    val source: String?
)

data class MultiSourceForecast(
    val date: String,
    @SerializedName("temp_max") val tempMax: Double?,
    @SerializedName("temp_min") val tempMin: Double?,
    @SerializedName("precipitation_probability") val precipitationProbability: Int?,
    @SerializedName("weather_description") val weatherDescription: String?,
    val source: String?
)

data class UVIndexData(
    @SerializedName("uv_index") val uvIndex: Double,
    @SerializedName("uv_index_max") val uvIndexMax: Double?,
    @SerializedName("safe_exposure_time") val safeExposureTime: Map<String, Int>?,
    @SerializedName("sun_info") val sunInfo: Map<String, String>?,
    val source: String?
)

data class MarineWeatherData(
    @SerializedName("wave_height") val waveHeight: Double?,
    @SerializedName("wave_direction") val waveDirection: Int?,
    @SerializedName("wave_period") val wavePeriod: Double?,
    @SerializedName("water_temperature") val waterTemperature: Double?,
    @SerializedName("sea_level_pressure") val seaLevelPressure: Double?,
    @SerializedName("wind_wave_height") val windWaveHeight: Double?,
    @SerializedName("swell_height") val swellHeight: Double?,
    val source: String?
)

data class HistoricalWeatherData(
    val dates: List<String>?,
    @SerializedName("temperatures_max") val temperaturesMax: List<Double>?,
    @SerializedName("temperatures_min") val temperaturesMin: List<Double>?,
    @SerializedName("precipitation_sum") val precipitationSum: List<Double>?,
    val source: String?
)
