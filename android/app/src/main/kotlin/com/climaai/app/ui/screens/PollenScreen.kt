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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.climaai.app.data.repository.OpenMeteoRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pollen readings for one location, in grains/m³ exactly as the source reports them.
 *
 * A null reading means the source publishes no figure for that species here — Open-Meteo's
 * pollen comes from CAMS, whose domain is Europe, so outside it every species is null. That
 * is not the same as zero and is never rendered as a number.
 */
data class PollenData(
    val readings: Map<PollenSpecies, Double?> = emptyMap(),
    val forecast: List<PollenDay> = emptyList()
) {
    /** Highest species reading right now, or null when nothing is reported here. */
    val peak: Double? get() = readings.values.filterNotNull().maxOrNull()
}

/** One forecast day. [peak] is the day's highest hourly reading across all species. */
data class PollenDay(
    /** ISO date (yyyy-MM-dd) in the series' own frame of reference, which is UTC. */
    val date: String,
    val peak: Double?
)

/**
 * The species Open-Meteo actually publishes. Mold is not one of them, so the screen no
 * longer has a mold card: the old one was derived from PM2.5, which does not measure spores.
 */
enum class PollenSpecies(val displayName: String, val group: String) {
    ALDER("Alder", "Tree"),
    BIRCH("Birch", "Tree"),
    OLIVE("Olive", "Tree"),
    GRASS("Grass", "Grass"),
    MUGWORT("Mugwort", "Weed"),
    RAGWEED("Ragweed", "Weed")
}

private fun speciesIcon(species: PollenSpecies): ImageVector = when (species) {
    PollenSpecies.ALDER, PollenSpecies.BIRCH, PollenSpecies.OLIVE -> Icons.Default.Park
    PollenSpecies.GRASS -> Icons.Default.Grass
    PollenSpecies.MUGWORT, PollenSpecies.RAGWEED -> Icons.Default.Spa
}

