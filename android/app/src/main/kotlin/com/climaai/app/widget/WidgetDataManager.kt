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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Manages widget data updates from the main app
 */
object WidgetDataManager {
    
    /**
     * Update all widgets with new weather data
     */
    fun updateWidgets(
        context: Context,
        weather: WeatherResponse,
        locationName: String,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)

        with(prefs.edit()) {
            // Coordinates, so background refreshes can reuse the last location
            // when none is supplied in their input data.
            latitude?.let { putFloat("latitude", it.toFloat()) }
            longitude?.let { putFloat("longitude", it.toFloat()) }
            putLong("last_updated", System.currentTimeMillis())

            // Daily forecast for the extra-large widget
            weather.daily.take(7).forEachIndexed { index, day ->
                putFloat("daily_${index}_high", day.temperatureMax.toFloat())
                putFloat("daily_${index}_low", day.temperatureMin.toFloat())
                putInt("daily_${index}_code", day.weatherCode)
                putString("daily_${index}_date", day.date)
            }
            putInt("daily_count", weather.daily.take(7).size)

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
     * Read back everything the widgets need, from the same prefs updateWidgets
     * writes. Returns sensible placeholders when nothing has been stored yet, so
     * a widget added before the first refresh still renders.
     */
    fun getData(context: Context): WidgetData {
        val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)

        val dailyCount = prefs.getInt("daily_count", 0)
        val forecast = (0 until dailyCount).map { index ->
            val code = prefs.getInt("daily_${index}_code", 0)
            DayForecast(
                dayName = dayLabel(prefs.getString("daily_${index}_date", null), index),
                icon = weatherEmoji(code),
                high = prefs.getFloat("daily_${index}_high", 0f).roundToInt(),
                low = prefs.getFloat("daily_${index}_low", 0f).roundToInt()
            )
        }

        val lastUpdatedMs = prefs.getLong("last_updated", 0L)
        return WidgetData(
            locationName = prefs.getString("location_name", "—") ?: "—",
            currentTemp = prefs.getFloat("temperature", 0f).roundToInt(),
            weatherIcon = weatherEmoji(prefs.getInt("weather_code", 0)),
            condition = prefs.getString("weather_description", "—") ?: "—",
            high = forecast.firstOrNull()?.high ?: 0,
            low = forecast.firstOrNull()?.low ?: 0,
            humidity = prefs.getInt("humidity", 0),
            windSpeed = prefs.getFloat("wind_speed", 0f).roundToInt(),
            dailyForecast = forecast,
            lastUpdated = if (lastUpdatedMs == 0L) "—" else timeFormat.format(Date(lastUpdatedMs)),
            latitude = prefs.getFloat("latitude", Float.NaN).takeIf { !it.isNaN() }?.toDouble(),
            longitude = prefs.getFloat("longitude", Float.NaN).takeIf { !it.isNaN() }?.toDouble()
        )
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    /** "Today" for the first entry, otherwise a short weekday name. */
    private fun dayLabel(isoDate: String?, index: Int): String {
        if (index == 0) return "Today"
        val parsed = isoDate?.let {
            runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it) }.getOrNull()
        } ?: return "—"
        return SimpleDateFormat("EEE", Locale.getDefault()).format(parsed)
    }

    private fun weatherEmoji(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67 -> "🌧️"
        in 71..77 -> "❄️"
        in 80..82 -> "🌧️"
        in 95..99 -> "⛈️"
        else -> "🌤️"
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

/** Everything the widgets render, read from the shared prefs by [WidgetDataManager.getData]. */
data class WidgetData(
    val locationName: String,
    val currentTemp: Int,
    val weatherIcon: String,
    val condition: String,
    val high: Int,
    val low: Int,
    val humidity: Int,
    val windSpeed: Int,
    val dailyForecast: List<DayForecast>,
    val lastUpdated: String,
    val latitude: Double?,
    val longitude: Double?
)
