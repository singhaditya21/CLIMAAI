package com.climaai.wear.data.api

import com.google.gson.annotations.SerializedName

// ============================================================
// Open-Meteo Weather Response Models
// ============================================================

data class OpenMeteoWeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: OpenMeteoCurrentWeather?,
    val daily: OpenMeteoDaily?
)

data class OpenMeteoCurrentWeather(
    val time: String,
    @SerializedName("temperature_2m") val temperature: Double,
    @SerializedName("relative_humidity_2m") val humidity: Int,
    @SerializedName("apparent_temperature") val feelsLike: Double,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("wind_speed_10m") val windSpeed: Double,
    @SerializedName("is_day") val isDay: Int
)

data class OpenMeteoDaily(
    val time: List<String>,
    @SerializedName("temperature_2m_max") val tempMax: List<Double>,
    @SerializedName("temperature_2m_min") val tempMin: List<Double>
)

// ============================================================
// Nominatim Geocoding Models
// ============================================================

data class NominatimResult(
    @SerializedName("place_id") val placeId: Long,
    val lat: String,
    val lon: String,
    @SerializedName("display_name") val displayName: String,
    val address: NominatimAddress?
)

data class NominatimAddress(
    val city: String?,
    val town: String?,
    val village: String?,
    val county: String?,
    val state: String?,
    val country: String?,
    @SerializedName("country_code") val countryCode: String?
) {
    fun getLocationName(): String {
        return city ?: town ?: village ?: county ?: state ?: "Unknown"
    }
}

// ============================================================
// Weather Code Mapping
// ============================================================

object WeatherCodeMapper {

    fun getDescription(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }

    fun getIcon(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1, 2 -> if (isDay) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 61, 63, 65 -> "🌧️"
        56, 57, 66, 67 -> "🌨️"
        71, 73, 75, 77, 85, 86 -> "❄️"
        80, 81, 82 -> "🌦️"
        95, 96, 99 -> "⛈️"
        else -> "🌤️"
    }
}
