package com.climaai.app.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_prefs")

/**
 * Manages widget customization preferences.
 */
class WidgetPrefs(private val context: Context) {
    
    companion object {
        // Widget Style Keys (per widget instance using appWidgetId)
        private fun backgroundStyleKey(widgetId: Int) = stringPreferencesKey("bg_style_$widgetId")
        private fun colorThemeKey(widgetId: Int) = stringPreferencesKey("color_theme_$widgetId")
        private fun transparencyKey(widgetId: Int) = intPreferencesKey("transparency_$widgetId")
        private fun showLocationKey(widgetId: Int) = booleanPreferencesKey("show_location_$widgetId")
        private fun showFeelsLikeKey(widgetId: Int) = booleanPreferencesKey("show_feels_like_$widgetId")
        private fun compactModeKey(widgetId: Int) = booleanPreferencesKey("compact_mode_$widgetId")
        
        // Defaults
        const val DEFAULT_BG_STYLE = "gradient"
        const val DEFAULT_COLOR_THEME = "blue"
        const val DEFAULT_TRANSPARENCY = 80
    }
    
    // Background Style: solid, gradient, transparent
    fun getBackgroundStyle(widgetId: Int): Flow<String> = context.widgetDataStore.data.map { prefs ->
        prefs[backgroundStyleKey(widgetId)] ?: DEFAULT_BG_STYLE
    }
    
    suspend fun setBackgroundStyle(widgetId: Int, style: String) {
        context.widgetDataStore.edit { prefs ->
            prefs[backgroundStyleKey(widgetId)] = style
        }
    }
    
    // Color Theme: blue, purple, teal, coral, etc.
    fun getColorTheme(widgetId: Int): Flow<String> = context.widgetDataStore.data.map { prefs ->
        prefs[colorThemeKey(widgetId)] ?: DEFAULT_COLOR_THEME
    }
    
    suspend fun setColorTheme(widgetId: Int, theme: String) {
        context.widgetDataStore.edit { prefs ->
            prefs[colorThemeKey(widgetId)] = theme
        }
    }
    
    // Transparency: 0-100
    fun getTransparency(widgetId: Int): Flow<Int> = context.widgetDataStore.data.map { prefs ->
        prefs[transparencyKey(widgetId)] ?: DEFAULT_TRANSPARENCY
    }
    
    suspend fun setTransparency(widgetId: Int, value: Int) {
        context.widgetDataStore.edit { prefs ->
            prefs[transparencyKey(widgetId)] = value.coerceIn(0, 100)
        }
    }
    
    // Show Location
    fun getShowLocation(widgetId: Int): Flow<Boolean> = context.widgetDataStore.data.map { prefs ->
        prefs[showLocationKey(widgetId)] ?: true
    }
    
    suspend fun setShowLocation(widgetId: Int, show: Boolean) {
        context.widgetDataStore.edit { prefs ->
            prefs[showLocationKey(widgetId)] = show
        }
    }
    
    // Show Feels Like
    fun getShowFeelsLike(widgetId: Int): Flow<Boolean> = context.widgetDataStore.data.map { prefs ->
        prefs[showFeelsLikeKey(widgetId)] ?: true
    }
    
    suspend fun setShowFeelsLike(widgetId: Int, show: Boolean) {
        context.widgetDataStore.edit { prefs ->
            prefs[showFeelsLikeKey(widgetId)] = show
        }
    }
    
    // Compact Mode
    fun getCompactMode(widgetId: Int): Flow<Boolean> = context.widgetDataStore.data.map { prefs ->
        prefs[compactModeKey(widgetId)] ?: false
    }
    
    suspend fun setCompactMode(widgetId: Int, compact: Boolean) {
        context.widgetDataStore.edit { prefs ->
            prefs[compactModeKey(widgetId)] = compact
        }
    }
    
    /**
     * Get all preferences for a widget (used by widget content).
     */
    fun getWidgetConfig(widgetId: Int): WidgetConfig = runBlocking {
        val prefs = context.widgetDataStore.data.first()
        WidgetConfig(
            backgroundStyle = prefs[backgroundStyleKey(widgetId)] ?: DEFAULT_BG_STYLE,
            colorTheme = prefs[colorThemeKey(widgetId)] ?: DEFAULT_COLOR_THEME,
            transparency = prefs[transparencyKey(widgetId)] ?: DEFAULT_TRANSPARENCY,
            showLocation = prefs[showLocationKey(widgetId)] ?: true,
            showFeelsLike = prefs[showFeelsLikeKey(widgetId)] ?: true,
            compactMode = prefs[compactModeKey(widgetId)] ?: false
        )
    }
    
    /**
     * Delete preferences when widget is removed.
     */
    suspend fun deleteWidgetConfig(widgetId: Int) {
        context.widgetDataStore.edit { prefs ->
            prefs.remove(backgroundStyleKey(widgetId))
            prefs.remove(colorThemeKey(widgetId))
            prefs.remove(transparencyKey(widgetId))
            prefs.remove(showLocationKey(widgetId))
            prefs.remove(showFeelsLikeKey(widgetId))
            prefs.remove(compactModeKey(widgetId))
        }
    }
}

/**
 * Widget configuration data class.
 */
data class WidgetConfig(
    val backgroundStyle: String = "gradient",
    val colorTheme: String = "blue",
    val transparency: Int = 80,
    val showLocation: Boolean = true,
    val showFeelsLike: Boolean = true,
    val compactMode: Boolean = false
)

/**
 * Widget color themes.
 */
enum class WidgetColorTheme(
    val id: String,
    val displayName: String,
    val primaryColor: Long,
    val secondaryColor: Long
) {
    BLUE("blue", "Ocean Blue", 0xFF1E3A5F, 0xFF0D1B2A),
    PURPLE("purple", "Royal Purple", 0xFF4C1D95, 0xFF2E1065),
    TEAL("teal", "Aqua Teal", 0xFF115E59, 0xFF042F2E),
    CORAL("coral", "Sunset Coral", 0xFF9A3412, 0xFF7C2D12),
    GREEN("green", "Forest Green", 0xFF166534, 0xFF14532D),
    DARK("dark", "Midnight Black", 0xFF1F2937, 0xFF111827);
    
    companion object {
        fun fromId(id: String): WidgetColorTheme = entries.find { it.id == id } ?: BLUE
    }
}

/**
 * Widget background styles.
 */
enum class WidgetBackgroundStyle(val id: String, val displayName: String) {
    SOLID("solid", "Solid Color"),
    GRADIENT("gradient", "Gradient"),
    TRANSPARENT("transparent", "Transparent");
    
    companion object {
        fun fromId(id: String): WidgetBackgroundStyle = entries.find { it.id == id } ?: GRADIENT
    }
}
