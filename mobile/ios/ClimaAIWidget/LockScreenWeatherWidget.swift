//
//  LockScreenWeatherWidget.swift
//  ClimaAIWidget
//
//  iOS 16+ Lock Screen accessories (circular, rectangular, inline)
//

import WidgetKit
import SwiftUI

@available(iOSApplicationExtension 16.0, *)
struct LockScreenWeatherWidget: Widget {
    let kind: String = "LockScreenWeatherWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: WeatherTimelineProvider()) { entry in
            LockScreenWidgetView(entry: entry)
        }
        .configurationDisplayName("Weather")
        .description("Quick weather on your Lock Screen")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

@available(iOSApplicationExtension 16.0, *)
struct LockScreenWidgetView: View {
    @Environment(\.widgetFamily) var family
    var entry: WeatherTimelineEntry
    
    var body: some View {
        switch family {
        case .accessoryCircular:
            circularView
        case .accessoryRectangular:
            rectangularView
        case .accessoryInline:
            inlineView
        default:
            circularView
        }
    }
    
    // MARK: - Circular (like Watch complication)
    
    private var circularView: some View {
        ZStack {
            AccessoryWidgetBackground()
            
            VStack(spacing: 2) {
                Image(systemName: entry.weatherData.weatherIcon)
                    .font(.title3)
                Text("\(Int(entry.weatherData.temperature))°")
                    .font(.headline)
                    .fontWeight(.semibold)
            }
        }
    }
    
    // MARK: - Rectangular (wide mini card)
    
    private var rectangularView: some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.weatherData.locationName)
                    .font(.caption)
                    .lineLimit(1)
                
                HStack(spacing: 4) {
                    Image(systemName: entry.weatherData.weatherIcon)
                    Text("\(Int(entry.weatherData.temperature))°")
                        .font(.title2)
                        .fontWeight(.semibold)
                }
            }
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 2) {
                Text(entry.weatherData.weatherDescription)
                    .font(.caption2)
                Text("Feels \(Int(entry.weatherData.feelsLike))°")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
    
    // MARK: - Inline (single line text)
    
    private var inlineView: some View {
        HStack(spacing: 4) {
            Image(systemName: entry.weatherData.weatherIcon)
            Text("\(Int(entry.weatherData.temperature))° \(entry.weatherData.weatherDescription)")
        }
    }
}

@available(iOSApplicationExtension 16.0, *)
#Preview(as: .accessoryCircular) {
    LockScreenWeatherWidget()
} timeline: {
    WeatherTimelineEntry(date: .now, weatherData: .placeholder)
}

@available(iOSApplicationExtension 16.0, *)
#Preview(as: .accessoryRectangular) {
    LockScreenWeatherWidget()
} timeline: {
    WeatherTimelineEntry(date: .now, weatherData: .placeholder)
}
