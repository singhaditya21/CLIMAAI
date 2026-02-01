package com.climaai.wear.data

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
    
    // Cached weather data
    private var cachedWeather: WearWeatherData? = null
    
    suspend fun getWeather(): WearWeatherData {
        // In production:
        // 1. Try to get from phone via DataClient
        // 2. Fall back to direct API call
        // 3. Fall back to cached data
        
        return cachedWeather ?: WearWeatherData(
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