private sealed interface PollenUiState {
    object Loading : PollenUiState
    object NoLocation : PollenUiState
    data class Ready(val data: PollenData) : PollenUiState
    data class Failed(val message: String) : PollenUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollenScreen(
    pollenData: PollenData?,
    onBack: () -> Unit,
    latitude: Double? = null,
    longitude: Double? = null
) {
    val scrollState = rememberScrollState()
    var state by remember { mutableStateOf<PollenUiState>(PollenUiState.Loading) }
    var reloadToken by remember { mutableStateOf(0) }

    LaunchedEffect(pollenData, latitude, longitude, reloadToken) {
        if (pollenData != null) {
            state = PollenUiState.Ready(pollenData)
            return@LaunchedEffect
        }
        if (latitude == null || longitude == null) {
            state = PollenUiState.NoLocation
            return@LaunchedEffect
        }

        state = PollenUiState.Loading
        state = OpenMeteoRepository.getPollen(latitude, longitude).fold(
            onSuccess = { PollenUiState.Ready(it) },
            onFailure = { PollenUiState.Failed(it.message ?: "Could not reach the pollen service") }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pollen & Allergens") },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(padding)
                    .padding(horizontal = 20.dp)
            ) {
                when (val current = state) {
                    is PollenUiState.Loading -> StatusCard(
                        icon = Icons.Default.LocalFlorist,
                        title = "Loading pollen data",
                        detail = null,
                        showSpinner = true
                    )

                    is PollenUiState.NoLocation -> StatusCard(
                        icon = Icons.Default.LocationOff,
                        title = "No location",
                        detail = "Pollen levels need a location. Pick one on the weather screen and come back."
                    )

                    is PollenUiState.Failed -> StatusCard(
                        icon = Icons.Default.CloudOff,
                        title = "Pollen data unavailable",
                        detail = current.message,
                        onRetry = { reloadToken++ }
                    )

                    is PollenUiState.Ready -> PollenReport(current.data)
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PollenReport(data: PollenData) {
    val peak = data.peak

    OverallPollenCard(peak = peak)

    // A non-null peak means at least one species came back with a figure. With none, there
    // are no levels to break down and no advice that is keyed off a real measurement, so
    // both sections are dropped rather than filled with placeholder levels.
    if (peak != null) {
        Spacer(Modifier.height(24.dp))

        Text(
            "Allergen Levels",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        Spacer(Modifier.height(12.dp))

        PollenSpecies.values().forEach { species ->
            AllergenCard(species = species, grains = data.readings[species])
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Allergy Tips",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        Spacer(Modifier.height(12.dp))

        AllergyTipsCard(level = pollenLevel(peak))
    }

    if (data.forecast.any { it.peak != null }) {
        Spacer(Modifier.height(24.dp))

        Text(
            "${data.forecast.size}-Day Pollen Forecast",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        Spacer(Modifier.height(12.dp))

        PollenForecastCard(forecast = data.forecast)
    }

    Spacer(Modifier.height(16.dp))

    Text(
        "Source: Open-Meteo / CAMS. Pollen forecasts cover Europe only; elsewhere no " +
            "species is reported.",
        fontSize = 11.sp,
        color = Color.White.copy(alpha = 0.5f)
    )
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    title: String,
    detail: String?,
    showSpinner: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color(0xFF667EEA),
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            if (detail != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    detail,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onRetry) {
                    Text("Try again", color = Color(0xFF667EEA))
                }
            }
        }
    }
}

@Composable
private fun OverallPollenCard(peak: Double?) {
    val level = peak?.let { pollenLevel(it) }
    val color = level?.let { getPollenColor(it) } ?: Color.White.copy(alpha = 0.4f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocalFlorist,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = color
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Highest Pollen Level",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(4.dp))

            Text(
                if (level != null) getPollenLabel(level) else "Not reported here",
                fontSize = if (level != null) 28.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )

            if (peak != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    formatGrains(peak),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            if (level != null) {
                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index < level) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < level) color else Color.White.copy(alpha = 0.2f)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllergenCard(species: PollenSpecies, grains: Double?) {
    val level = grains?.let { pollenLevel(it) }
    val color = level?.let { getPollenColor(it) } ?: Color.White.copy(alpha = 0.35f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
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
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = speciesIcon(species),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    species.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    if (grains != null) "${species.group} · ${formatGrains(grains)}" else species.group,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (level != null) getPollenLabel(level) else "Not reported",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
                if (level != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        repeat(5) { index ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index < level) color else Color.White.copy(alpha = 0.2f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllergyTipsCard(level: Int) {
    val tips = when {
        level <= 1 -> listOf(
            "Great day for outdoor activities!",
            "Minimal pollen impact expected"
        )
        level <= 2 -> listOf(
            "Consider taking antihistamines if sensitive",
            "Good day for most outdoor activities"
        )
        level <= 3 -> listOf(
            "Take allergy medication before going out",
            "Keep windows closed during peak hours (10 AM - 4 PM)",
            "Shower after outdoor activities"
        )
        level <= 4 -> listOf(
            "Limit outdoor time, especially mornings",
            "Wear sunglasses to protect eyes",
            "Use HEPA air filters indoors",
            "Change clothes after being outside"
        )
        else -> listOf(
            "Stay indoors as much as possible",
            "Keep all windows and doors closed",
            "Run air conditioning with clean filters",
            "Consult doctor if symptoms persist"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF667EEA).copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            tips.forEach { tip ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFF667EEA),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        tip,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PollenForecastCard(forecast: List<PollenDay>) {
    // The series is UTC (the air quality request sends no timezone), so "today" is worked
    // out in the same frame rather than the device's, which would mislabel it near midnight.
    val today = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            forecast.forEach { day ->
                val level = day.peak?.let { pollenLevel(it) }
                val color = level?.let { getPollenColor(it) } ?: Color.White.copy(alpha = 0.35f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        dayLabel(day.date, today),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            // A dash, not a zero: a day with no reading is unknown, not clear.
                            level?.toString() ?: "–",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

private fun dayLabel(isoDate: String, today: String): String {
    if (isoDate == today) return "Today"

    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(isoDate)
        if (parsed != null) {
            SimpleDateFormat("EEE", Locale.getDefault())
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(parsed)
        } else {
            isoDate
        }
    } catch (e: Exception) {
        isoDate
    }
}

private fun formatGrains(grains: Double): String =
    String.format(Locale.getDefault(), "%.1f grains/m³", grains)

/**
 * Bucket a grains/m³ reading onto the 0-5 scale the cards draw.
 *
 * These are generic cross-species bands, which is why the reading itself is always shown
 * next to the label — the number is the measurement, the band is only a colour hint.
 */
private fun pollenLevel(grains: Double): Int = when {
    grains <= 0.0 -> 0
    grains <= 10.0 -> 1
    grains <= 50.0 -> 2
    grains <= 100.0 -> 3
    grains <= 200.0 -> 4
    else -> 5
}

private fun getPollenColor(level: Int): Color = when (level) {
    0 -> Color(0xFF4CAF50)    // None - Green
    1 -> Color(0xFF8BC34A)    // Very Low - Light Green
    2 -> Color(0xFFFFEB3B)    // Low - Yellow
    3 -> Color(0xFFFF9800)    // Moderate - Orange
    4 -> Color(0xFFFF5722)    // High - Red Orange
    else -> Color(0xFFE91E63) // Very High - Pink/Red
}

private fun getPollenLabel(level: Int): String = when (level) {
    0 -> "None"
    1 -> "Very Low"
    2 -> "Low"
    3 -> "Moderate"
    4 -> "High"
    else -> "Very High"
}
