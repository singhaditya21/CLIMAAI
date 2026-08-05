package com.climaai.app.ui.screens

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.climaai.app.ui.viewmodel.SubscriptionState
import com.climaai.app.ui.viewmodel.SubscriptionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    subscriptionViewModel: SubscriptionViewModel,
    onDismiss: () -> Unit,
    onSubscriptionSuccess: () -> Unit
) {
    val context = LocalContext.current
    val state by subscriptionViewModel.state.collectAsState()
    var selectedPlanIndex by remember { mutableStateOf(1) } // Default to annual

    // Handle success
    LaunchedEffect(state.isPremium) {
        if (state.isPremium) {
            onSubscriptionSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF0F0F1A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Premium badge
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFFD700), Color(0xFFFF8C00))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Unlock Premium",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Get the most out of ClimaAI with premium features",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Features list
            PremiumFeaturesList()

            Spacer(Modifier.height(32.dp))

            // Plan selection
            if (state.plans.isNotEmpty()) {
                PlanCard(
                    name = "Monthly",
                    price = "$4.99",
                    period = "/month",
                    isSelected = selectedPlanIndex == 0,
                    isPopular = false,
                    onClick = { selectedPlanIndex = 0 }
                )

                Spacer(Modifier.height(12.dp))

                PlanCard(
                    name = "Annual",
                    price = "$39.99",
                    period = "/year",
                    isSelected = selectedPlanIndex == 1,
                    isPopular = true,
                    savings = "Save 33%",
                    onClick = { selectedPlanIndex = 1 }
                )
            }

            Spacer(Modifier.height(24.dp))

            // Trial info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF667EEA).copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = Color(0xFF667EEA)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Start with a ${state.trialDays}-day free trial",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Subscribe button
            Button(
                onClick = {
                    val productId = if (selectedPlanIndex == 0) 
                        SubscriptionViewModel.PRODUCT_MONTHLY 
                    else 
                        SubscriptionViewModel.PRODUCT_ANNUAL
                    
                    subscriptionViewModel.purchaseSubscription(
                        context as Activity,
                        productId
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF667EEA)
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Start Free Trial",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Restore purchases
            TextButton(
                onClick = { subscriptionViewModel.restorePurchases(context) }
            ) {
                Text(
                    "Restore Purchases",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // Terms
            Text(
                "Payment will be charged to your Google Play account. " +
                "Subscription automatically renews unless canceled at least 24 hours before the end of the current period.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )

            // Error message
            if (state.error != null) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF5252).copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        state.error!!,
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFFFF5252),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PremiumFeaturesList() {
    // No "No Ads" entry: the app carries no ads on any tier, so removing them
    // is not a thing Premium can honestly sell.
    val features = listOf(
        Triple(Icons.Default.DateRange, "16-Day Forecast", "Extended weather predictions"),
        Triple(Icons.Default.Psychology, "Unlimited AI Insights", "Personalized recommendations"),
        Triple(Icons.Default.DirectionsRun, "Activity Forecasts", "Best times for any activity"),
        Triple(Icons.Default.Map, "Advanced Radar", "High-resolution weather maps"),
        Triple(Icons.Default.Spa, "Health Alerts", "Pollen, UV, and air quality")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        features.forEach { (icon, title, subtitle) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF667EEA).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF667EEA),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Text(
                        subtitle,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    name: String,
    price: String,
    period: String,
    isSelected: Boolean,
    isPopular: Boolean,
    savings: String? = null,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF667EEA) else Color.White.copy(alpha = 0.2f)
    val bgColor = if (isSelected) Color(0xFF667EEA).copy(alpha = 0.1f) else Color.Transparent

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isSelected,
                        onClick = onClick,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color(0xFF667EEA),
                            unselectedColor = Color.White.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        if (savings != null) {
                            Text(
                                savings,
                                fontSize = 13.sp,
                                color = Color(0xFF38EF7D),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        price,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        period,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Popular badge
        if (isPopular) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-12).dp),
                color = Color(0xFFFFD700),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "BEST VALUE",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}
