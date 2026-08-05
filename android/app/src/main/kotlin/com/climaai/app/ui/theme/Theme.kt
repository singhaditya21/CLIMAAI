package com.climaai.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.climaai.app.data.AppearancePrefs
import com.climaai.app.data.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    secondary = Color(0xFFA78BFA),
    tertiary = Color(0xFF22D3EE),
    background = Color(0xFF0D1B2A),
    surface = Color(0xFF1E3A5F),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3B82F6),
    secondary = Color(0xFF8B5CF6),
    tertiary = Color(0xFF06B6D4),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1E293B),
    onSurface = Color(0xFF1E293B)
)

@Composable
fun ClimaAITheme(
    dynamicColor: Boolean = false, // Disable dynamic color by default for consistent branding
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // The persisted appearance preference is the single source of truth for
    // light/dark — the same store AppearanceSettingsScreen writes to, so a
    // selection there restyles the whole app immediately. SYSTEM defers to the
    // OS; OLED is a dark variant, so it maps onto the dark palette until a
    // dedicated true-black scheme exists.
    val prefs = remember(context) { AppearancePrefs(context) }
    val themeMode by prefs.themeMode.collectAsState(initial = AppearancePrefs.DEFAULT_THEME)
    val darkTheme = when (ThemeMode.fromId(themeMode)) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.OLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PremiumTypography,
        content = content
    )
}
