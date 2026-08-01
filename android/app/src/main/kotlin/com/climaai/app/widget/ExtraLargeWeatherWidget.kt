package com.climaai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.climaai.app.MainActivity
import com.climaai.app.R

/**
 * Extra large 4x4 weather widget with 7-day forecast.
 * Closes the gap with AccuWeather's widget variety.
 */
class ExtraLargeWeatherWidget : GlanceAppWidget() {
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataManager.getData(context)
        
        provideContent {
            ExtraLargeWidgetContent(
                locationName = data.locationName,
                currentTemp = data.currentTemp,
                currentIcon = data.weatherIcon,
                condition = data.condition,
                high = data.high,
                low = data.low,
                humidity = data.humidity,
                windSpeed = data.windSpeed,
                forecast = data.dailyForecast,
                lastUpdated = data.lastUpdated
            )
        }
    }
}

@Composable
private fun ExtraLargeWidgetContent(
    locationName: String,
    currentTemp: Int,
    currentIcon: String,
    condition: String,
    high: Int,
    low: Int,
    humidity: Int,
    windSpeed: Int,
    forecast: List<DayForecast>,
    lastUpdated: String
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF1A1A2E)))
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(16.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // Header: Location and current weather
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = locationName,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = condition,
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                            fontSize = 12.sp
                        )
                    )
                }
                
                Text(
                    text = currentIcon,
                    style = TextStyle(fontSize = 40.sp)
                )
                
                Spacer(GlanceModifier.width(8.dp))
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${currentTemp}°",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )
                    Row {
                        Text(
                            text = "H:${high}°",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFFF8A65)),
                                fontSize = 11.sp
                            )
                        )
                        Spacer(GlanceModifier.width(4.dp))
                        Text(
                            text = "L:${low}°",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF64B5F6)),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
            
            Spacer(GlanceModifier.height(12.dp))
            
            // Stats row
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(Color.White.copy(alpha = 0.1f)))
                    .cornerRadius(12.dp)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatItem("💧", "${humidity}%", "Humidity", GlanceModifier.defaultWeight())
                StatItem("💨", "${windSpeed} mph", "Wind", GlanceModifier.defaultWeight())
            }
            
            Spacer(GlanceModifier.height(12.dp))
            
            // 7-day forecast
            Text(
                text = "7-Day Forecast",
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.6f)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.padding(bottom = 4.dp)
            )
            
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(Color.White.copy(alpha = 0.05f)))
                    .cornerRadius(12.dp)
                    .padding(8.dp)
            ) {
                forecast.take(7).forEachIndexed { index, day ->
                    ForecastRow(day)
                    if (index < forecast.size - 1 && index < 6) {
                        Spacer(GlanceModifier.height(4.dp))
                    }
                }
            }
            
            Spacer(GlanceModifier.defaultWeight())
            
            // Last updated
            Text(
                text = "Updated $lastUpdated",
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.4f)),
                    fontSize = 9.sp
                )
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: String,
    value: String,
    label: String,
    modifier: GlanceModifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, style = TextStyle(fontSize = 14.sp))
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = value,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(Color.White.copy(alpha = 0.5f)),
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun ForecastRow(day: DayForecast) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day.dayName,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 12.sp
            ),
            modifier = GlanceModifier.width(48.dp)
        )
        
        Spacer(GlanceModifier.defaultWeight())
        
        Text(
            text = day.icon,
            style = TextStyle(fontSize = 16.sp)
        )
        
        Spacer(GlanceModifier.defaultWeight())
        
        Row {
            Text(
                text = "${day.high}°",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFFF8A65)),
                    fontSize = 12.sp
                )
            )
            Text(
                text = " / ",
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.3f)),
                    fontSize = 12.sp
                )
            )
            Text(
                text = "${day.low}°",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF64B5F6)),
                    fontSize = 12.sp
                )
            )
        }
    }
}

data class DayForecast(
    val dayName: String,
    val icon: String,
    val high: Int,
    val low: Int
)

class ExtraLargeWeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExtraLargeWeatherWidget()
}
