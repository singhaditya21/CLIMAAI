package com.climaai.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Animated temperature counter that animates from 0 to target value.
 */
@Composable
fun AnimatedTemperature(
    temperature: Double,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 72.sp,
    fontWeight: FontWeight = FontWeight.Light,
    color: Color = Color.White,
    animationDuration: Int = 1000
) {
    var targetTemp by remember { mutableDoubleStateOf(0.0) }
    
    LaunchedEffect(temperature) {
        targetTemp = temperature
    }
    
    val animatedTemp by animateFloatAsState(
        targetValue = targetTemp.toFloat(),
        animationSpec = tween(
            durationMillis = animationDuration,
            easing = FastOutSlowInEasing
        ),
        label = "tempAnimation"
    )
    
    // Scale animation on value change
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scaleAnimation"
    )
    
    Text(
        text = "${animatedTemp.toInt()}°",
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

/**
 * Animated number counter for any numeric value.
 */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Medium,
    color: Color = Color.White,
    prefix: String = "",
    suffix: String = ""
) {
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "counterAnimation"
    )
    
    Text(
        text = "$prefix$animatedValue$suffix",
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

/**
 * Gradient text effect.
 */
@Composable
fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 72.sp,
    fontWeight: FontWeight = FontWeight.Light,
    colors: List<Color> = listOf(Color.White, Color(0xFF87CEEB))
) {
    // For now, use simple text - gradient requires custom draw
    // This is a placeholder that can be enhanced with Canvas drawing
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = colors.first(),
        modifier = modifier
    )
}

/**
 * Animated text that fades in on appear.
 */
@Composable
fun FadeInText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.White,
    delay: Int = 0
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "fadeIn"
    )
    
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color.copy(alpha = alpha),
        modifier = modifier
    )
}
