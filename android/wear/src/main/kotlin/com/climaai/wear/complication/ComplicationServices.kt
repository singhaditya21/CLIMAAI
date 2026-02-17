package com.climaai.wear.complication

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.climaai.wear.data.WearWeatherRepository
import kotlinx.coroutines.runBlocking

/**
 * Temperature complication for watch faces.
 * Shows current temperature as short text or ranged value.
 */
class TemperatureComplicationService : ComplicationDataSourceService() {
    
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
    
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val weather = runBlocking { WearWeatherRepository.getWeather() }
        
        val complicationData = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("${weather.temperature}°").build(),
                contentDescription = PlainComplicationText.Builder("Current temperature: ${weather.temperature}°").build()
            ).build()
            
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = weather.temperature.toFloat(),
                min = weather.low.toFloat() - 10,
                max = weather.high.toFloat() + 10,
                contentDescription = PlainComplicationText.Builder("Temperature range").build()
            )
                .setText(PlainComplicationText.Builder("${weather.temperature}°").build())
                .build()
            
            else -> null
        }
        
        complicationData?.let { listener.onComplicationData(it) }
    }
}

/**
 * Condition complication for watch faces.
 * Shows current weather condition with icon.
 */
class ConditionComplicationService : ComplicationDataSourceService() {
    
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
    
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val weather = runBlocking { WearWeatherRepository.getWeather() }
        
        val complicationData = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(weather.conditionIcon).build(),
                contentDescription = PlainComplicationText.Builder(weather.condition).build()
            ).build()
            
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("${weather.condition}, ${weather.temperature}°").build(),
                contentDescription = PlainComplicationText.Builder("Current weather").build()
            )
                .setTitle(PlainComplicationText.Builder(weather.location).build())
                .build()
            
            else -> null
        }
        
        complicationData?.let { listener.onComplicationData(it) }
    }
}
