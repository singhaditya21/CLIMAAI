package com.climaai.app.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.climaai.app.billing.Feature
import com.climaai.app.billing.FeatureGate
import com.climaai.app.data.*
import com.climaai.app.ui.components.GlassmorphicCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPaywall: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppearancePrefs(context) }
    
    val isPremium = FeatureGate.isPremium()
    
    // Collect preferences
    val themeMode by prefs.themeMode.collectAsState(initial = AppearancePrefs.DEFAULT_THEME)
    val accentColor by prefs.accentColor.collectAsState(initial = AppearancePrefs.DEFAULT_ACCENT)
    val fontSize by prefs.fontSize.collectAsState(initial = AppearancePrefs.DEFAULT_FONT_SIZE)
    val iconStyle by prefs.iconStyle.collectAsState(initial = AppearancePrefs.DEFAULT_ICON_STYLE)
    val showAnimations by prefs.showAnimations.collectAsState(initial = true)
    val useDynamicColor by prefs.useDynamicColor.collectAsState(initial = false)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
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
                // Premium Banner (if not premium)
                if (!isPremium) {
                    PremiumUpgradeBanner(onClick = onNavigateToPaywall)
                }
                
                // Theme Section
                AppearanceSection(title = "Theme") {
                    ThemeModeSelector(
                        selected = themeMode,
                        isPremium = isPremium,
                        onSelect = { mode ->
                            if (mode == "oled" && !isPremium) {
                                onNavigateToPaywall()
                            } else {
                                scope.launch { prefs.setThemeMode(mode) }
                            }
                        }
                    )
                }
                
                // Accent Color Section
                AppearanceSection(title = "Accent Color", isPremium = !isPremium) {
                    AccentColorPicker(
                        selected = accentColor,
                        enabled = isPremium,
                        onSelect = { color ->
                            if (isPremium) {
                                scope.launch { prefs.setAccentColor(color) }
                            } else {
                                onNavigateToPaywall()
                            }
                        }
                    )
                }
                
                // Font Size Section
                AppearanceSection(title = "Font Size", isPremium = !isPremium) {
                    FontSizeSelector(
                        selected = fontSize,
                        enabled = isPremium,
                        onSelect = { size ->
                            if (isPremium) {
                                scope.launch { prefs.setFontSize(size) }
                            } else {
                                onNavigateToPaywall()
                            }
                        }
                    )
                }
                
                // Icon Style Section
                AppearanceSection(title = "Weather Icons", isPremium = !isPremium) {
                    IconStyleSelector(
                        selected = iconStyle,
                        enabled = isPremium,
                        onSelect = { style ->
                            if (isPremium) {
                                scope.launch { prefs.setIconStyle(style) }
                            } else {
                                onNavigateToPaywall()
                            }
                        }
                    )
                }
                
                // Animations Toggle
                AppearanceSection(title = "Effects") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Animations",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Weather backgrounds and transitions",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = showAnimations,
                            onCheckedChange = { scope.launch { prefs.setShowAnimations(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF60A5FA)
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PremiumUpgradeBanner(onClick: () -> Unit) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        glowColor = Color(0xFFA78BFA),
        backgroundColor = Color(0xFF6366F1).copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFA78BFA),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Unlock Full Customization",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Themes, colors, fonts & more",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    title: String,
    isPremium: Boolean = false,
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
            if (isPremium) {
                Surface(
                    color = Color(0xFFA78BFA).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "PRO",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color(0xFFA78BFA),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        GlassmorphicCard(animatedBorder = false) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ThemeModeSelector(
    selected: String,
    isPremium: Boolean,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode ->
            val isLocked = mode == ThemeMode.OLED && !isPremium
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected == mode.id) Color.White.copy(alpha = 0.1f)
                        else Color.Transparent
                    )
                    .clickable { onSelect(mode.id) }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    mode.displayName,
                    color = if (isLocked) Color.White.copy(alpha = 0.5f) else Color.White
                )
                if (isLocked) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Premium",
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(18.dp)
                    )
                } else if (selected == mode.id) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color(0xFF60A5FA)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentColorPicker(
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(AccentColor.entries.toList()) { color ->
            val isSelected = selected == color.id
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(color.hex))
                    .then(
                        if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                        else Modifier
                    )
                    .clickable(enabled = enabled) { onSelect(color.id) },
                contentAlignment = Alignment.Center
            ) {
                if (!enabled) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
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
    }
}

@Composable
private fun FontSizeSelector(
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FontSizeOption.entries.forEach { option ->
            val isSelected = selected == option.id
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled) { onSelect(option.id) },
                color = if (isSelected) Color(0xFF60A5FA).copy(alpha = 0.3f) 
                        else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Aa",
                        color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        style = when (option) {
                            FontSizeOption.SMALL -> MaterialTheme.typography.bodySmall
                            FontSizeOption.MEDIUM -> MaterialTheme.typography.bodyMedium
                            FontSizeOption.LARGE -> MaterialTheme.typography.bodyLarge
                        }
                    )
                    Text(
                        option.displayName,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun IconStyleSelector(
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconStyle.entries.forEach { style ->
            val isSelected = selected == style.id
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled) { onSelect(style.id) },
                color = if (isSelected) Color(0xFF60A5FA).copy(alpha = 0.3f) 
                        else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        when (style) {
                            IconStyle.FILLED -> "☀️"
                            IconStyle.OUTLINED -> "☀"
                            IconStyle.ANIMATED -> "✨"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        style.displayName,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
