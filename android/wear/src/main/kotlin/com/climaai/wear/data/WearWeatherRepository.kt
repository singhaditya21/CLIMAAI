package com.climaai.wear.data

import android.util.Log
import com.climaai.wear.data.api.NominatimApi
import com.climaai.wear.data.api.OpenMeteoApi
import com.climaai.wear.data.api.WeatherCodeMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
 * In production, syncs with phone app via Wearable Data Layer API.
 */
object WearWeatherRepository {
    
    private const val TAG = "WearWeatherRepository"

    // API instances
    private val weatherApi by lazy { OpenMeteoApi.create() }
    private val nominatimApi by lazy { NominatimApi.create() }

    // Cached weather data
    private var cachedWeather: WearWeatherData? = null
    
    suspend fun getWeather(lat: Double = 37.7749, lon: Double = -122.4194): WearWeatherData = withContext(Dispatchers.IO) {
        // In production:
        // 1. Try to get from phone via DataClient (skipped for this task)
        // 2. Fall back to direct API call
        // 3. Fall back to cached data
        
        try {
            // Fetch weather
            val weatherResponse = weatherApi.getWeather(lat, lon)

            if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                val data = weatherResponse.body()!!
                val current = data.current ?: throw Exception("No current weather data")

                // Fetch location name (optional, best effort)
                val locationName = try {
                    val locationResponse = nominatimApi.reverseGeocode(lat, lon)
                    if (locationResponse.isSuccessful && locationResponse.body() != null) {
                        locationResponse.body()!!.address?.getLocationName() ?: "Unknown"
                    } else {
                        "Unknown Location"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch location name", e)
                    "Unknown Location"
                }

                // Extract daily high/low (assuming first element is today)
                val daily = data.daily
                val high = daily?.tempMax?.firstOrNull() ?: current.temperature
                val low = daily?.tempMin?.firstOrNull() ?: current.temperature

                val newData = WearWeatherData(
                    temperature = current.temperature.roundToInt(),
                    condition = WeatherCodeMapper.getDescription(current.weatherCode),
                    conditionIcon = WeatherCodeMapper.getIcon(current.weatherCode, current.isDay == 1),
                    high = high.roundToInt(),
                    low = low.roundToInt(),
                    humidity = current.humidity,
                    windSpeed = current.windSpeed.roundToInt(),
                    location = if (locationName != "Unknown Location" && locationName != "Unknown") locationName else "Lat: %.2f, Lon: %.2f".format(lat, lon)
                )

                cachedWeather = newData
                return@withContext newData
            } else {
                Log.e(TAG, "Weather API error: ${weatherResponse.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch weather", e)
        }

        // Return cached or default on error
        return@withContext cachedWeather ?: WearWeatherData(
            temperature = 72,
            condition = "Partly Cloudy (Demo)",
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
