package com.climaai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.climaai.app.BuildConfig
import com.climaai.app.data.DailyWeather
import com.climaai.app.data.TemperatureUnit
import com.climaai.app.data.UnitsPrefs
import com.climaai.app.ui.viewmodel.WeatherState
import com.climaai.app.ui.viewmodel.WeatherViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(
    viewModel: WeatherViewModel,
    onNavigateBack: () -> Unit
) {
    val weatherState by viewModel.weatherState.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()

    // Readings arrive from the API in Celsius; this is the unit they are shown
    // in — the same preference HomeScreen follows, so navigating between the
    // two screens can never change what a temperature means.
    val context = LocalContext.current
    val unitsPrefs = remember { UnitsPrefs(context) }
    val temperatureUnit by unitsPrefs.temperatureUnit.collectAsState(
        initial = TemperatureUnit.CELSIUS
    )

    val daily = (weatherState as? WeatherState.Success)?.data?.daily ?: emptyList()
    val displayDays = if (isPremium) daily else daily.take(7)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Forecast", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
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
                        colors = listOf(Color(0xFF1E3A5F), Color(0xFF0D1B2A))
                    )
                )
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayDays.withIndex().toList()) { (index, day) ->
                    DailyForecastCard(day, index, temperatureUnit)
                }
                
                // Premium upsell — only when monetization is live; with the flag
                // off the paywall route is dark, so an upsell would dead-end.
                if (BuildConfig.MONETIZATION_ENABLED && !isPremium && daily.size > 7) {
                    item {
                        PremiumUpsellCard()
                    }
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun DailyForecastCard(
    day: DailyWeather,
    dayIndex: Int,
    temperatureUnit: TemperatureUnit
) {
    val dayName = when (dayIndex) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayIndex) }
            SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
        }
    }
    
    val dateStr = run {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayIndex) }
        SimpleDateFormat("MMM d", Locale.getDefault()).format(cal.time)
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${temperatureUnit.fromCelsius(day.temperatureMin).roundToInt()}°",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = " / ",
                        color = Color.White.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "${temperatureUnit.fromCelsius(day.temperatureMax).roundToInt()}°",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = day.sunrise.substringAfter("T").take(5),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NightsStay,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = day.sunset.substringAfter("T").take(5),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                if (day.precipitationProbability > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${day.precipitationProbability}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF60A5FA)
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${day.windSpeedMax.toInt()} km/h",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumUpsellCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFBBF24).copy(alpha = 0.2f)
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
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Unlock 14-Day Forecast",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "Upgrade to Premium for extended forecasts",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
