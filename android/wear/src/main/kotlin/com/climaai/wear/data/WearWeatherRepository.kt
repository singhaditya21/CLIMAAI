package com.climaai.wear.data

import android.util.Log
import com.climaai.wear.data.api.NominatimApi
import com.climaai.wear.data.api.OpenMeteoApi
import com.climaai.wear.data.api.WeatherCodeMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WearWeatherData(
    val temperature: Int,
    val condition: String,
    val conditionIcon: String,
    val high: Int,
    val low: Int,
    val humidity: Int,
    val windSpeed: Int,
    val location: String
)

/**
 * Repository for fetching weather data on Wear OS.
 * Fetches directly from Open-Meteo API (standalone mode).
 */
object WearWeatherRepository {
    
    private const val TAG = "WearWeatherRepository"

    // Default location: San Francisco
    private const val DEFAULT_LAT = 37.7749
    private const val DEFAULT_LON = -122.4194
    private const val DEFAULT_LOCATION_NAME = "San Francisco"

    private val weatherApi = OpenMeteoApi.create()
    private val locationApi = NominatimApi.create()

    // Cached weather data
    private var cachedWeather: WearWeatherData? = null
    
    suspend fun getWeather(
        lat: Double = DEFAULT_LAT,
        lon: Double = DEFAULT_LON
    ): WearWeatherData = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch Weather
            val weatherResponse = weatherApi.getWeather(lat, lon)

            if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                val data = weatherResponse.body()!!
                val current = data.current
                val daily = data.daily

                if (current != null) {
                    // 2. Fetch Location Name (optional, use default if fails)
                    var locationName = DEFAULT_LOCATION_NAME
                    // Only fetch location name if we are not using default coordinates or if we want to be accurate
                    try {
                        val locationResponse = locationApi.reverseGeocode(lat, lon)
                        if (locationResponse.isSuccessful && locationResponse.body() != null) {
                            val locationData = locationResponse.body()!!
                            locationData.address?.getLocationName()?.let {
                                locationName = it
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Reverse geocoding failed", e)
                    }

                    // Map to WearWeatherData
                    val newWeather = WearWeatherData(
                        temperature = current.temperature.toInt(),
                        condition = WeatherCodeMapper.getDescription(current.weatherCode),
                        conditionIcon = WeatherCodeMapper.getIcon(current.weatherCode, current.isDay == 1),
                        high = daily?.tempMax?.firstOrNull()?.toInt() ?: current.temperature.toInt(),
                        low = daily?.tempMin?.firstOrNull()?.toInt() ?: current.temperature.toInt(),
                        humidity = current.humidity,
                        windSpeed = current.windSpeed.toInt(),
                        location = locationName
                    )

                    cachedWeather = newWeather
                    return@withContext newWeather
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch weather", e)
        }
        
        // Return cached or default if fetch fails
        return@withContext cachedWeather ?: WearWeatherData(
            temperature = 72,
            condition = "Partly Cloudy",
            conditionIcon = "⛅",
            high = 78,
            low = 65,
            humidity = 45,
            windSpeed = 8,
            location = "San Francisco"
        )
    }
    
    fun updateCache(weather: WearWeatherData) {
        cachedWeather = weather
    }
}
