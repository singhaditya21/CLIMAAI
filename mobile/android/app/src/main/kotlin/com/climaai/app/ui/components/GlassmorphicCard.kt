package com.climaai.app.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Premium glassmorphic card with blur backdrop, gradient border, and subtle glow.
 * 
 * Features:
 * - Frosted glass effect with blur
 * - Animated gradient border
 * - Inner shadow for depth
 * - Customizable opacity and blur radius
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    blurRadius: Dp = 20.dp,
    backgroundColor: Color = Color.White.copy(alpha = 0.08f),
    borderWidth: Dp = 1.dp,
    animatedBorder: Boolean = true,
    glowColor: Color = Color(0xFF667EEA),
    content: @Composable () -> Unit
) {
    // Animated gradient rotation for border
    val infiniteTransition = rememberInfiniteTransition(label = "border")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "borderRotation"
    )
    
    val gradientBrush = if (animatedBorder) {
        Brush.sweepGradient(
            0f to Color(0xFF667EEA).copy(alpha = 0.6f),
            0.25f to Color(0xFF764BA2).copy(alpha = 0.3f),
            0.5f to Color(0xFF667EEA).copy(alpha = 0.1f),
            0.75f to Color(0xFF764BA2).copy(alpha = 0.3f),
            1f to Color(0xFF667EEA).copy(alpha = 0.6f)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.3f),
                Color.White.copy(alpha = 0.1f)
            )
        )
    }
    
    val shape = RoundedCornerShape(cornerRadius)
    
    Surface(
        modifier = modifier
            .clip(shape)
            .border(borderWidth, gradientBrush, shape)
            .drawBehind {
                // Subtle outer glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        radius = size.maxDimension * 0.6f
                    )
                )
            }
            .graphicsLayer {
                // Apply blur effect on Android 12+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = RenderEffect.createBlurEffect(
                        blurRadius.toPx() / 4,
                        blurRadius.toPx() / 4,
                        Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                }
            },
        color = backgroundColor,
        shape = shape,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    )
                )
        ) {
            content()
        }
    }
}

/**
 * Simplified glassmorphic card for older devices without blur.
 */
@Composable
fun GlassmorphicCardCompat(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    
    Surface(
        modifier = modifier
            .clip(shape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape = shape
            ),
        color = Color.White.copy(alpha = 0.1f),
        shape = shape
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.05f),
                        Color.Transparent
                    )
                )
            )
        ) {
            content()
        }
    }
}

/**
 * Quick stats card with glassmorphism.
 */
@Composable
fun GlassStatCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF60A5FA),
    content: @Composable () -> Unit
) {
    GlassmorphicCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        glowColor = accentColor,
        animatedBorder = false,
        content = content
    )
}
