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
import com.climaai.app.data.AccentColor
import com.climaai.app.data.AppearancePrefs
import com.climaai.app.data.FontSizeOption
import com.climaai.app.data.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    secondary = Color(0xFFA78BFA),
    tertiary = Color(0xFF22D3EE),
    background = Color(0xFF0B1624),
    surface = Color(0xFF162C46),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val OledColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    secondary = Color(0xFFA78BFA),
    tertiary = Color(0xFF22D3EE),
    background = Color(0xFF000000),
    surface = Color(0xFF0A0F18),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    secondary = Color(0xFF7C3AED),
    tertiary = Color(0xFF0891B2),
    background = Color(0xFFF4F8FB),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F253E),
    onSurface = Color(0xFF0F253E)
)

@Composable
fun ClimaAITheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val prefs = remember(context) { AppearancePrefs(context) }
    val themeModeRaw by prefs.themeMode.collectAsState(initial = AppearancePrefs.DEFAULT_THEME)
    val accentId by prefs.accentColor.collectAsState(initial = AppearancePrefs.DEFAULT_ACCENT)
    val dynamicPref by prefs.useDynamicColor.collectAsState(initial = dynamicColor)
    val fontSizeId by prefs.fontSize.collectAsState(initial = AppearancePrefs.DEFAULT_FONT_SIZE)

    val mode = ThemeMode.fromId(themeModeRaw)
    val darkTheme = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.OLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val accent = Color(AccentColor.fromId(accentId).hex)
    val baseScheme = when {
        dynamicPref && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        mode == ThemeMode.OLED -> OledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val colorScheme = if (dynamicPref && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        baseScheme
    } else {
        baseScheme.copy(primary = accent)
    }

    val fontScale = FontSizeOption.fromId(fontSizeId).scale

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PremiumTypography.scaled(fontScale),
        content = content
    )
}
