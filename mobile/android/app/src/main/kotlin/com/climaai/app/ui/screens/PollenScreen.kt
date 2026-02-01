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

/**
 * Pollen data model
 */
data class PollenData(
    val tree: Int = 0,        // 0-5 scale
    val grass: Int = 0,
    val weed: Int = 0,
    val mold: Int = 0,
    val overall: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollenScreen(
    pollenData: PollenData?,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    // Mock data if null
    val data = pollenData ?: PollenData(
        tree = 3,
        grass = 2,
        weed = 1,
        mold = 2,
        overall = 3
    )
    
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
                // Overall pollen level
                OverallPollenCard(level = data.overall)
                
                Spacer(Modifier.height(24.dp))
                
                // Individual allergens
                Text(
                    "Allergen Levels",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                
                Spacer(Modifier.height(12.dp))
                
                AllergenCard(
                    name = "Tree Pollen",
                    level = data.tree,
                    icon = Icons.Default.Park,
                    description = "Oak, Birch, Cedar, Pine"
                )
                
                Spacer(Modifier.height(12.dp))
                
                AllergenCard(
                    name = "Grass Pollen",
                    level = data.grass,
                    icon = Icons.Default.Grass,
                    description = "Bermuda, Timothy, Kentucky Blue"
                )
                
                Spacer(Modifier.height(12.dp))
                
                AllergenCard(
                    name = "Weed Pollen",
                    level = data.weed,
                    icon = Icons.Default.Spa,
                    description = "Ragweed, Sagebrush, Pigweed"
                )
                
                Spacer(Modifier.height(12.dp))
                
                AllergenCard(
                    name = "Mold Spores",
                    level = data.mold,
                    icon = Icons.Default.Grain,
                    description = "Outdoor mold concentration"
                )
                
                Spacer(Modifier.height(24.dp))
                
                // Tips section
                Text(
                    "Allergy Tips",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                
                Spacer(Modifier.height(12.dp))
                
                AllergyTipsCard(overallLevel = data.overall)
                
                Spacer(Modifier.height(24.dp))
                
                // Forecast preview
                Text(
                    "5-Day Pollen Forecast",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                
                Spacer(Modifier.height(12.dp))
                
                PollenForecastCard()
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun OverallPollenCard(level: Int) {
    val color = getPollenColor(level)
    val label = getPollenLabel(level)
    
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
                "Overall Pollen Level",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                label,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            
            Spacer(Modifier.height(8.dp))
            
            // Level indicator dots
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

@Composable
private fun AllergenCard(
    name: String,
    level: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String
) {
    val color = getPollenColor(level)
    
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    description,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    getPollenLabel(level),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
                // Mini level bar
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

@Composable
private fun AllergyTipsCard(overallLevel: Int) {
    val tips = when {
        overallLevel <= 1 -> listOf(
            "Great day for outdoor activities!",
            "Minimal pollen impact expected"
        )
        overallLevel <= 2 -> listOf(
            "Consider taking antihistamines if sensitive",
            "Good day for most outdoor activities"
        )
        overallLevel <= 3 -> listOf(
            "Take allergy medication before going out",
            "Keep windows closed during peak hours (10 AM - 4 PM)",
            "Shower after outdoor activities"
        )
        overallLevel <= 4 -> listOf(
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
                        Icons.Default.LightbulbOutline,
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
private fun PollenForecastCard() {
    val forecast = listOf(
        Triple("Today", 3, "Moderate"),
        Triple("Tomorrow", 4, "High"),
        Triple("Wed", 4, "High"),
        Triple("Thu", 3, "Moderate"),
        Triple("Fri", 2, "Low")
    )
    
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
            forecast.forEach { (day, level, _) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        day,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(getPollenColor(level).copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            level.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = getPollenColor(level)
                        )
                    }
                }
            }
        }
    }
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
