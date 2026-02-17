package com.climaai.app.data.repository

import android.util.Log
import com.climaai.app.data.*
import com.climaai.app.data.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Weather data source using Open-Meteo API.
 * Free, no API key required.
 *
 * Primary data source for standalone mode (no backend needed).
 */
object OpenMeteoRepository {

    private const val TAG = "OpenMeteoRepository"
    private val weatherApi = OpenMeteoApi.create()
    private val airQualityApi = OpenMeteoAirQualityApi.create()
    private val nominatimApi = NominatimApi.create()

    /**
     * Fetch weather directly from Open-Meteo.
     */
    suspend fun getWeather(lat: Double, lon: Double): Result<WeatherResponse> = withContext(Dispatchers.IO) {
        try {
            val response = weatherApi.getWeather(lat, lon)

            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                val weather = mapToWeatherResponse(data, lat, lon)

                // Try to enrich with air quality data
                val enrichedWeather = try {
                    val aqResponse = airQualityApi.getAirQuality(lat, lon)
                    if (aqResponse.isSuccessful && aqResponse.body() != null) {
                        val aqData = aqResponse.body()!!
                        weather.copy(airQuality = mapAirQuality(aqData))
                    } else {
                        weather
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Air quality fetch failed, continuing without", e)
                    weather
                }

                Result.success(enrichedWeather)
            } else {
                Log.e(TAG, "API error: ${response.code()}")
                Result.failure(Exception("Failed to fetch weather: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error", e)
            Result.failure(e)
        }
    }

    /**
     * Reverse geocode coordinates to a location name using Nominatim.
     */
    suspend fun getLocationName(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            val response = nominatimApi.reverseGeocode(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                result.address?.getLocationName()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed", e)
            null
        }
    }

    /**
     * Map Open-Meteo response to app's WeatherResponse model (Models.kt).
     */
    private fun mapToWeatherResponse(
        data: OpenMeteoWeatherResponse,
        lat: Double,
        lon: Double
    ): WeatherResponse {
        val current = data.current ?: throw Exception("No current weather")
        val hourly = data.hourly
        val daily = data.daily

        val isDay = current.isDay == 1
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone(data.timezone)
        }

        return WeatherResponse(
            location = Location(
                latitude = lat,
                longitude = lon,
                elevation = data.elevation,
                name = null // Will be enriched by reverse geocoding
            ),
            current = CurrentWeather(
                temperature = current.temperature,
                feelsLike = current.feelsLike,
                humidity = current.humidity,
                windSpeed = current.windSpeed,
                windDirection = current.windDirection,
                precipitation = current.precipitation,
                weatherCode = current.weatherCode,
                weatherDescription = WeatherCodeMapper.getDescription(current.weatherCode),
                cloudCover = current.cloudCover,
                pressure = current.pressure,
                visibility = (current.visibility ?: 10000.0) / 1000, // Convert m to km
                uvIndex = current.uvIndex,
                isDay = isDay,
                timestamp = try {
                    isoFormat.parse(current.time) ?: Date()
                } catch (e: Exception) {
                    Date()
                }
            ),
            hourly = hourly?.let { mapHourlyForecast(it, data.timezone) } ?: emptyList(),
            daily = daily?.let { mapDailyForecast(it) } ?: emptyList(),
            airQuality = null, // Fetched separately
            timezone = data.timezone,
            cached = false
        )
    }

    private fun mapHourlyForecast(hourly: OpenMeteoHourly, timezone: String): List<HourlyWeather> {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone(timezone)
        }

        return hourly.time.mapIndexed { index, time ->
            val isDay = hourly.isDay?.getOrNull(index) == 1
            val weatherCode = hourly.weatherCode.getOrNull(index) ?: 0

            HourlyWeather(
                time = try {
                    isoFormat.parse(time) ?: Date()
                } catch (e: Exception) {
                    Date()
                },
                temperature = hourly.temperature.getOrNull(index) ?: 0.0,
                feelsLike = hourly.feelsLike.getOrNull(index) ?: 0.0,
                precipitationProbability = hourly.precipProbability.getOrNull(index) ?: 0,
                precipitation = hourly.precipitation.getOrNull(index) ?: 0.0,
                weatherCode = weatherCode,
                weatherDescription = WeatherCodeMapper.getDescription(weatherCode),
                windSpeed = 0.0, // Not in hourly params
                windDirection = 0,
                humidity = hourly.humidity.getOrNull(index) ?: 0,
                cloudCover = hourly.cloudCover.getOrNull(index) ?: 0,
                uvIndex = hourly.uvIndex?.getOrNull(index) ?: 0.0,
                isDay = isDay
            )
        }.take(48)
    }

    private fun mapDailyForecast(daily: OpenMeteoDaily): List<DailyWeather> {
        return daily.time.mapIndexed { index, date ->
            val weatherCode = daily.weatherCode.getOrNull(index) ?: 0

            DailyWeather(
                date = date,
                temperatureMax = daily.tempMax.getOrNull(index) ?: 0.0,
                temperatureMin = daily.tempMin.getOrNull(index) ?: 0.0,
                sunrise = daily.sunrise.getOrNull(index) ?: "",
                sunset = daily.sunset.getOrNull(index) ?: "",
                precipitationSum = daily.precipSum.getOrNull(index) ?: 0.0,
                precipitationProbability = daily.precipProbabilityMax.getOrNull(index) ?: 0,
                weatherCode = weatherCode,
                weatherDescription = WeatherCodeMapper.getDescription(weatherCode),
                windSpeedMax = daily.windSpeedMax.getOrNull(index) ?: 0.0,
                windDirection = daily.windDirectionDominant.getOrNull(index) ?: 0,
                uvIndexMax = daily.uvIndexMax.getOrNull(index) ?: 0.0
            )
        }
    }

    /**
     * Map Open-Meteo Air Quality response to app's AirQuality model.
     */
    private fun mapAirQuality(data: OpenMeteoAirQualityResponse): AirQuality? {
        val current = data.current ?: return null
        val aqi = current.usAqi

        val category = when {
            aqi <= 50 -> "Good"
            aqi <= 100 -> "Moderate"
            aqi <= 150 -> "Unhealthy for Sensitive Groups"
            aqi <= 200 -> "Unhealthy"
            aqi <= 300 -> "Very Unhealthy"
            else -> "Hazardous"
        }

        val recommendation = when {
            aqi <= 50 -> "Air quality is satisfactory. Enjoy outdoor activities!"
            aqi <= 100 -> "Air quality is acceptable. Sensitive individuals should limit prolonged outdoor exertion."
            aqi <= 150 -> "Members of sensitive groups may experience health effects. Limit prolonged outdoor exertion."
            aqi <= 200 -> "Everyone may begin to experience health effects. Reduce prolonged outdoor exertion."
            aqi <= 300 -> "Health alert: everyone may experience serious health effects. Avoid outdoor activities."
            else -> "Health emergency: stay indoors and keep windows closed."
        }

        return AirQuality(
            aqi = aqi,
            pm25 = current.pm25,
            pm10 = current.pm10,
            carbonMonoxide = current.carbonMonoxide ?: 0.0,
            nitrogenDioxide = current.nitrogenDioxide ?: 0.0,
            ozone = current.ozone ?: 0.0,
            sulphurDioxide = current.sulphurDioxide ?: 0.0,
            category = category,
            healthRecommendation = recommendation
        )
    }
}
