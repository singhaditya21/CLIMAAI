package com.climaai.wear.tile

import androidx.wear.tiles.*
import androidx.wear.tiles.material.*
import androidx.wear.tiles.material.layouts.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import com.google.common.util.concurrent.ListenableFuture
import com.climaai.wear.data.NoDataReason
import com.climaai.wear.data.WearWeather
import com.climaai.wear.data.WearWeatherData
import com.climaai.wear.data.WearWeatherRepository

/**
 * Tile service for glanceable weather information on Wear OS.
 */
class WeatherTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return scope.future {
            val weather = WearWeatherRepository.getWeather(this@WeatherTileService)

            val layout = when (weather) {
                is WearWeather.Available -> weatherLayout(weather.data)
                is WearWeather.Unavailable -> noDataLayout(weather.reason)
            }

            TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                // Come back sooner when there is nothing to show, so the tile
                // recovers as soon as the fix or the network returns instead of
                // sitting on "No data" for a full refresh period.
                .setFreshnessIntervalMillis(
                    if (weather is WearWeather.Available) REFRESH_INTERVAL_MS else RETRY_INTERVAL_MS
                )
                .setTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(layout)
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun weatherLayout(weather: WearWeatherData): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .addContent(
                // Location
                Text.Builder(this, weather.location)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(0xAAFFFFFF.toInt()))
                    .build()
            )
            .addContent(
                // Weather icon
                Text.Builder(this, weather.conditionIcon)
                    .setTypography(Typography.TYPOGRAPHY_DISPLAY1)
                    .build()
            )
            .addContent(
                // Temperature
                Text.Builder(this, "${weather.temperature}°")
                    .setTypography(Typography.TYPOGRAPHY_DISPLAY2)
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .build()
            )

        // A reading past its fresh window is still shown, but with its age —
        // nothing on the tile passes for "now" unless it is.
        if (weather.isStale) {
            column.addContent(
                Text.Builder(this, "Updated ${weather.updatedAgo()}")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(0x88FFFFFF.toInt()))
                    .build()
            )
        }

        return PrimaryLayout.Builder(deviceParameters())
            .setContent(
                column
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .build()
            )
            .build()
    }

    /**
     * The tile with no reading behind it. It says so; it does not fall back to a
     * sample temperature that would look exactly like a real one.
     */
    private fun noDataLayout(reason: NoDataReason): LayoutElementBuilders.LayoutElement {
        return PrimaryLayout.Builder(deviceParameters())
            .setContent(
                LayoutElementBuilders.Column.Builder()
                    .addContent(
                        Text.Builder(this, NO_VALUE)
                            .setTypography(Typography.TYPOGRAPHY_DISPLAY2)
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .build()
                    )
                    .addContent(
                        Text.Builder(this, reason.label)
                            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                            .setColor(argb(0xAAFFFFFF.toInt()))
                            .setMaxLines(2)
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

    private companion object {
        const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        const val RETRY_INTERVAL_MS = 5 * 60 * 1000L

        /** Stands in for a temperature the watch does not have. */
        const val NO_VALUE = "--"
    }
}
