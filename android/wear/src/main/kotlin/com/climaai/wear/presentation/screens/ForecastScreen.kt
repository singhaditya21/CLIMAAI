package com.climaai.wear.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*

data class DayForecast(
    val day: String,
    val icon: String,
    val high: Int,
    val low: Int
)

@Composable
fun ForecastScreen(
    onBack: () -> Unit
) {
    val forecast = remember {
        listOf(
            DayForecast("Today", "⛅", 78, 65),
            DayForecast("Tue", "☀️", 82, 68),
            DayForecast("Wed", "☀️", 80, 66),
            DayForecast("Thu", "🌧️", 72, 60),
            DayForecast("Fri", "⛈️", 68, 58),
            DayForecast("Sat", "🌤️", 75, 62),
            DayForecast("Sun", "☀️", 79, 64)
        )
    }
    
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = rememberScalingLazyListState()) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = "7-Day Forecast",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            items(forecast.size) { index ->
                val day = forecast[index]
                ForecastDayChip(day)
            }
            
            item {
                Chip(
                    onClick = onBack,
                    label = { Text("Back") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ForecastDayChip(day: DayForecast) {
    Chip(
        onClick = { },
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(day.day, modifier = Modifier.weight(1f))
                Text(day.icon)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${day.high}°",
                    color = Color(0xFFFF8A65)
                )
                Text(" / ", color = Color.White.copy(alpha = 0.5f))
                Text(
                    "${day.low}°",
                    color = Color(0xFF64B5F6)
                )
            }
        },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    )
}
