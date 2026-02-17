package com.climaai.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.climaai.app.data.NotificationPrefsManager
import com.climaai.app.data.cache.UsageSummary
import com.climaai.app.ui.components.AnimatedCounter
import com.climaai.app.ui.viewmodel.WeatherViewModel
import com.climaai.app.work.WorkScheduler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WeatherViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToPaywall: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val isPremium by viewModel.isPremium.collectAsState()
    val subscriptionStatus by viewModel.subscriptionStatus.collectAsState()
    
    // Notification preferences
    val notificationPrefs = remember { NotificationPrefsManager(context) }
    val dailySummaryEnabled by notificationPrefs.dailySummaryEnabled.collectAsState(initial = true)
    val dailySummaryHour by notificationPrefs.dailySummaryHour.collectAsState(initial = 7)
    val rainAlertsEnabled by notificationPrefs.rainAlertsEnabled.collectAsState(initial = true)
    val severeWeatherEnabled by notificationPrefs.severeWeatherEnabled.collectAsState(initial = true)
    val uvAlertsEnabled by notificationPrefs.uvAlertsEnabled.collectAsState(initial = false)
    val pollenAlertsEnabled by notificationPrefs.pollenAlertsEnabled.collectAsState(initial = false)
    
    // Settings state
    var temperatureUnit by remember { mutableStateOf("celsius") }
    var darkMode by remember { mutableStateOf("auto") }
    var showTimePicker by remember { mutableStateOf(false) }
    
    // Time picker state
    val timePickerState = rememberTimePickerState(
        initialHour = dailySummaryHour,
        initialMinute = 0,
        is24Hour = true
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White) },
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
                // Subscription Card
                SubscriptionCard(isPremium, subscriptionStatus?.subscription?.plan)
                
                // Units Section
                SettingsSection(title = "Units") {
                    SettingsOptionRow(
                        icon = Icons.Default.Thermostat,
                        title = "Temperature",
                        value = if (temperatureUnit == "celsius") "°C" else "°F",
                        onClick = {
                            temperatureUnit = if (temperatureUnit == "celsius") "fahrenheit" else "celsius"
                        }
                    )
                }
                
                // Appearance Section
                SettingsSection(title = "Appearance") {
                    SettingsNavigationRow(
                        icon = Icons.Default.Palette,
                        title = "Customize Look",
                        subtitle = if (isPremium) "Theme, colors, fonts & more" else "Unlock with Premium",
                        isPremium = !isPremium,
                        onClick = {
                            if (isPremium) {
                                onNavigateToAppearance()
                            } else {
                                onNavigateToPaywall()
                            }
                        }
                    )
                }
                
                // Notifications Section - Enhanced
                SettingsSection(title = "Notifications") {
                    // Daily Summary
                    SettingsToggleRow(
                        icon = Icons.Default.WbSunny,
                        title = "Daily Summary",
                        subtitle = "Weather briefing at ${formatHour(dailySummaryHour)}",
                        checked = dailySummaryEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                notificationPrefs.setDailySummaryEnabled(enabled)
                                if (enabled) {
                                    WorkScheduler.scheduleDailySummary(context, dailySummaryHour)
                                } else {
                                    WorkScheduler.cancelDailySummary(context)
                                }
                            }
                        }
                    )
                    
                    if (dailySummaryEnabled) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        SettingsOptionRow(
                            icon = Icons.Default.Schedule,
                            title = "Summary Time",
                            value = formatHour(dailySummaryHour),
                            onClick = { showTimePicker = true }
                        )
                    }
                    
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    
                    // Rain Alerts
                    SettingsToggleRow(
                        icon = Icons.Default.WaterDrop,
                        title = "Rain Alerts",
                        subtitle = "Notify when rain is expected soon",
                        checked = rainAlertsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                notificationPrefs.setRainAlertsEnabled(enabled)
                            }
                        }
                    )
                    
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    
                    // Severe Weather Alerts
                    SettingsToggleRow(
                        icon = Icons.Default.Warning,
                        title = "Severe Weather",
                        subtitle = "Storms, extreme conditions",
                        checked = severeWeatherEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                notificationPrefs.setSevereWeatherEnabled(enabled)
                            }
                        }
                    )
                    
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    
                    // UV Alerts
                    SettingsToggleRow(
                        icon = Icons.Default.WbSunny,
                        title = "UV Warnings",
                        subtitle = "Alert when UV index is high",
                        checked = uvAlertsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                notificationPrefs.setUVAlertsEnabled(enabled)
                            }
                        }
                    )
                    
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    
                    // Pollen Alerts
                    SettingsToggleRow(
                        icon = Icons.Default.Grass,
                        title = "Pollen Alerts",
                        subtitle = "Notify on high pollen days",
                        checked = pollenAlertsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                notificationPrefs.setPollenAlertsEnabled(enabled)
                            }
                        }
                    )
                }
                
                // Widget Section
                SettingsSection(title = "Widgets") {
                    SettingsInfoRow(
                        icon = Icons.Default.Widgets,
                        title = "Add Widget",
                        value = ""
                    )
                    Text(
                        text = "Long-press your home screen and select Widgets to add ClimaAI widgets",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                
                // API Usage Section
                val usageSummary by viewModel.usageSummary.collectAsState()
                usageSummary?.let { usage ->
                    ApiUsageSection(usage)
                }
                
                // Appearance Section
                SettingsSection(title = "Appearance") {
                    SettingsOptionRow(
                        icon = Icons.Default.DarkMode,
                        title = "Dark Mode",
                        value = when (darkMode) {
                            "light" -> "Off"
                            "dark" -> "On"
                            else -> "Auto"
                        },
                        onClick = {
                            darkMode = when (darkMode) {
                                "auto" -> "light"
                                "light" -> "dark"
                                else -> "auto"
                            }
                        }
                    )
                }
                
                // About Section
                SettingsSection(title = "About") {
                    SettingsInfoRow(
                        icon = Icons.Default.Info,
                        title = "Version",
                        value = "1.0.0"
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    SettingsInfoRow(
                        icon = Icons.Default.Policy,
                        title = "Privacy Policy",
                        value = ""
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    SettingsInfoRow(
                        icon = Icons.Default.Description,
                        title = "Terms of Service",
                        value = ""
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    
    // Time Picker Dialog
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Summary Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            notificationPrefs.setDailySummaryHour(timePickerState.hour)
                            if (dailySummaryEnabled) {
                                WorkScheduler.scheduleDailySummary(context, timePickerState.hour)
                            }
                        }
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatHour(hour: Int): String {
    return when {
        hour == 0 -> "12:00 AM"
        hour < 12 -> "$hour:00 AM"
        hour == 12 -> "12:00 PM"
        else -> "${hour - 12}:00 PM"
    }
}

@Composable
private fun SubscriptionCard(isPremium: Boolean, currentPlan: String?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPremium) Color(0xFF6366F1).copy(alpha = 0.3f)
                            else Color(0xFFFBBF24).copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPremium) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPremium) "Premium Active" else "Free Plan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isPremium) currentPlan?.replaceFirstChar { it.uppercase() } ?: "Subscription"
                          else "Upgrade for extended forecasts & more",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            if (!isPremium) {
                Button(
                    onClick = { /* Open paywall */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFBBF24)
                    )
                ) {
                    Text("Upgrade", color = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsOptionRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF60A5FA),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onClick) {
            Text(value, color = Color(0xFF60A5FA))
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF60A5FA),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF60A5FA),
                checkedTrackColor = Color(0xFF60A5FA).copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF60A5FA),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isPremium: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isPremium) Color(0xFFA78BFA) else Color(0xFF60A5FA),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                if (isPremium) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFFA78BFA).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "PRO",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color(0xFFA78BFA),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        Icon(
            imageVector = if (isPremium) Icons.Default.Lock else Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (isPremium) Color(0xFFA78BFA) else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ApiUsageSection(usage: UsageSummary) {
    SettingsSection(title = "API Usage") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Daily usage indicator
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularUsageIndicator(
                    progress = usage.dailyUsagePercent,
                    label = "${usage.dailyRefreshes}",
                    sublabel = "/ ${usage.dailyLimit}",
                    size = 72.dp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            
            // Hourly usage indicator
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularUsageIndicator(
                    progress = usage.hourlyUsagePercent,
                    label = "${usage.hourlyRefreshes}",
                    sublabel = "/ ${usage.hourlyLimit}",
                    size = 72.dp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This Hour",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            
            // Total lifetime calls
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(72.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedCounter(
                            value = usage.totalLifetimeCalls,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF60A5FA)
                        )
                        Text(
                            text = "total",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "All Time",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
        
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        
        // Breakdown row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ApiCallChip("\u2600\uFE0F Weather", usage.weatherCallsToday)
            ApiCallChip("\uD83C\uDF2C AQI", usage.aqiCallsToday)
            ApiCallChip("\uD83D\uDCCD Geo", usage.geocodingCallsToday)
        }
    }
}

@Composable
private fun ApiCallChip(label: String, count: Int) {
    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF60A5FA)
            )
        }
    }
}

@Composable
private fun CircularUsageIndicator(
    progress: Float,
    label: String,
    sublabel: String,
    size: Dp = 72.dp,
    strokeWidth: Dp = 6.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "usageProgress"
    )
    
    val progressColor = when {
        animatedProgress < 0.50f -> Color(0xFF34D399) // green
        animatedProgress < 0.80f -> Color(0xFFFBBF24) // yellow
        else -> Color(0xFFEF4444) // red
    }
    
    val trackColor = Color.White.copy(alpha = 0.1f)
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            
            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            
            // Progress
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = progressColor
            )
            Text(
                text = sublabel,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}
