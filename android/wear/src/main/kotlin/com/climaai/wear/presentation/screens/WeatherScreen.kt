package com.climaai.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.climaai.wear.data.WearWeatherRepository
import com.climaai.wear.data.WearWeatherData

@Composable
fun WeatherScreen(
    onNavigateToForecast: () -> Unit
) {
    var weather by remember { mutableStateOf<WearWeatherData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Fetch weather on launch
    LaunchedEffect(Unit) {
        // Fetch real data from repository
        weather = WearWeatherRepository.getWeather()
        isLoading = false
    }
    
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            weather?.let { data ->
                WeatherContent(
                    weather = data,
                    onViewForecast = onNavigateToForecast
                )
            }
        }
    }
}

@Composable
private fun WeatherContent(
    weather: WearWeatherData,
    onViewForecast: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Location
        item {
            Text(
                text = weather.location,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        
        // Weather icon
        item {
            Text(
                text = weather.conditionIcon,
                fontSize = 48.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        // Temperature
        item {
            Text(
                text = "${weather.temperature}°",
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
                color = Color.White
            )
        }
        
        // High/Low
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "H: ${weather.high}°",
                    fontSize = 14.sp,
                    color = Color(0xFFFF8A65)
                )
                Text(
                    text = "L: ${weather.low}°",
                    fontSize = 14.sp,
                    color = Color(0xFF64B5F6)
                )
            }
        }
        
        // Condition
        item {
            Text(
                text = weather.condition,
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        // Details chip
        item {
            Chip(
                onClick = { },
                label = {
                    Text("💧 ${weather.humidity}%  💨 ${weather.windSpeed} mph")
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        
        // Forecast button
        item {
            Chip(
                onClick = onViewForecast,
                label = { Text("7-Day Forecast") },
                icon = { Text("📅") },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
