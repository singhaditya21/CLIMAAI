package com.climaai.wear.complication

import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.climaai.wear.data.NoDataReason
import com.climaai.wear.data.WearWeather
import com.climaai.wear.data.WearWeatherData
import com.climaai.wear.data.WearWeatherRepository

/** Stands in for a reading the watch does not have. Never a plausible number. */
private const val NO_VALUE = "--"

/**
 * Temperature complication for watch faces.
 * Shows current temperature as short text or ranged value.
 *
 * Suspending rather than blocking: the reading can cost a location fix and a
 * network round trip, and [SuspendingComplicationDataSourceService] answers the
 * listener when that finishes instead of holding the main thread until it does.
 */
class TemperatureComplicationService : SuspendingComplicationDataSourceService() {

    /**
     * Sample values for the watch face picker. This is the one place invented
     * numbers belong — the picker has no watch data to draw and never shows
     * these on a configured complication.
     */
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("72°").build(),
                contentDescription = PlainComplicationText.Builder("Temperature").build()
            ).build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = 72f,
                min = 0f,
                max = 100f,
                contentDescription = PlainComplicationText.Builder("Temperature").build()
            )
                .setText(PlainComplicationText.Builder("72°").build())
                .build()

            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        return when (val weather = WearWeatherRepository.getWeather(this)) {
            is WearWeather.Available -> reading(request.complicationType, weather.data)
            is WearWeather.Unavailable -> noReading(request.complicationType, weather.reason)
        }
    }

    private fun reading(type: ComplicationType, weather: WearWeatherData): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("${weather.temperature}°").build(),
                contentDescription = PlainComplicationText.Builder(
                    "Current temperature: ${weather.temperature}°${ageSuffix(weather)}"
                ).build()
            ).build()

            ComplicationType.RANGED_VALUE -> {
                val low = weather.low
                val high = weather.high
                // Without today's range there is no honest arc to draw, so say
                // so rather than inventing bounds around the current reading.
                if (low == null || high == null) {
                    NoDataComplicationData()
                } else {
                    val min = low.toFloat() - 10
                    val max = high.toFloat() + 10
                    RangedValueComplicationData.Builder(
                        // The arc, not the number, is what gets clamped: a day
                        // that overshoots its own forecast range still reads out
                        // the real temperature below.
                        value = weather.temperature.toFloat().coerceIn(min, max),
                        min = min,
                        max = max,
                        contentDescription = PlainComplicationText.Builder(
                            "Temperature range${ageSuffix(weather)}"
                        ).build()
                    )
                        .setText(PlainComplicationText.Builder("${weather.temperature}°").build())
                        .build()
                }
            }

            else -> null
        }
    }
}

/**
 * Condition complication for watch faces.
 * Shows current weather condition with icon.
 */
class ConditionComplicationService : SuspendingComplicationDataSourceService() {

    /** Sample values for the watch face picker — see the note on the temperature service. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("Sunny").build(),
                contentDescription = PlainComplicationText.Builder("Weather condition").build()
            ).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("Partly Cloudy, 72°").build(),
                contentDescription = PlainComplicationText.Builder("Weather").build()
            ).build()

            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        return when (val weather = WearWeatherRepository.getWeather(this)) {
            is WearWeather.Available -> reading(request.complicationType, weather.data)
            is WearWeather.Unavailable -> noReading(request.complicationType, weather.reason)
        }
    }

    private fun reading(type: ComplicationType, weather: WearWeatherData): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(weather.conditionIcon).build(),
                contentDescription = PlainComplicationText.Builder(
                    "${weather.condition}${ageSuffix(weather)}"
                ).build()
            ).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("${weather.condition}, ${weather.temperature}°").build(),
                contentDescription = PlainComplicationText.Builder(
                    "Current weather${ageSuffix(weather)}"
                ).build()
            )
                .setTitle(PlainComplicationText.Builder(weather.location).build())
                .build()

            else -> null
        }
    }
}

/**
 * ", updated 2h ago" once a reading is past its fresh window, nothing while it
 * is current. A complication has no room to show the age, so the content
 * description is where it goes.
 */
private fun ageSuffix(weather: WearWeatherData): String =
    if (weather.isStale) ", updated ${weather.updatedAgo()}" else ""

/**
 * What every complication shows when the watch has no reading: a visible "--"
 * and a reason in the content description, never a stale or sample number.
 */
private fun noReading(type: ComplicationType, reason: NoDataReason): ComplicationData? {
    val explanation = "Weather unavailable: ${reason.description}"

    return when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(NO_VALUE).build(),
            contentDescription = PlainComplicationText.Builder(explanation).build()
        ).build()

        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(reason.label).build(),
            contentDescription = PlainComplicationText.Builder(explanation).build()
        ).build()

        // A ranged value has no "--" to render: whatever number we pass draws a
        // real arc. NO_DATA lets the watch face draw its own placeholder.
        ComplicationType.RANGED_VALUE -> NoDataComplicationData()

        else -> null
    }
}
