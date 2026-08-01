package com.climaai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.climaai.app.MainActivity

/**
 * Small weather widget showing current temperature and icon
 */
class SmallWeatherWidget : GlanceAppWidget() {
    
    override val sizeMode = SizeMode.Single
    
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
        
        val temperature = prefs.getFloat("temperature", 20f)
        val weatherCode = prefs.getInt("weather_code", 0)
        val locationName = prefs.getString("location_name", "Loading...") ?: "Loading..."
        
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF1E3A5F))
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Weather icon
                Text(
                    text = getWeatherEmoji(weatherCode),
                    style = TextStyle(fontSize = 32.sp)
                )
                
                Spacer(modifier = GlanceModifier.height(4.dp))
                
                // Temperature
                Text(
                    text = "${temperature.toInt()}°",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                Spacer(modifier = GlanceModifier.height(4.dp))
                
                // Location
                Text(
                    text = locationName,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

class SmallWeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallWeatherWidget()
}

/**
 * Medium weather widget with current conditions and hourly preview
 */
class MediumWeatherWidget : GlanceAppWidget() {
    
    override val sizeMode = SizeMode.Single
    
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
        
        val temperature = prefs.getFloat("temperature", 20f)
        val feelsLike = prefs.getFloat("feels_like", 18f)
        val weatherCode = prefs.getInt("weather_code", 0)
        val weatherDescription = prefs.getString("weather_description", "Clear") ?: "Clear"
        val locationName = prefs.getString("location_name", "Location") ?: "Location"
        val humidity = prefs.getInt("humidity", 50)
        val windSpeed = prefs.getFloat("wind_speed", 10f)
        
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF1E3A5F))
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(16.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.Start
            ) {
                // Left side - Current weather
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = locationName,
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                            fontSize = 12.sp
                        )
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = getWeatherEmoji(weatherCode),
                            style = TextStyle(fontSize = 28.sp)
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = "${temperature.toInt()}°",
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    
                    Text(
                        text = weatherDescription,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 14.sp
                        )
                    )
                    
                    Text(
                        text = "Feels like ${feelsLike.toInt()}°",
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.6f)),
                            fontSize = 11.sp
                        )
                    )
                }
                
                // Right side - Stats
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatRow("💧", "${humidity}%")
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    StatRow("💨", "${windSpeed.toInt()} km/h")
                }
            }
        }
    }
    
    @Composable
    private fun StatRow(icon: String, value: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, style = TextStyle(fontSize = 14.sp))
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = value,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 13.sp
                )
            )
        }
    }
}

class MediumWeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediumWeatherWidget()
}

/**
 * Large weather widget with full forecast
 */
class LargeWeatherWidget : GlanceAppWidget() {
    
    override val sizeMode = SizeMode.Single
    
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
        val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
        
        val temperature = prefs.getFloat("temperature", 20f)
        val weatherCode = prefs.getInt("weather_code", 0)
        val weatherDescription = prefs.getString("weather_description", "Clear") ?: "Clear"
        val locationName = prefs.getString("location_name", "Location") ?: "Location"
        val aiInsight = prefs.getString("ai_insight", "Great day for outdoor activities!") ?: ""
        
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF1E3A5F))
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(16.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = locationName,
                            style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                                fontSize = 12.sp
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = getWeatherEmoji(weatherCode),
                                style = TextStyle(fontSize = 24.sp)
                            )
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Text(
                                text = "${temperature.toInt()}°",
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Text(
                            text = weatherDescription,
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 14.sp
                            )
                        )
                    }
                }
                
                Spacer(modifier = GlanceModifier.height(12.dp))
                
                // AI Insight
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(Color(0xFF6366F1).copy(alpha = 0.3f))
                        .cornerRadius(8.dp)
                        .padding(12.dp)
                ) {
                    Row {
                        Text(text = "✨", style = TextStyle(fontSize = 16.sp))
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = aiInsight,
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
                
                Spacer(modifier = GlanceModifier.height(12.dp))
                
                // Hourly forecast row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HourlyItem("Now", weatherCode, temperature.toInt())
                    HourlyItem("1h", weatherCode, (temperature + 1).toInt())
                    HourlyItem("2h", weatherCode, (temperature + 2).toInt())
                    HourlyItem("3h", weatherCode, (temperature + 1).toInt())
                    HourlyItem("4h", weatherCode, temperature.toInt())
                }
            }
        }
    }
    
    @Composable
    private fun HourlyItem(time: String, code: Int, temp: Int) {
        Column(
            modifier = GlanceModifier.padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = time,
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.6f)),
                    fontSize = 10.sp
                )
            )
            Text(
                text = getWeatherEmoji(code),
                style = TextStyle(fontSize = 18.sp)
            )
            Text(
                text = "$temp°",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

class LargeWeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargeWeatherWidget()
}

// Helper function to get weather emoji from code
private fun getWeatherEmoji(code: Int): String {
    return when (code) {
        0 -> "☀️"
        1, 2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67 -> "🌧️"
        in 71..77 -> "❄️"
        in 80..82 -> "🌧️"
        in 95..99 -> "⛈️"
        else -> "☀️"
    }
}
