package com.climaai.wear.presentation.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.climaai.wear.data.NoDataReason
import com.climaai.wear.data.WearLocationProvider
import com.climaai.wear.data.WearWeather
import com.climaai.wear.data.WearWeatherData
import com.climaai.wear.data.WearWeatherRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WeatherScreen(
    onNavigateToForecast: () -> Unit
) {
    val context = LocalContext.current
    // null while the reading is in flight; there is no placeholder to show in
    // the meantime, only the spinner.
    var weather by remember { mutableStateOf<WearWeather?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Re-run the load whatever the user chose, so a denial lands on the
        // explicit "Location off" state rather than a spinner.
        attempt++
    }

    LaunchedEffect(attempt) {
        weather = null

        // The activity is the only surface that can ask; the tile and the
        // complications inherit whatever the user decides here.
        if (!WearLocationProvider.hasPermission(context) && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return@LaunchedEffect
        }

        weather = WearWeatherRepository.getWeather(context)
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        when (val current = weather) {
            null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is WearWeather.Available -> WeatherContent(
                weather = current.data,
                onViewForecast = onNavigateToForecast
            )

            is WearWeather.Unavailable -> NoWeatherContent(
                reason = current.reason,
                onRetry = { attempt++ }
            )
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
                    text = "H: ${degrees(weather.high)}",
                    fontSize = 14.sp,
                    color = Color(0xFFFF8A65)
                )
                Text(
                    text = "L: ${degrees(weather.low)}",
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

        // A reading served from cache after a failed refresh is shown with the
        // time it was taken, so nothing on this screen passes for "now" unless
        // it is.
        if (weather.isStale) {
            item {
                Text(
                    text = "Updated ${timeFormat.format(Date(weather.observedAtMillis))}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }

        // Details chip
        item {
            val humidity = weather.humidity?.let { "$it%" } ?: NO_VALUE
            val wind = weather.windSpeed?.let { "$it km/h" } ?: NO_VALUE
            Chip(
                onClick = { },
                label = {
                    Text("💧 $humidity  💨 $wind")
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        // Forecast button
        if (weather.daily.isNotEmpty()) {
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
}

/**
 * The screen with no reading behind it: it names what is missing and offers a
 * retry. Nothing here stands in for a temperature.
 */
@Composable
private fun NoWeatherContent(
    reason: NoDataReason,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = NO_VALUE,
            fontSize = 42.sp,
            fontWeight = FontWeight.Light,
            color = Color.White
        )
        Text(
            text = reason.description,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp)
        )
        if (reason == NoDataReason.LOCATION_PERMISSION) {
            Text(
                text = "Enable it in Settings › Apps › ClimaAI",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Chip(
            onClick = onRetry,
            label = { Text("Retry") },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/** Stands in for a value the watch does not have. */
internal const val NO_VALUE = "--"

private fun degrees(value: Int?): String = value?.let { "$it°" } ?: NO_VALUE

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
