package com.climaai.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.climaai.app.data.CurrentWeather
import com.climaai.app.data.WeatherResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages widget data updates from the main app
 */
object WidgetDataManager {
    
    /**
     * Update all widgets with new weather data
     */
    fun updateWidgets(context: Context, weather: WeatherResponse, locationName: String) {
        val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
        
        with(prefs.edit()) {
            // Current weather
            putFloat("temperature", weather.current.temperature.toFloat())
            putFloat("feels_like", weather.current.feelsLike.toFloat())
            putInt("weather_code", weather.current.weatherCode)
            putString("weather_description", weather.current.weatherDescription)
            putInt("humidity", weather.current.humidity)
            putFloat("wind_speed", weather.current.windSpeed.toFloat())
            putFloat("uv_index", weather.current.uvIndex.toFloat())
            putString("location_name", locationName)
            
            // Air quality
            weather.airQuality?.let { aq ->
                putInt("aqi", aq.aqi)
                putString("aqi_category", aq.category)
            }
            
            // Store hourly forecast (first 6 hours)
            weather.hourly.take(6).forEachIndexed { index, hourly ->
                putFloat("hourly_${index}_temp", hourly.temperature.toFloat())
                putInt("hourly_${index}_code", hourly.weatherCode)
                putLong("hourly_${index}_time", hourly.time.time)
            }
            
            apply()
        }
        
        // Trigger widget updates
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SmallWeatherWidget().updateAll(context)
                MediumWeatherWidget().updateAll(context)
                LargeWeatherWidget().updateAll(context)
            } catch (e: Exception) {
                // Widget not on home screen, ignore
            }
        }
    }
    
    /**
     * Update AI insight text for large widget
     */
    fun updateAIInsight(context: Context, insight: String) {
        val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
        prefs.edit().putString("ai_insight", insight).apply()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LargeWeatherWidget().updateAll(context)
            } catch (e: Exception) {
                // Widget not on home screen, ignore
            }
        }
    }
    
    /**
     * Clear all widget data
     */
    fun clearData(context: Context) {
        context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
