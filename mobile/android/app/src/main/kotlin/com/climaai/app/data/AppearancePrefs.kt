package com.climaai.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "appearance_prefs")

/**
 * Manages app appearance preferences using DataStore.
 */
class AppearancePrefs(private val context: Context) {
    
    companion object {
        // Theme
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        
        // Font
        private val FONT_SIZE = stringPreferencesKey("font_size")
        
        // Icons
        private val ICON_STYLE = stringPreferencesKey("icon_style")
        
        // Display
        private val TEMPERATURE_DISPLAY = stringPreferencesKey("temperature_display")
        private val SHOW_ANIMATIONS = booleanPreferencesKey("show_animations")
        
        // Defaults
        const val DEFAULT_THEME = "system"
        const val DEFAULT_ACCENT = "blue"
        const val DEFAULT_FONT_SIZE = "medium"
        const val DEFAULT_ICON_STYLE = "filled"
        const val DEFAULT_TEMP_DISPLAY = "large"
    }
    
    // Theme Mode: light, dark, system, oled
    val themeMode: Flow<String> = context.appearanceDataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: DEFAULT_THEME
    }
    
    suspend fun setThemeMode(mode: String) {
        context.appearanceDataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }
    
    // Accent Color: blue, purple, teal, coral, custom
    val accentColor: Flow<String> = context.appearanceDataStore.data.map { prefs ->
        prefs[ACCENT_COLOR] ?: DEFAULT_ACCENT
    }
    
    suspend fun setAccentColor(color: String) {
        context.appearanceDataStore.edit { prefs ->
            prefs[ACCENT_COLOR] = color
        }
    }
    
    // Dynamic Color (Material You)
    val useDynamicColor: Flow<Boolean> = context.appearanceDataStore.data.map { prefs ->
        prefs[USE_DYNAMIC_COLOR] ?: false
    }
    
    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.appearanceDataStore.edit { prefs ->
            prefs[USE_DYNAMIC_COLOR] = enabled
        }
    }
    
    // Font Size: small, medium, large
    val fontSize: Flow<String> = context.appearanceDataStore.data.map { prefs ->
        prefs[FONT_SIZE] ?: DEFAULT_FONT_SIZE
    }
    
    suspend fun setFontSize(size: String) {
        context.appearanceDataStore.edit { prefs ->
            prefs[FONT_SIZE] = size
        }
    }
    
    // Icon Style: filled, outlined, animated
    val iconStyle: Flow<String> = context.appearanceDataStore.data.map { prefs ->
        prefs[ICON_STYLE] ?: DEFAULT_ICON_STYLE
    }
    
    suspend fun setIconStyle(style: String) {
        context.appearanceDataStore.edit { prefs ->
            prefs[ICON_STYLE] = style
        }
    }
    
    // Temperature Display: large, compact
    val temperatureDisplay: Flow<String> = context.appearanceDataStore.data.map { prefs ->
        prefs[TEMPERATURE_DISPLAY] ?: DEFAULT_TEMP_DISPLAY
    }
    
    suspend fun setTemperatureDisplay(display: String) {
        context.appearanceDataStore.edit { prefs ->
            prefs[TEMPERATURE_DISPLAY] = display
        }
    }
    
    // Show Animations
    val showAnimations: Flow<Boolean> = context.appearanceDataStore.data.map { prefs ->
        prefs[SHOW_ANIMATIONS] ?: true
    }
    
    suspend fun setShowAnimations(enabled: Boolean) {
        context.appearanceDataStore.edit { prefs ->
            prefs[SHOW_ANIMATIONS] = enabled
        }
    }
}

/**
 * Accent color options with hex values.
 */
enum class AccentColor(val id: String, val displayName: String, val hex: Long) {
    BLUE("blue", "Ocean Blue", 0xFF60A5FA),
    PURPLE("purple", "Royal Purple", 0xFFA78BFA),
    TEAL("teal", "Aqua Teal", 0xFF22D3EE),
    CORAL("coral", "Sunset Coral", 0xFFF87171),
    GREEN("green", "Forest Green", 0xFF34D399),
    ORANGE("orange", "Warm Orange", 0xFFFB923C),
    PINK("pink", "Rose Pink", 0xFFF472B6);
    
    companion object {
        fun fromId(id: String): AccentColor = entries.find { it.id == id } ?: BLUE
    }
}

/**
 * Theme mode options.
 */
enum class ThemeMode(val id: String, val displayName: String) {
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    SYSTEM("system", "System"),
    OLED("oled", "OLED Black");
    
    companion object {
        fun fromId(id: String): ThemeMode = entries.find { it.id == id } ?: SYSTEM
    }
}

/**
 * Font size options with scale factors.
 */
enum class FontSizeOption(val id: String, val displayName: String, val scale: Float) {
    SMALL("small", "Small", 0.85f),
    MEDIUM("medium", "Medium", 1.0f),
    LARGE("large", "Large", 1.15f);
    
    companion object {
        fun fromId(id: String): FontSizeOption = entries.find { it.id == id } ?: MEDIUM
    }
}

/**
 * Icon style options.
 */
enum class IconStyle(val id: String, val displayName: String) {
    FILLED("filled", "Filled"),
    OUTLINED("outlined", "Outlined"),
    ANIMATED("animated", "Animated");
    
    companion object {
        fun fromId(id: String): IconStyle = entries.find { it.id == id } ?: FILLED
    }
}
