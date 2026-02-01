package com.climaai.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

/**
 * Data class for animated particles.
 */
data class Particle(
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float,
    val alpha: Float,
    val angle: Float = 0f
)

/**
 * Animated weather background with particles.
 * 
 * Supports:
 * - Rain droplets
 * - Snow flakes
 * - Sun rays
 * - Stars (night)
 * - Clouds (drifting)
 */
@Composable
fun AnimatedWeatherBackground(
    weatherCode: Int,
    isDay: Boolean = true,
    modifier: Modifier = Modifier
) {
    val backgroundType = getBackgroundType(weatherCode, isDay)
    
    when (backgroundType) {
        BackgroundType.CLEAR_DAY -> SunnyBackground(modifier)
        BackgroundType.CLEAR_NIGHT -> StarryBackground(modifier)
        BackgroundType.CLOUDY -> CloudyBackground(modifier)
        BackgroundType.RAIN -> RainBackground(modifier)
        BackgroundType.SNOW -> SnowBackground(modifier)
        BackgroundType.STORM -> StormBackground(modifier)
    }
}

enum class BackgroundType { CLEAR_DAY, CLEAR_NIGHT, CLOUDY, RAIN, SNOW, STORM }

private fun getBackgroundType(code: Int, isDay: Boolean): BackgroundType = when (code) {
    0 -> if (isDay) BackgroundType.CLEAR_DAY else BackgroundType.CLEAR_NIGHT
    1, 2, 3 -> BackgroundType.CLOUDY
    in 45..48 -> BackgroundType.CLOUDY
    in 51..67 -> BackgroundType.RAIN
    in 71..77 -> BackgroundType.SNOW
    in 80..82 -> BackgroundType.RAIN
    in 85..86 -> BackgroundType.SNOW
    in 95..99 -> BackgroundType.STORM
    else -> if (isDay) BackgroundType.CLEAR_DAY else BackgroundType.CLEAR_NIGHT
}

/**
 * Sunny day with subtle shimmer rays.
 */
@Composable
fun SunnyBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "sunny")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        // Sun glow in corner
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF59D).copy(alpha = 0.4f * shimmer + 0.2f),
                    Color(0xFFFFE082).copy(alpha = 0.2f * shimmer),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.85f, size.height * 0.1f),
                radius = size.minDimension * 0.5f
            ),
            radius = size.minDimension
        )
        
        // Subtle rays
        for (i in 0..5) {
            val angle = i * 30f + shimmer * 10
            drawLine(
                color = Color.White.copy(alpha = 0.05f + shimmer * 0.05f),
                start = Offset(size.width * 0.85f, size.height * 0.1f),
                end = Offset(
                    size.width * 0.85f + kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat() * size.width * 0.5f,
                    size.height * 0.1f + kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat() * size.height * 0.5f
                ),
                strokeWidth = 40f
            )
        }
    }
}

/**
 * Starry night with twinkling stars.
 */
@Composable
fun StarryBackground(modifier: Modifier = Modifier) {
    val stars = remember {
        List(50) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 3f + 1f,
                speed = Random.nextFloat() * 2f + 1f,
                alpha = Random.nextFloat()
            )
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val twinkle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        stars.forEachIndexed { index, star ->
            val alpha = (star.alpha + twinkle * if (index % 2 == 0) 0.3f else -0.3f)
                .coerceIn(0.1f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = star.size,
                center = Offset(star.x * size.width, star.y * size.height)
            )
        }
        
        // Moon glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFF5F5F5).copy(alpha = 0.3f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.8f, size.height * 0.15f),
                radius = 100f
            ),
            radius = 150f,
            center = Offset(size.width * 0.8f, size.height * 0.15f)
        )
    }
}

/**
 * Rain with falling droplets.
 */
@Composable
fun RainBackground(modifier: Modifier = Modifier) {
    val drops = remember {
        List(80) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 15f + 10f,
                speed = Random.nextFloat() * 5f + 8f,
                alpha = Random.nextFloat() * 0.3f + 0.1f
            )
        }
    }
    
    var time by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            time += 0.02f
            if (time > 1f) time = 0f
            kotlinx.coroutines.delay(16)
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        drops.forEach { drop ->
            val y = (drop.y + time * drop.speed) % 1.2f
            if (y < 1f) {
                drawLine(
                    color = Color(0xFF87CEEB).copy(alpha = drop.alpha),
                    start = Offset(drop.x * size.width, y * size.height),
                    end = Offset(drop.x * size.width, (y + drop.size / size.height) * size.height),
                    strokeWidth = 2f
                )
            }
        }
    }
}

/**
 * Snow with floating flakes.
 */
@Composable
fun SnowBackground(modifier: Modifier = Modifier) {
    val flakes = remember {
        List(60) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 4f + 2f,
                speed = Random.nextFloat() * 2f + 1f,
                alpha = Random.nextFloat() * 0.5f + 0.3f,
                angle = Random.nextFloat() * 360f
            )
        }
    }
    
    var time by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            time += 0.005f
            if (time > 1f) time = 0f
            kotlinx.coroutines.delay(16)
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        flakes.forEach { flake ->
            val y = (flake.y + time * flake.speed) % 1.1f
            val x = flake.x + kotlin.math.sin(y * 10 + flake.angle) * 0.02f
            if (y < 1f && x in 0f..1f) {
                drawCircle(
                    color = Color.White.copy(alpha = flake.alpha),
                    radius = flake.size,
                    center = Offset(x * size.width, y * size.height)
                )
            }
        }
    }
}

/**
 * Cloudy with drifting clouds.
 */
@Composable
fun CloudyBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "clouds")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        // Cloud layers
        listOf(0.15f, 0.25f, 0.35f).forEachIndexed { index, yPos ->
            val xOffset = (drift + index * 0.3f) % 1f
            drawCircle(
                color = Color.White.copy(alpha = 0.08f - index * 0.02f),
                radius = size.width * 0.25f,
                center = Offset(xOffset * size.width * 1.5f - size.width * 0.25f, size.height * yPos)
            )
        }
    }
}

/**
 * Storm with lightning flashes.
 */
@Composable
fun StormBackground(modifier: Modifier = Modifier) {
    var flashAlpha by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(Random.nextLong(2000, 5000))
            flashAlpha = 0.3f
            kotlinx.coroutines.delay(50)
            flashAlpha = 0f
            kotlinx.coroutines.delay(100)
            flashAlpha = 0.15f
            kotlinx.coroutines.delay(50)
            flashAlpha = 0f
        }
    }
    
    // Combine rain with lightning
    RainBackground(modifier)
    
    Canvas(modifier = modifier.fillMaxSize()) {
        if (flashAlpha > 0) {
            drawRect(Color.White.copy(alpha = flashAlpha))
        }
    }
}
