package com.climaai.app.data.sync

import android.content.Context
import android.util.Log
import com.climaai.app.data.WeatherResponse
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object WearableSyncManager {
    private const val TAG = "WearableSyncManager"
    private const val WEATHER_PATH = "/weather"

    suspend fun syncWeather(context: Context, weather: WeatherResponse) {
        withContext(Dispatchers.IO) {
            try {
                val dataClient = Wearable.getDataClient(context)

                val request = PutDataMapRequest.create(WEATHER_PATH).apply {
                    dataMap.putDouble("temperature", weather.current.temperature)
                    dataMap.putString("condition", weather.current.weatherDescription)
                    dataMap.putString("conditionIcon", getWeatherIcon(weather.current.weatherCode))
                    dataMap.putInt("high", weather.daily.firstOrNull()?.temperatureMax?.toInt() ?: 0)
                    dataMap.putInt("low", weather.daily.firstOrNull()?.temperatureMin?.toInt() ?: 0)
                    dataMap.putInt("humidity", weather.current.humidity)
                    dataMap.putInt("windSpeed", weather.current.windSpeed.toInt())
                    dataMap.putString("location", weather.location.name ?: "Unknown")
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }

                dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
                Log.d(TAG, "Weather data synced to Wearable")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync weather data", e)
            }
        }
    }

    private fun getWeatherIcon(code: Int): String {
        return when (code) {
            0 -> "☀️" // Clear sky
            1, 2, 3 -> "⛅" // Partly cloudy
            45, 48 -> "🌫️" // Fog
            51, 53, 55 -> "🌧️" // Drizzle
            56, 57 -> "🌨️" // Freezing Drizzle
            61, 63, 65 -> "🌧️" // Rain
            66, 67 -> "🌨️" // Freezing Rain
            71, 73, 75 -> "❄️" // Snow fall
            77 -> "❄️" // Snow grains
            80, 81, 82 -> "🌧️" // Rain showers
            85, 86 -> "❄️" // Snow showers
            95 -> "⛈️" // Thunderstorm
            96, 99 -> "⛈️" // Thunderstorm with hail
            else -> "❓"
        }
    }
}
