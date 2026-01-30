package com.climaai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.climaai.app.data.*
import com.climaai.app.ui.viewmodel.WeatherState
import com.climaai.app.ui.viewmodel.WeatherViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: WeatherViewModel,
    onNavigateToForecast: () -> Unit,
    onNavigateToAI: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val weatherState by viewModel.weatherState.collectAsState()
    val location by viewModel.location.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            location?.name ?: "ClimaAI",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { /* Location picker */ }) {
                        Icon(Icons.Default.LocationOn, "Location")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E3A5F),
                            Color(0xFF0D1B2A)
                        )
                    )
                )
                .padding(padding)
        ) {
            when (val state = weatherState) {
                is WeatherState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
                is WeatherState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.message, color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
                is WeatherState.Success -> {
                    WeatherContent(
                        weather = state.data,
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        onNavigateToForecast = onNavigateToForecast,
                        onNavigateToAI = onNavigateToAI
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherContent(
    weather: WeatherResponse,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNavigateToForecast: () -> Unit,
    onNavigateToAI: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Current Weather Card
        item {
            CurrentWeatherCard(weather.current)
        }
        
        // Quick Stats Row
        item {
            QuickStatsRow(weather.current, weather.airQuality)
        }
        
        // Hourly Forecast
        item {
            HourlyForecastSection(weather.hourly)
        }
        
        // AI Insights Preview
        item {
            AIInsightsPreviewCard(onClick = onNavigateToAI)
        }
        
        // Daily Forecast Preview
        item {
            DailyForecastPreview(
                daily = weather.daily.take(5),
                onViewAll = onNavigateToForecast
            )
        }
        
        // Air Quality Card
        weather.airQuality?.let { aq ->
            item {
                AirQualityCard(aq)
            }
        }
        
        // Bottom spacing
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun CurrentWeatherCard(current: CurrentWeather) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Weather icon
            Icon(
                imageVector = getWeatherIcon(current.weatherCode),
                contentDescription = current.weatherDescription,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF87CEEB)
            )
            
            // Temperature
            Text(
                text = "${current.temperature.toInt()}°",
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                color = Color.White
            )
            
            // Description
            Text(
                text = current.weatherDescription,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            
            // Feels like
            Text(
                text = "Feels like ${current.feelsLike.toInt()}°",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun QuickStatsRow(current: CurrentWeather, airQuality: AirQuality?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            icon = Icons.Default.WaterDrop,
            label = "Humidity",
            value = "${current.humidity}%",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon = Icons.Default.Air,
            label = "Wind",
            value = "${current.windSpeed.toInt()} km/h",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon = Icons.Default.WbSunny,
            label = "UV",
            value = "${current.uvIndex.toInt()}",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF87CEEB),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun HourlyForecastSection(hourly: List<HourlyWeather>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Hourly Forecast",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(hourly.take(12)) { hour ->
                    HourlyItem(hour)
                }
            }
        }
    }
}

@Composable
private fun HourlyItem(hour: HourlyWeather) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = timeFormat.format(hour.time),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Icon(
            imageVector = getWeatherIcon(hour.weatherCode),
            contentDescription = null,
            tint = Color(0xFF87CEEB),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${hour.temperature.toInt()}°",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
private fun AIInsightsPreviewCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF6366F1).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI",
                tint = Color(0xFFA78BFA),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "Get personalized weather recommendations",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun DailyForecastPreview(
    daily: List<DailyWeather>,
    onViewAll: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "This Week",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                TextButton(onClick = onViewAll) {
                    Text("See All", color = Color(0xFF87CEEB))
                }
            }
            
            daily.forEachIndexed { index, day ->
                DailyForecastRow(day, index)
                if (index < daily.lastIndex) {
                    Divider(
                        color = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyForecastRow(day: DailyWeather, dayIndex: Int) {
    val dayName = when (dayIndex) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayIndex) }
            SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time)
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayName,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.width(80.dp)
        )
        
        Icon(
            imageVector = getWeatherIcon(day.weatherCode),
            contentDescription = null,
            tint = Color(0xFF87CEEB),
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (day.precipitationProbability > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${day.precipitationProbability}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF60A5FA)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        
        Text(
            text = "${day.temperatureMin.toInt()}°",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${day.temperatureMax.toInt()}°",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun AirQualityCard(airQuality: AirQuality) {
    val aqiColor = when {
        airQuality.aqi <= 50 -> Color(0xFF22C55E)
        airQuality.aqi <= 100 -> Color(0xFFEAB308)
        airQuality.aqi <= 150 -> Color(0xFFF97316)
        else -> Color(0xFFEF4444)
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(aqiColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${airQuality.aqi}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = aqiColor
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Air Quality: ${airQuality.category}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                Text(
                    text = airQuality.healthRecommendation,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// Helper function to map weather codes to icons
private fun getWeatherIcon(code: Int): ImageVector {
    return when (code) {
        0 -> Icons.Default.WbSunny
        1, 2 -> Icons.Default.WbCloudy
        3 -> Icons.Default.Cloud
        45, 48 -> Icons.Default.Foggy
        in 51..67 -> Icons.Default.Grain
        in 71..77 -> Icons.Default.AcUnit
        in 80..82 -> Icons.Default.Grain
        in 95..99 -> Icons.Default.Thunderstorm
        else -> Icons.Default.WbSunny
    }
}
