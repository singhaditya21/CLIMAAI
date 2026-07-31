package com.climaai.wear.tile

import android.content.Context
import androidx.wear.tiles.*
import androidx.wear.tiles.material.*
import androidx.wear.tiles.material.layouts.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import com.google.common.util.concurrent.ListenableFuture
import com.climaai.wear.data.WearWeatherRepository

/**
 * Tile service for glanceable weather information on Wear OS.
 */
class WeatherTileService : TileService() {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return scope.future {
            val weather = WearWeatherRepository.getWeather(applicationContext)
            
            TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                .setFreshnessIntervalMillis(1800000) // 30 minutes
                .setTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(buildTileLayout(weather.temperature, weather.conditionIcon, weather.location))
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        }
    }
    
    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return scope.future {
            ResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        }
    }
    
    private fun buildTileLayout(
        temperature: Int,
        icon: String,
        location: String
    ): LayoutElementBuilders.LayoutElement {
        return PrimaryLayout.Builder(deviceParameters())
            .setContent(
                LayoutElementBuilders.Column.Builder()
                    .addContent(
                        // Location
                        Text.Builder(this, location)
                            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                            .setColor(argb(0xAAFFFFFF.toInt()))
                            .build()
                    )
                    .addContent(
                        // Weather icon
                        Text.Builder(this, icon)
                            .setTypography(Typography.TYPOGRAPHY_DISPLAY1)
                            .build()
                    )
                    .addContent(
                        // Temperature
                        Text.Builder(this, "${temperature}°")
                            .setTypography(Typography.TYPOGRAPHY_DISPLAY2)
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .build()
                    )
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .build()
            )
            .build()
    }
    
    private fun deviceParameters(): DeviceParametersBuilders.DeviceParameters {
        return DeviceParametersBuilders.DeviceParameters.Builder()
            .setScreenWidthDp(192)
            .setScreenHeightDp(192)
            .setScreenDensity(2f)
            .build()
    }
    
    private fun argb(color: Int): ColorBuilders.ColorProp {
        return ColorBuilders.ColorProp.Builder()
            .setArgb(color)
            .build()
    }
}
