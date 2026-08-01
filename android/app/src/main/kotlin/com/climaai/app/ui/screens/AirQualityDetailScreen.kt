package com.climaai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.climaai.app.data.AirQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirQualityDetailScreen(
    airQuality: AirQuality?,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Air Quality") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A2E),
                            Color(0xFF16213E)
                        )
                    )
                )
        ) {
            if (airQuality == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF667EEA))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(padding)
                        .padding(horizontal = 20.dp)
                ) {
                    // AQI Circle
                    AQICircle(aqi = airQuality.aqi)
                    
                    Spacer(Modifier.height(24.dp))
                    
                    // Status description
                    AQIStatusCard(aqi = airQuality.aqi)
                    
                    Spacer(Modifier.height(24.dp))
                    
                    // Pollutants
                    Text(
                        "Pollutants",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    PollutantGrid(airQuality)
                    
                    Spacer(Modifier.height(24.dp))
                    
                    // Health recommendations
                    Text(
                        "Health Recommendations",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    HealthRecommendationsCard(aqi = airQuality.aqi)
                    
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun AQICircle(aqi: Int) {
    val color = getAQIColor(aqi)
    
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    aqi.toString(),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    getAQILabel(aqi),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun AQIStatusCard(aqi: Int) {
    val color = getAQIColor(aqi)
    val description = getAQIDescription(aqi)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getAQIIcon(aqi),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                description,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun PollutantGrid(airQuality: AirQuality) {
    val pollutants = listOf(
        Triple("PM2.5", airQuality.pm25, "μg/m³"),
        Triple("PM10", airQuality.pm10 ?: 0.0, "μg/m³"),
        Triple("O₃", airQuality.ozone ?: 0.0, "μg/m³"),
        Triple("NO₂", airQuality.nitrogenDioxide, "μg/m³")
    )
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        pollutants.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { (name, value, unit) ->
                    PollutantCard(
                        name = name,
                        value = value,
                        unit = unit,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty space if odd number
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PollutantCard(
    name: String,
    value: Double,
    unit: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                name,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                String.format("%.1f", value),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                unit,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun HealthRecommendationsCard(aqi: Int) {
    val recommendations = getHealthRecommendations(aqi)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            recommendations.forEach { (icon, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF667EEA),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

private fun getAQIColor(aqi: Int): Color = when {
    aqi <= 50 -> Color(0xFF4CAF50)   // Good - Green
    aqi <= 100 -> Color(0xFFFFEB3B)  // Moderate - Yellow
    aqi <= 150 -> Color(0xFFFF9800)  // Unhealthy for Sensitive - Orange
    aqi <= 200 -> Color(0xFFFF5722)  // Unhealthy - Red
    aqi <= 300 -> Color(0xFF9C27B0)  // Very Unhealthy - Purple
    else -> Color(0xFF7E0023)         // Hazardous - Maroon
}

private fun getAQILabel(aqi: Int): String = when {
    aqi <= 50 -> "Good"
    aqi <= 100 -> "Moderate"
    aqi <= 150 -> "Unhealthy for Sensitive Groups"
    aqi <= 200 -> "Unhealthy"
    aqi <= 300 -> "Very Unhealthy"
    else -> "Hazardous"
}

private fun getAQIIcon(aqi: Int): androidx.compose.ui.graphics.vector.ImageVector = when {
    aqi <= 50 -> Icons.Default.CheckCircle
    aqi <= 100 -> Icons.Default.Info
    aqi <= 150 -> Icons.Default.Warning
    else -> Icons.Default.Error
}

private fun getAQIDescription(aqi: Int): String = when {
    aqi <= 50 -> "Air quality is satisfactory, and air pollution poses little or no risk."
    aqi <= 100 -> "Air quality is acceptable. However, there may be a risk for some people, particularly those unusually sensitive to air pollution."
    aqi <= 150 -> "Members of sensitive groups may experience health effects. The general public is less likely to be affected."
    aqi <= 200 -> "Some members of the general public may experience health effects; members of sensitive groups may experience more serious health effects."
    aqi <= 300 -> "Health alert: The risk of health effects is increased for everyone."
    else -> "Health warning of emergency conditions: everyone is more likely to be affected."
}

private fun getHealthRecommendations(aqi: Int): List<Pair<androidx.compose.ui.graphics.vector.ImageVector, String>> = when {
    aqi <= 50 -> listOf(
        Pair(Icons.Default.DirectionsRun, "Great day for outdoor activities"),
        Pair(Icons.Default.Air, "Open windows for fresh air")
    )
    aqi <= 100 -> listOf(
        Pair(Icons.Default.DirectionsRun, "Outdoor exercise is fine for most people"),
        Pair(Icons.Default.ChildCare, "Sensitive individuals should limit prolonged outdoor exertion")
    )
    aqi <= 150 -> listOf(
        Pair(Icons.Default.Warning, "Sensitive groups should reduce outdoor activity"),
        Pair(Icons.Default.Masks, "Consider wearing a mask outdoors"),
        Pair(Icons.Default.Home, "Keep windows closed")
    )
    else -> listOf(
        Pair(Icons.Default.Home, "Stay indoors and keep windows closed"),
        Pair(Icons.Default.Masks, "Wear N95 mask if going outside"),
        Pair(Icons.Default.Air, "Use air purifier indoors"),
        Pair(Icons.Default.Block, "Avoid all outdoor physical activities")
    )
}
