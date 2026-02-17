package com.climaai.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

/**
 * Animated weather background with particles
 */
@Composable
fun WeatherBackground(
    weatherCode: Int,
    isDay: Boolean,
    modifier: Modifier = Modifier
) {
    val weatherType = getWeatherType(weatherCode)
    
    // Animation for particles
    val infiniteTransition = rememberInfiniteTransition(label = "weather")
    val animationPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    
    // Generate particles
    val particles = remember(weatherType) {
        generateParticles(weatherType)
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = getBackgroundColors(weatherType, isDay)
                    )
                )
        )
        
        // Particles canvas
        if (particles.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                particles.forEach { particle ->
                    val yPos = ((particle.y + animationPhase * particle.speed) % 1.2f) * size.height
                    val xPos = particle.x * size.width
                    
                    when (weatherType) {
                        WeatherType.RAIN -> drawRainDrop(xPos, yPos, particle.opacity)
                        WeatherType.SNOW -> drawSnowflake(xPos, yPos, particle.size, particle.opacity)
                        else -> {}
                    }
                }
            }
        }
    }
}

private enum class WeatherType {
    CLEAR, CLOUDY, RAIN, SNOW, THUNDER, FOG
}

private fun getWeatherType(code: Int): WeatherType = when (code) {
    0 -> WeatherType.CLEAR
    1, 2, 3 -> WeatherType.CLOUDY
    45, 48 -> WeatherType.FOG
    in 51..67, in 80..82 -> WeatherType.RAIN
    in 71..77, 85, 86 -> WeatherType.SNOW
    in 95..99 -> WeatherType.THUNDER
    else -> WeatherType.CLOUDY
}

private fun getBackgroundColors(type: WeatherType, isDay: Boolean): List<Color> = when (type) {
    WeatherType.CLEAR -> if (isDay) {
        listOf(Color(0xFF66B3FF), Color(0xFF3380CC))
    } else {
        listOf(Color(0xFF1A1A4D), Color(0xFF0D0D26))
    }
    WeatherType.CLOUDY -> if (isDay) {
        listOf(Color(0xFF99B3CC), Color(0xFF8099B3))
    } else {
        listOf(Color(0xFF33334D), Color(0xFF1A1A33))
    }
    WeatherType.RAIN -> listOf(Color(0xFF4D6680), Color(0xFF334D66))
    WeatherType.SNOW -> if (isDay) {
        listOf(Color(0xFFCCD9E6), Color(0xFFB3C0CC))
    } else {
        listOf(Color(0xFF4D5966), Color(0xFF33404D))
    }
    WeatherType.THUNDER -> listOf(Color(0xFF333359), Color(0xFF1A1A33))
    WeatherType.FOG -> listOf(Color(0xFF9999A6), Color(0xFF80808C))
}

private data class WeatherParticle(
    val x: Float,
    val y: Float,
    val speed: Float,
    val size: Float,
    val opacity: Float
)

private fun generateParticles(type: WeatherType): List<WeatherParticle> {
    val count = when (type) {
        WeatherType.RAIN -> 100
        WeatherType.SNOW -> 60
        else -> 0
    }
    
    return List(count) {
        WeatherParticle(
            x = Random.nextFloat(),
            y = Random.nextFloat() * 1.2f - 0.2f,
            speed = Random.nextFloat() * 0.7f + 0.3f,
            size = Random.nextFloat() * 8f + 4f,
            opacity = Random.nextFloat() * 0.5f + 0.3f
        )
    }
}

private fun DrawScope.drawRainDrop(x: Float, y: Float, opacity: Float) {
    drawLine(
        color = Color.White.copy(alpha = opacity),
        start = Offset(x, y),
        end = Offset(x, y + 15f),
        strokeWidth = 1.5f
    )
}

private fun DrawScope.drawSnowflake(x: Float, y: Float, size: Float, opacity: Float) {
    drawCircle(
        color = Color.White.copy(alpha = opacity),
        radius = size / 2,
        center = Offset(x, y)
    )
}
