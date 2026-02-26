package com.climaai.wear.data

import android.content.Context
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

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
    
    suspend fun getWeather(context: Context): WearWeatherData {
        // In production:
        // 1. Try to get from phone via DataClient
        try {
            val dataClient = Wearable.getDataClient(context)
            // Query for data items with path /weather
            val buffer = dataClient.dataItems.await()

            try {
                for (item in buffer) {
                    if (item.uri.path == "/weather") {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap

                        val weather = WearWeatherData(
                            temperature = dataMap.getDouble("temperature").toInt(),
                            condition = dataMap.getString("condition", "Partly Cloudy"),
                            conditionIcon = dataMap.getString("conditionIcon", "⛅"),
                            high = dataMap.getInt("high", 78),
                            low = dataMap.getInt("low", 65),
                            humidity = dataMap.getInt("humidity", 45),
                            windSpeed = dataMap.getInt("windSpeed", 8),
                            location = dataMap.getString("location", "San Francisco")
                        )

                        cachedWeather = weather
                        return weather
                    }
                }
            } finally {
                buffer.release()
            }
        } catch (e: Exception) {
            // Log error but continue to fallback
            e.printStackTrace()
        }

        // 2. Fall back to cached data
        cachedWeather?.let { return it }

        // 3. Fall back to demo data
        return WearWeatherData(
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
