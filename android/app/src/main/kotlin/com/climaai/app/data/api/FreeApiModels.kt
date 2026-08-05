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

/**
 * Pollen is reported in grains/m³ and is fractional (Berlin answers 36.9 for mugwort),
 * so the pollen fields have to be Double: declared as Int, Gson threw while reading the
 * body and the caller lost the *entire* air quality response — AQI included — for every
 * location that actually has pollen coverage.
 *
 * Pollen is also null outside the CAMS Europe domain, and us_aqi is null where Open-Meteo
 * has no AQI model, so null here means "not reported", never zero.
 */
data class OpenMeteoCurrentAirQuality(
    val time: String,
    @SerializedName("us_aqi") val usAqi: Int?,
    val pm10: Double,
    @SerializedName("pm2_5") val pm25: Double,
    @SerializedName("carbon_monoxide") val carbonMonoxide: Double?,
    @SerializedName("nitrogen_dioxide") val nitrogenDioxide: Double?,
    @SerializedName("sulphur_dioxide") val sulphurDioxide: Double?,
    val ozone: Double?,
    @SerializedName("uv_index") val uvIndex: Double?,
    @SerializedName("alder_pollen") val alderPollen: Double?,
    @SerializedName("birch_pollen") val birchPollen: Double?,
    @SerializedName("grass_pollen") val grassPollen: Double?,
    @SerializedName("mugwort_pollen") val mugwortPollen: Double?,
    @SerializedName("olive_pollen") val olivePollen: Double?,
    @SerializedName("ragweed_pollen") val ragweedPollen: Double?
)

/** Hourly series. Individual entries are null wherever the source has no value for that hour. */
data class OpenMeteoHourlyAirQuality(
    val time: List<String>,
    val pm10: List<Double?>?,
    @SerializedName("pm2_5") val pm25: List<Double?>?,
    @SerializedName("us_aqi") val usAqi: List<Int?>?,
    @SerializedName("uv_index") val uvIndex: List<Double?>?,
    @SerializedName("alder_pollen") val alderPollen: List<Double?>?,
    @SerializedName("birch_pollen") val birchPollen: List<Double?>?,
    @SerializedName("grass_pollen") val grassPollen: List<Double?>?,
    @SerializedName("mugwort_pollen") val mugwortPollen: List<Double?>?,
    @SerializedName("olive_pollen") val olivePollen: List<Double?>?,
    @SerializedName("ragweed_pollen") val ragweedPollen: List<Double?>?
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
    /** Unix seconds the frame was captured for. */
    val time: Long,
    /** Opaque path issued by the index, e.g. "/v2/radar/470e284220fa". */
    val path: String
) {
    /**
     * Tile URL template for this frame, with the {z}/{x}/{y} placeholders left for
     * the map library to fill in.
     *
     * [path] is an opaque id, not a timestamp — a URL built out of [time] (or out of a
     * frame index) 404s, so the path has to be carried through from weather-maps.json
     * verbatim. [host] likewise comes from the index rather than being hard-coded.
     */
    fun tileUrlTemplate(host: String, colorScheme: Int = COLOR_SCHEME_UNIVERSAL_BLUE): String {
        return "$host$path/256/{z}/{x}/{y}/$colorScheme/1_1.png"
    }

    companion object {
        /** RainViewer's default palette and the only one its public docs still list. */
        const val COLOR_SCHEME_UNIVERSAL_BLUE = 2
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
