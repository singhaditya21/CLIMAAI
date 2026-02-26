package com.climaai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

/**
 * Data class for precipitation nowcast
 */
data class PrecipitationNowcast(
    val hasPrecipitation: Boolean,
    val precipitationInMinutes: Int?,
    val precipitationEndsInMinutes: Int?,
    val intensity: String,  // none, light, moderate, heavy
    val precipitationType: String,  // none, rain, snow, mixed
    val probability: Int,
    val summary: String
)

/**
 * Precipitation alert banner - "Rain in X minutes"
 */
@Composable
fun PrecipitationBanner(
    nowcast: PrecipitationNowcast,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shouldShow = nowcast.hasPrecipitation && 
        (nowcast.precipitationInMinutes ?: 0) <= 120
    
    if (!shouldShow) return
    
    val borderColor = when (nowcast.intensity) {
        "heavy" -> Color(0xFFFF6B6B)
        "moderate" -> Color(0xFFFFAA33)
        else -> Color(0xFF4A90E2)
    }
    
    val probabilityColor = when {
        nowcast.probability >= 80 -> Color(0xFFFF6B6B)
        nowcast.probability >= 50 -> Color(0xFFFF9800)
        else -> Color(0xFF4A90E2)
    }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(borderColor.copy(alpha = 0.5f), borderColor.copy(alpha = 0.2f))
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Text(
                text = when (nowcast.precipitationType) {
                    "rain" -> "🌧️"
                    "snow" -> "❄️"
                    "mixed" -> "🌨️"
                    else -> "☁️"
                },
                style = MaterialTheme.typography.headlineMedium
            )
            
            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nowcast.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                if ((nowcast.precipitationInMinutes ?: 0) > 0) {
                    Text(
                        text = "Tap for details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Probability badge
            if (nowcast.probability > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(probabilityColor.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${nowcast.probability}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = probabilityColor
                    )
                }
            }
        }
    }
}
