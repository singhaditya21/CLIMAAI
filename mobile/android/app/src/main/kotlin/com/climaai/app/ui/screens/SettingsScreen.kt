package com.climaai.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.climaai.app.ui.viewmodel.WeatherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WeatherViewModel,
    onNavigateBack: () -> Unit
) {
    val isPremium by viewModel.isPremium.collectAsState()
    val subscriptionStatus by viewModel.subscriptionStatus.collectAsState()
    
    // Settings state
    var temperatureUnit by remember { mutableStateOf("celsius") }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var darkMode by remember { mutableStateOf("auto") }
    
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
                
                // Notifications Section
                SettingsSection(title = "Notifications") {
                    SettingsToggleRow(
                        icon = Icons.Default.Notifications,
                        title = "Weather Alerts",
                        subtitle = "Severe weather, rain, UV warnings",
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
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
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    SettingsInfoRow(
                        icon = Icons.Default.Policy,
                        title = "Privacy Policy",
                        value = ""
                    )
                    Divider(color = Color.White.copy(alpha = 0.1f))
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
                tint = if (isPremium) Color(0xFFFBBF24) else Color(0xFFFBBF24),
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
