package com.climaai.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated weather icon with pulsing glow and rotation effects.
 */
@Composable
fun AnimatedWeatherIcon(
    weatherCode: Int,
    isDay: Boolean = true,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    tint: Color = Color(0xFF87CEEB)
) {
    val icon = getWeatherIconVector(weatherCode, isDay)
    val animationType = getAnimationType(weatherCode)
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Glow effect
        AnimatedGlow(
            color = tint,
            size = size,
            animationType = animationType
        )
        
        // Animated icon
        when (animationType) {
            AnimationType.PULSE -> PulsingIcon(icon, tint, size * 0.7f)
            AnimationType.ROTATE -> RotatingIcon(icon, tint, size * 0.7f)
            AnimationType.BOUNCE -> BouncingIcon(icon, tint, size * 0.7f)
            AnimationType.SHAKE -> ShakingIcon(icon, tint, size * 0.7f)
            AnimationType.NONE -> StaticIcon(icon, tint, size * 0.7f)
        }
    }
}

enum class AnimationType { PULSE, ROTATE, BOUNCE, SHAKE, NONE }

private fun getAnimationType(code: Int): AnimationType = when (code) {
    0 -> AnimationType.PULSE          // Clear - sun pulses
    1, 2 -> AnimationType.NONE        // Partly cloudy
    3 -> AnimationType.NONE          // Overcast
    in 45..48 -> AnimationType.NONE   // Fog
    in 51..67 -> AnimationType.BOUNCE // Rain - droplets bounce
    in 71..77 -> AnimationType.SHAKE  // Snow - shake effect
    in 80..82 -> AnimationType.BOUNCE // Showers
    in 95..99 -> AnimationType.SHAKE  // Thunderstorm
    else -> AnimationType.NONE
}

@Composable
private fun AnimatedGlow(
    color: Color,
    size: Dp,
    animationType: AnimationType
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    
    Canvas(modifier = Modifier.size(size)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = alpha * scale),
                    Color.Transparent
                ),
                radius = this.size.minDimension * scale
            )
        )
    }
}

@Composable
private fun PulsingIcon(icon: ImageVector, tint: Color, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        tint = tint
    )
}

@Composable
private fun RotatingIcon(icon: ImageVector, tint: Color, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation },
        tint = tint
    )
}

@Composable
private fun BouncingIcon(icon: ImageVector, tint: Color, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceY"
    )
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .graphicsLayer { translationY = offsetY },
        tint = tint
    )
}

@Composable
private fun ShakingIcon(icon: ImageVector, tint: Color, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shakeRotation"
    )
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation },
        tint = tint
    )
}

@Composable
private fun StaticIcon(icon: ImageVector, tint: Color, size: Dp) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = tint
    )
}

/**
 * Get weather icon vector for a WMO code.
 */
fun getWeatherIconVector(code: Int, isDay: Boolean = true): ImageVector = when (code) {
    0 -> if (isDay) Icons.Default.WbSunny else Icons.Default.NightsStay
    1 -> if (isDay) Icons.Default.WbCloudy else Icons.Default.Cloud
    2 -> Icons.Default.Cloud
    3 -> Icons.Default.Cloud
    in 45..48 -> Icons.Default.Cloud  // Fog
    in 51..55 -> Icons.Default.Grain  // Drizzle
    in 56..57 -> Icons.Default.AcUnit // Freezing drizzle
    in 61..65 -> Icons.Default.Grain  // Rain
    in 66..67 -> Icons.Default.AcUnit // Freezing rain
    in 71..77 -> Icons.Default.AcUnit // Snow
    in 80..82 -> Icons.Default.Grain  // Rain showers
    in 85..86 -> Icons.Default.AcUnit // Snow showers
    in 95..99 -> Icons.Default.Thunderstorm
    else -> if (isDay) Icons.Default.WbSunny else Icons.Default.NightsStay
}
