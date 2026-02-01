package com.climaai.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

/**
 * Haptic feedback types mapped to Android constants.
 */
enum class HapticType {
    LIGHT,      // Light tap - card selection
    MEDIUM,     // Medium tap - button press, refresh
    HEAVY,      // Heavy tap - alerts, confirmations
    SUCCESS,    // Success action
    ERROR       // Error action
}

/**
 * Perform haptic feedback on the current view.
 */
@Composable
fun rememberHapticFeedback(): (HapticType) -> Unit {
    val view = LocalView.current
    return remember {
        { type: HapticType ->
            performHaptic(view, type)
        }
    }
}

private fun performHaptic(view: View, type: HapticType) {
    val feedbackConstant = when (type) {
        HapticType.LIGHT -> HapticFeedbackConstants.CLOCK_TICK
        HapticType.MEDIUM -> HapticFeedbackConstants.CONTEXT_CLICK
        HapticType.HEAVY -> HapticFeedbackConstants.LONG_PRESS
        HapticType.SUCCESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        HapticType.ERROR -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
    }
    view.performHapticFeedback(feedbackConstant)
}

/**
 * Modifier for clickable with haptic feedback.
 */
fun Modifier.clickableWithHaptic(
    hapticType: HapticType = HapticType.LIGHT,
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(color = Color.White.copy(alpha = 0.3f))
    ) {
        performHaptic(view, hapticType)
        onClick()
    }
}

/**
 * Modifier for press scale animation.
 */
fun Modifier.pressScale(
    pressedScale: Float = 0.96f
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Card entrance animation modifier.
 */
fun Modifier.cardEntrance(
    delay: Int = 0,
    duration: Int = 400
): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(duration),
        label = "cardAlpha"
    )
    
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 30f,
        animationSpec = tween(duration, easing = FastOutSlowInEasing),
        label = "cardOffset"
    )
    
    this.graphicsLayer {
        this.alpha = alpha
        translationY = offsetY
    }
}

/**
 * Shimmer loading effect modifier.
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    
    this.graphicsLayer {
        // Apply shimmer via graphics layer transform
        translationX = translateX
    }
}

/**
 * Bounce on appear modifier.
 */
fun Modifier.bounceOnAppear(
    targetScale: Float = 1f,
    initialScale: Float = 0.8f
): Modifier = composed {
    var appeared by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        appeared = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (appeared) targetScale else initialScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceScale"
    )
    
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
