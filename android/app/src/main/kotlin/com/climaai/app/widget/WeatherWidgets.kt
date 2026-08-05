package com.climaai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.climaai.app.MainActivity

/**
 * Small weather widget showing current temperature and icon
 */
class SmallWeatherWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataManager.getData(context)
        // A render is the only moment anything notices the reading has aged out.
        WidgetDataManager.requestRefreshIfStale(context)

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1E3A5F))
                    .cornerRadius(16.dp)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(12.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // No icon rather than a sun: a weather code of 0 is what
                    // "nothing stored" reads back as, and it draws a clear sky.
                    if (data.hasData) {
                        Text(
                            text = data.weatherIcon,
                            style = TextStyle(fontSize = 32.sp)
                        )

                        Spacer(modifier = GlanceModifier.height(4.dp))
                    }

                    // Temperature
                    Text(
                        text = if (data.hasData) "${data.currentTemp}°" else NO_DATA,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(4.dp))

                    // Location
                    Text(
                        text = if (data.hasData) data.locationName else NO_DATA_LABEL,
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

class SmallWeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallWeatherWidget()
}

/**
 * Medium weather widget with current conditions and hourly preview
 */
class MediumWeatherWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataManager.getData(context)
        WidgetDataManager.requestRefreshIfStale(context)

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1E3A5F))
                    .cornerRadius(16.dp)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Left side - Current weather
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (data.hasData) data.locationName else NO_DATA,
                            style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                                fontSize = 12.sp
                            )
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (data.hasData) {
                                Text(
                                    text = data.weatherIcon,
                                    style = TextStyle(fontSize = 28.sp)
                                )
                                Spacer(modifier = GlanceModifier.width(8.dp))
                            }
                            Text(
                                text = if (data.hasData) "${data.currentTemp}°" else NO_DATA,
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Text(
                            text = if (data.hasData) data.condition else NO_DATA_LABEL,
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 14.sp
                            )
                        )

                        // Omitted entirely rather than shown as "Feels like —°",
                        // which reads like a failed measurement rather than none.
                        if (data.hasData) {
                            Text(
                                text = "Feels like ${data.feelsLike}°",
                                style = TextStyle(
                                    color = ColorProvider(Color.White.copy(alpha = 0.6f)),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    // Right side - Stats
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatRow("💧", if (data.hasData) "${data.humidity}%" else NO_DATA)
                        Spacer(modifier = GlanceModifier.height(8.dp))
                        StatRow("💨", if (data.hasData) "${data.windSpeed} km/h" else NO_DATA)
                    }
                }
            }
        }
    }

    @Composable
    private fun StatRow(icon: String, value: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, style = TextStyle(fontSize = 14.sp))
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = value,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 13.sp
                )
            )
        }
    }
}

class MediumWeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediumWeatherWidget()
}

/**
 * Large weather widget with full forecast
 */
class LargeWeatherWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataManager.getData(context)
        WidgetDataManager.requestRefreshIfStale(context)

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1E3A5F))
                    .cornerRadius(16.dp)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(16.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = if (data.hasData) data.locationName else NO_DATA,
                                style = TextStyle(
                                    color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                                    fontSize = 12.sp
                                )
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (data.hasData) {
                                    Text(
                                        text = data.weatherIcon,
                                        style = TextStyle(fontSize = 24.sp)
                                    )
                                    Spacer(modifier = GlanceModifier.width(8.dp))
                                }
                                Text(
                                    text = if (data.hasData) "${data.currentTemp}°" else NO_DATA,
                                    style = TextStyle(
                                        color = ColorProvider(Color.White),
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Text(
                                text = if (data.hasData) data.condition else NO_DATA_LABEL,
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(12.dp))

                    // AI Insight. Only ever shown when one was actually written
                    // for this reading — there is no stock encouragement to fall
                    // back on, because a widget cannot know it is a good day for
                    // anything without the weather it is describing.
                    val insight = data.aiInsight
                    if (insight != null) {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(Color(0xFF6366F1).copy(alpha = 0.3f))
                                .cornerRadius(8.dp)
                                .padding(12.dp)
                        ) {
                            Row {
                                Text(text = "✨", style = TextStyle(fontSize = 16.sp))
                                Spacer(modifier = GlanceModifier.width(8.dp))
                                Text(
                                    text = insight,
                                    style = TextStyle(
                                        color = ColorProvider(Color.White),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.height(12.dp))
                    }

                    // Hourly forecast row, from the stored hourly readings. This
                    // used to be current temperature +1, +2, +1, +0 against a
                    // repeat of the current icon — a forecast in appearance only.
                    if (data.hourlyForecast.isEmpty()) {
                        Text(
                            text = "Hourly forecast unavailable",
                            style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.5f)),
                                fontSize = 11.sp
                            )
                        )
                    } else {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            data.hourlyForecast.forEach { hour ->
                                HourlyItem(hour)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HourlyItem(hour: HourForecast) {
        Column(
            modifier = GlanceModifier.padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = hour.label,
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.6f)),
                    fontSize = 10.sp
                )
            )
            Text(
                text = hour.icon,
                style = TextStyle(fontSize = 18.sp)
            )
            Text(
                text = "${hour.temp}°",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

class LargeWeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargeWeatherWidget()
}
