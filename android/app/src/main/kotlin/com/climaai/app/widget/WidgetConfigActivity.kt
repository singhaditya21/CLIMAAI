package com.climaai.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.climaai.app.billing.Feature
import com.climaai.app.billing.FeatureGate
import com.climaai.app.ui.components.GlassmorphicCard
import com.climaai.app.ui.theme.ClimaAITheme
import kotlinx.coroutines.launch

/**
 * Widget configuration activity shown when user adds a widget.
 * Premium users can customize; free users get default style.
 */
class WidgetConfigActivity : ComponentActivity() {
    
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set result to CANCELED in case user backs out
        setResult(RESULT_CANCELED)
        
        // Get the widget ID from intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        
        // Initialize feature gate
        FeatureGate.init(this)
        
        // The preview below shows the real stored reading rather than a stand-in
        // one, so nothing on this screen can be read as a weather report for a
        // place the user is not.
        val widgetData = WidgetDataManager.getData(this)

        setContent {
            ClimaAITheme {
                WidgetConfigScreen(
                    widgetId = appWidgetId,
                    isPremium = FeatureGate.isPremium(),
                    data = widgetData,
                    onConfirm = { confirmWidget() },
                    onCancel = { finish() }
                )
            }
        }
    }
    
    private fun confirmWidget() {
        // Return success result
        val resultValue = Intent().putExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            appWidgetId
        )
        setResult(RESULT_OK, resultValue)

        // The host draws the new widget itself. What it cannot know is that the
        // stored reading may be hours old, so ask for a fetch. This used to
        // broadcast ACTION_APPWIDGET_UPDATE with no component or package set,
        // which an implicit broadcast to a manifest receiver never delivers.
        WidgetDataManager.requestRefreshIfStale(this)

        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    widgetId: Int,
    isPremium: Boolean,
    data: WidgetData,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // Widget preferences
    var backgroundStyle by remember { mutableStateOf(WidgetPrefs.DEFAULT_BG_STYLE) }
    var colorTheme by remember { mutableStateOf(WidgetPrefs.DEFAULT_COLOR_THEME) }
    var transparency by remember { mutableFloatStateOf(WidgetPrefs.DEFAULT_TRANSPARENCY.toFloat()) }
    var showLocation by remember { mutableStateOf(true) }
    var showFeelsLike by remember { mutableStateOf(true) }
    var compactMode by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customize Widget", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = onConfirm) {
                        Text("Done", color = Color(0xFF60A5FA))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E3A5F), Color(0xFF0D1B2A))
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preview
                WidgetPreview(
                    data = data,
                    backgroundStyle = backgroundStyle,
                    colorTheme = colorTheme,
                    transparency = transparency.toInt(),
                    showLocation = showLocation,
                    showFeelsLike = showFeelsLike,
                    compactMode = compactMode
                )
                
                // Premium Banner (if not premium)
                if (!isPremium) {
                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = Color(0xFFA78BFA),
                        backgroundColor = Color(0xFF6366F1).copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Upgrade to Premium to customize",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                // Color Theme
                ConfigSection(
                    title = "Color Theme",
                    locked = !isPremium
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(WidgetColorTheme.entries.toList()) { theme ->
                            ColorThemeOption(
                                theme = theme,
                                isSelected = colorTheme == theme.id,
                                enabled = isPremium,
                                onSelect = { colorTheme = theme.id }
                            )
                        }
                    }
                }
                
                // Background Style
                ConfigSection(
                    title = "Background Style",
                    locked = !isPremium
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WidgetBackgroundStyle.entries.forEach { style ->
                            val isSelected = backgroundStyle == style.id
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = isPremium) { backgroundStyle = style.id },
                                color = if (isSelected) Color(0xFF60A5FA).copy(alpha = 0.3f)
                                        else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    style.displayName,
                                    modifier = Modifier.padding(12.dp),
                                    color = if (isPremium) Color.White else Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                // Transparency
                ConfigSection(
                    title = "Transparency: ${transparency.toInt()}%",
                    locked = !isPremium
                ) {
                    Slider(
                        value = transparency,
                        onValueChange = { if (isPremium) transparency = it },
                        valueRange = 0f..100f,
                        enabled = isPremium,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF60A5FA),
                            activeTrackColor = Color(0xFF60A5FA)
                        )
                    )
                }
                
                // Display Options
                ConfigSection(title = "Display Options") {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Show Location", color = Color.White)
                            Switch(
                                checked = showLocation,
                                onCheckedChange = { showLocation = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF60A5FA)
                                )
                            )
                        }
                        
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Show Feels Like", color = Color.White)
                            Switch(
                                checked = showFeelsLike,
                                onCheckedChange = { showFeelsLike = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF60A5FA)
                                )
                            )
                        }
                        
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Compact Mode", color = Color.White)
                            Switch(
                                checked = compactMode,
                                onCheckedChange = { compactMode = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF60A5FA)
                                )
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ConfigSection(
    title: String,
    locked: Boolean = false,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.titleSmall
            )
            if (locked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Premium",
                    tint = Color(0xFFA78BFA),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        GlassmorphicCard(animatedBorder = false) {
            Box(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ColorThemeOption(
    theme: WidgetColorTheme,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(theme.primaryColor),
                        Color(theme.secondaryColor)
                    )
                )
            )
            .then(
                if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                else Modifier
            )
            .clickable(enabled = enabled) { onSelect() },
        contentAlignment = Alignment.Center
    ) {
        if (!enabled) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        } else if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun WidgetPreview(
    data: WidgetData,
    backgroundStyle: String,
    colorTheme: String,
    transparency: Int,
    showLocation: Boolean,
    showFeelsLike: Boolean,
    compactMode: Boolean
) {
    val theme = WidgetColorTheme.fromId(colorTheme)
    val bgColor = when (backgroundStyle) {
        "transparent" -> Color.Transparent
        "solid" -> Color(theme.primaryColor).copy(alpha = transparency / 100f)
        else -> Color(theme.primaryColor).copy(alpha = transparency / 100f)
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Preview",
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        // Mini widget preview
        Surface(
            modifier = Modifier
                .width(200.dp)
                .height(if (compactMode) 80.dp else 100.dp),
            color = bgColor,
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (backgroundStyle == "gradient") {
                            Modifier.background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(theme.primaryColor).copy(alpha = transparency / 100f),
                                        Color(theme.secondaryColor).copy(alpha = transparency / 100f)
                                    )
                                )
                            )
                        } else Modifier
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showLocation) {
                        Text(
                            if (data.hasData) data.locationName else NO_DATA,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (data.hasData) {
                            Text(data.weatherIcon, style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            if (data.hasData) "${data.currentTemp}°" else NO_DATA,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (showFeelsLike && !compactMode && data.hasData) {
                        Text(
                            "Feels like ${data.feelsLike}°",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
