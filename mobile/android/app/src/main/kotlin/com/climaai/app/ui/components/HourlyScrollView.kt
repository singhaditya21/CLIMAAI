package com.climaai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Data class for hourly forecast item
 */
data class HourlyForecastItem(
    val time: LocalDateTime,
    val temperature: Double,
    val weatherCode: Int,
    val precipitationProbability: Int
)

/**
 * Horizontal scrollable hourly forecast
 */
@Composable
fun HourlyScrollView(
    forecast: List<HourlyForecastItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Hourly Forecast",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(forecast.take(24)) { hour ->
                HourlyItemView(
                    item = hour,
                    isNow = forecast.indexOf(hour) == 0
                )
            }
        }
    }
}

@Composable
private fun HourlyItemView(
    item: HourlyForecastItem,
    isNow: Boolean
) {
    val backgroundColor = if (isNow) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(65.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(vertical = 12.dp)
    ) {
        // Time
        Text(
            text = if (isNow) "Now" else formatHour(item.time),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
            color = if (isNow) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Weather icon
        Text(
            text = getWeatherEmoji(item.weatherCode),
            fontSize = 28.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Temperature
        Text(
            text = "${item.temperature.toInt()}°",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        // Precipitation probability
        if (item.precipitationProbability > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "💧",
                    fontSize = 10.sp
                )
                Text(
                    text = "${item.precipitationProbability}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4A90E2)
                )
            }
        }
    }
}

private fun formatHour(time: LocalDateTime): String {
    val formatter = DateTimeFormatter.ofPattern("ha")
    return time.format(formatter).lowercase()
}

private fun getWeatherEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2, 3 -> "⛅"
    45, 48 -> "🌫️"
    51, 53, 55 -> "🌧️"
    61, 63, 65 -> "🌧️"
    71, 73, 75, 77 -> "❄️"
    80, 81, 82 -> "🌧️"
    85, 86 -> "🌨️"
    95, 96, 99 -> "⛈️"
    else -> "☁️"
}
