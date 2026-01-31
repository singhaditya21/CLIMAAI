package com.climaai.app.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore for theme preferences
val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

/**
 * Available app themes
 */
enum class AppTheme(
    val displayName: String,
    val icon: String
) {
    SYSTEM("System", "settings"),
    LIGHT("Light", "light_mode"),
    DARK("Dark", "dark_mode"),
    OCEAN("Ocean", "water"),
    SUNSET("Sunset", "wb_twilight");
    
    val isDark: Boolean?
        get() = when (this) {
            SYSTEM -> null
            LIGHT, SUNSET -> false
            DARK, OCEAN -> true
        }
    
    val accentColor: Color
        get() = when (this) {
            SYSTEM, LIGHT, DARK -> Color(0xFF4A90E2)  // Blue
            OCEAN -> Color(0xFF00CCD6)  // Cyan
            SUNSET -> Color(0xFFFF8050)  // Orange-red
        }
    
    val secondaryColor: Color
        get() = when (this) {
            SYSTEM, LIGHT, DARK -> Color(0xFF00BCD4)  // Cyan
            OCEAN -> Color(0xFF0066B3)  // Deep blue
            SUNSET -> Color(0xFFE64980)  // Pink
        }
    
    val backgroundGradient: List<Color>
        get() = when (this) {
            SYSTEM, LIGHT -> listOf(Color(0xFFF2F2F2), Color(0xFFE6E6E6))
            DARK -> listOf(Color(0xFF1A1A1A), Color(0xFF262626))
            OCEAN -> listOf(Color(0xFF003366), Color(0xFF001A33))
            SUNSET -> listOf(Color(0xFFFFD9B3), Color(0xFFFFB399))
        }
}

/**
 * Theme manager for app-wide theme state
 */
class ThemeManager(private val context: Context) {
    
    companion object {
        private val THEME_KEY = stringPreferencesKey("selected_theme")
        
        @Volatile
        private var INSTANCE: ThemeManager? = null
        
        fun getInstance(context: Context): ThemeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    val selectedTheme: Flow<AppTheme> = context.themeDataStore.data.map { preferences ->
        val themeName = preferences[THEME_KEY] ?: AppTheme.SYSTEM.name
        try {
            AppTheme.valueOf(themeName)
        } catch (e: IllegalArgumentException) {
            AppTheme.SYSTEM
        }
    }
    
    suspend fun setTheme(theme: AppTheme) {
        context.themeDataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
        }
    }
}

/**
 * Composable local for theme state
 */
val LocalAppTheme = compositionLocalOf { AppTheme.SYSTEM }

/**
 * Theme provider composable
 */
@Composable
fun ClimaAIThemeProvider(
    themeManager: ThemeManager,
    content: @Composable () -> Unit
) {
    val theme by themeManager.selectedTheme.collectAsState(initial = AppTheme.SYSTEM)
    
    CompositionLocalProvider(LocalAppTheme provides theme) {
        content()
    }
}

/**
 * Helper to determine if dark theme should be used
 */
@Composable
fun shouldUseDarkTheme(theme: AppTheme = LocalAppTheme.current): Boolean {
    return when (theme.isDark) {
        true -> true
        false -> false
        null -> isSystemInDarkTheme()
    }
}
