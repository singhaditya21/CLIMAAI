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
    backgroundColor: Color = Color.White.copy(alpha = 0.09f),
    borderWidth: Dp = 1.dp,
    animatedBorder: Boolean = false,
    glowColor: Color = Color(0xFF60A5FA),
    content: @Composable () -> Unit
) {
    val gradientBrush = if (animatedBorder) {
        val infiniteTransition = rememberInfiniteTransition(label = "border")
        val alphaPulse by infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "borderPulse"
        )
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = alphaPulse),
                glowColor.copy(alpha = alphaPulse * 0.8f),
                Color.White.copy(alpha = 0.10f)
            )
        )
    } else {
        // Directional top-left specular rim highlight for optical glass depth
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.32f),
                Color.White.copy(alpha = 0.08f),
                glowColor.copy(alpha = 0.16f)
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
            // No renderEffect here. A renderEffect on this Surface's own
            // graphics layer blurs everything drawn *in* that layer — which is
            // the card's own children — so every temperature, label and icon
            // rendered unreadable. Glassmorphism blurs the backdrop behind a
            // card, not its contents.
            //
            // The glass look comes from the translucent surface colour, the
            // gradient border, the radial glow above and the inner white
            // gradient below. A true backdrop blur needs the background
            // composed into a separate layer and blurred there; worth doing
            // properly rather than by blurring the text.
            ,
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
