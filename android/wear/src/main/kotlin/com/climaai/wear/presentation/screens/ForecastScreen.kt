package com.climaai.wear.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.climaai.wear.data.WearDayForecast
import com.climaai.wear.data.WearWeather
import com.climaai.wear.data.WearWeatherRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ForecastScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    // Reads the same cached reading the main screen just fetched, so opening
    // the forecast costs no second request.
    var weather by remember { mutableStateOf<WearWeather?>(null) }

    LaunchedEffect(Unit) {
        weather = WearWeatherRepository.getWeather(context)
    }

    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        val forecast = (weather as? WearWeather.Available)?.data?.daily.orEmpty()

        ScalingLazyColumn(
            state = listState,
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

            if (weather == null) {
                item { CircularProgressIndicator() }
            } else if (forecast.isEmpty()) {
                // No forecast on hand and nothing to fake in its place.
                item {
                    Text(
                        text = (weather as? WearWeather.Unavailable)?.reason?.description
                            ?: "No forecast available",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(forecast.size) { index ->
                    ForecastDayChip(forecast[index])
                }
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
private fun ForecastDayChip(day: WearDayForecast) {
    Chip(
        onClick = { },
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dayLabel(day.date), modifier = Modifier.weight(1f))
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

/**
 * "Today" only when the entry really is today. The label is derived at render
 * time because a cached reading can outlive the midnight it was fetched before.
 */
private fun dayLabel(isoDate: String): String {
    val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val parsed = runCatching { isoFormat.parse(isoDate) }.getOrNull() ?: return NO_VALUE
    return if (isoFormat.format(Date()) == isoDate) {
        "Today"
    } else {
        SimpleDateFormat("EEE", Locale.getDefault()).format(parsed)
    }
}
