package com.climaai.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.compose.ui.graphics.Color

private val WearColors = Colors(
    primary = Color(0xFF667EEA),
    primaryVariant = Color(0xFF5A67D8),
    secondary = Color(0xFF38EF7D),
    secondaryVariant = Color(0xFF2DD970),
    background = Color(0xFF0F0F1A),
    surface = Color(0xFF1A1A2E),
    error = Color(0xFFFF5252),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.White
)

@Composable
fun ClimaAIWearTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = WearColors,
        content = content
    )
}
