package com.climaai.app.data.api

import com.google.gson.annotations.SerializedName

// ============================================================
// Open-Meteo Weather Response Models
// ============================================================

data class OpenMeteoWeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    @SerializedName("timezone_abbreviation") val timezoneAbbreviation: String,
    val elevation: Double,
    val current: OpenMeteoCurrentWeather?,
    @SerializedName("current_units") val currentUnits: Map<String, String>?,
    val hourly: OpenMeteoHourly?,
    @SerializedName("hourly_units") val hourlyUnits: Map<String, String>?,
    val daily: OpenMeteoDaily?,
    @SerializedName("daily_units") val dailyUnits: Map<String, String>?
)

data class OpenMeteoCurrentWeather(
    val time: String,
    @SerializedName("temperature_2m") val temperature: Double,
    @SerializedName("relative_humidity_2m") val humidity: Int,
    @SerializedName("apparent_temperature") val feelsLike: Double,
    @SerializedName("is_day") val isDay: Int,
    val precipitation: Double,
    val rain: Double,
    val showers: Double,
    val snowfall: Double,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("cloud_cover") val cloudCover: Int,
    @SerializedName("pressure_msl") val pressure: Double,
    @SerializedName("surface_pressure") val surfacePressure: Double,
    @SerializedName("wind_speed_10m") val windSpeed: Double,
    @SerializedName("wind_direction_10m") val windDirection: Int,
    @SerializedName("wind_gusts_10m") val windGusts: Double,
    @SerializedName("uv_index") val uvIndex: Double,
    val visibility: Double?,
    @SerializedName("dew_point_2m") val dewPoint: Double?
)

data class OpenMeteoHourly(
    val time: List<String>,
    @SerializedName("temperature_2m") val temperature: List<Double>,
    @SerializedName("relative_humidity_2m") val humidity: List<Int>,
    @SerializedName("apparent_temperature") val feelsLike: List<Double>,
    @SerializedName("precipitation_probability") val precipProbability: List<Int>,
    val precipitation: List<Double>,
    val rain: List<Double>,
    val showers: List<Double>,
    val snowfall: List<Double>,
    @SerializedName("weather_code") val weatherCode: List<Int>,
    @SerializedName("cloud_cover") val cloudCover: List<Int>,
    val visibility: List<Double>?,
    @SerializedName("uv_index") val uvIndex: List<Double>?,
    @SerializedName("is_day") val isDay: List<Int>?
)

data class OpenMeteoDaily(
    val time: List<String>,
    @SerializedName("weather_code") val weatherCode: List<Int>,
    @SerializedName("temperature_2m_max") val tempMax: List<Double>,
    @SerializedName("temperature_2m_min") val tempMin: List<Double>,
    @SerializedName("apparent_temperature_max") val feelsLikeMax: List<Double>,
    @SerializedName("apparent_temperature_min") val feelsLikeMin: List<Double>,
    val sunrise: List<String>,
    val sunset: List<String>,
    @SerializedName("uv_index_max") val uvIndexMax: List<Double>,
    @SerializedName("precipitation_sum") val precipSum: List<Double>,
    @SerializedName("rain_sum") val rainSum: List<Double>,
    @SerializedName("showers_sum") val showersSum: List<Double>,
    @SerializedName("snowfall_sum") val snowfallSum: List<Double>,
    @SerializedName("precipitation_probability_max") val precipProbabilityMax: List<Int>,
    @SerializedName("wind_speed_10m_max") val windSpeedMax: List<Double>,
    @SerializedName("wind_direction_10m_dominant") val windDirectionDominant: List<Int>
)

// ============================================================
// Open-Meteo Air Quality Response Models
// ============================================================

data class OpenMeteoAirQualityResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: OpenMeteoCurrentAirQuality?,
    val hourly: OpenMeteoHourlyAirQuality?
)

data class OpenMeteoCurrentAirQuality(
    val time: String,
    @SerializedName("us_aqi") val usAqi: Int,
    val pm10: Double,
    @SerializedName("pm2_5") val pm25: Double,
    @SerializedName("carbon_monoxide") val carbonMonoxide: Double?,
    @SerializedName("nitrogen_dioxide") val nitrogenDioxide: Double?,
    @SerializedName("sulphur_dioxide") val sulphurDioxide: Double?,
    val ozone: Double?,
    @SerializedName("uv_index") val uvIndex: Double?,
    @SerializedName("alder_pollen") val alderPollen: Int?,
    @SerializedName("birch_pollen") val birchPollen: Int?,
    @SerializedName("grass_pollen") val grassPollen: Int?,
    @SerializedName("mugwort_pollen") val mugwortPollen: Int?,
    @SerializedName("olive_pollen") val olivePollen: Int?,
    @SerializedName("ragweed_pollen") val ragweedPollen: Int?
)

data class OpenMeteoHourlyAirQuality(
    val time: List<String>,
    val pm10: List<Double>?,
    @SerializedName("pm2_5") val pm25: List<Double>?,
    @SerializedName("us_aqi") val usAqi: List<Int>?,
    @SerializedName("uv_index") val uvIndex: List<Double>?,
    @SerializedName("alder_pollen") val alderPollen: List<Int>?,
    @SerializedName("birch_pollen") val birchPollen: List<Int>?,
    @SerializedName("grass_pollen") val grassPollen: List<Int>?,
    @SerializedName("mugwort_pollen") val mugwortPollen: List<Int>?,
    @SerializedName("olive_pollen") val olivePollen: List<Int>?,
    @SerializedName("ragweed_pollen") val ragweedPollen: List<Int>?
)

// ============================================================
// Nominatim Geocoding Models
// ============================================================

data class NominatimResult(
    @SerializedName("place_id") val placeId: Long,
    val lat: String,
    val lon: String,
    @SerializedName("display_name") val displayName: String,
    val name: String?,
    val type: String?,
    val address: NominatimAddress?
)

data class NominatimAddress(
    val city: String?,
    val town: String?,
    val village: String?,
    val county: String?,
    val state: String?,
    val country: String?,
    @SerializedName("country_code") val countryCode: String?,
    val postcode: String?
) {
    fun getLocationName(): String {
        return city ?: town ?: village ?: county ?: state ?: "Unknown"
    }
    
    fun getFullLocation(): String {
        val parts = listOfNotNull(
            city ?: town ?: village,
            state,
            country
        )
        return parts.joinToString(", ")
    }
}

// ============================================================
// RainViewer Models
// ============================================================

data class RainViewerMapsResponse(
    val version: String,
    val generated: Long,
    val host: String,
    val radar: RainViewerRadar,
    val satellite: RainViewerSatellite?
)

data class RainViewerRadar(
    val past: List<RainViewerFrame>,
    val nowcast: List<RainViewerFrame>?
)

data class RainViewerSatellite(
    val infrared: List<RainViewerFrame>?
)

data class RainViewerFrame(
    val time: Long,
    val path: String
) {
    fun getTileUrl(z: Int, x: Int, y: Int): String {
        return "https://tilecache.rainviewer.com$path/256/$z/$x/$y/2/1_1.png"
    }
}

// ============================================================
// Weather Code Mapping (WMO)
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
