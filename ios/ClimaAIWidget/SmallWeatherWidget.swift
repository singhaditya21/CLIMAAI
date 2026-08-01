//
//  SmallWeatherWidget.swift
//  ClimaAIWidget
//
//  Small widget showing current temperature and weather icon
//

import WidgetKit
import SwiftUI

struct SmallWeatherWidget: Widget {
    let kind: String = "SmallWeatherWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: WeatherTimelineProvider()) { entry in
            SmallWeatherWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Current Weather")
        .description("Shows current temperature and conditions")
        .supportedFamilies([.systemSmall])
    }
}

struct SmallWeatherWidgetView: View {
    var entry: WeatherTimelineEntry
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: backgroundColors,
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            
            VStack(alignment: .leading, spacing: 8) {
                // Location
                HStack(spacing: 4) {
                    Image(systemName: "location.fill")
                        .font(.caption2)
                    Text(entry.weatherData.locationName)
                        .font(.caption)
                        .fontWeight(.medium)
                        .lineLimit(1)
                }
                .foregroundColor(.white.opacity(0.9))
                
                Spacer()
                
                // Weather icon
                Image(systemName: entry.weatherData.weatherIcon)
                    .font(.system(size: 32))
                    .foregroundStyle(.white)
                
                // Temperature
                Text("\(Int(entry.weatherData.temperature))°")
                    .font(.system(size: 36, weight: .medium))
                    .foregroundColor(.white)
                
                // Description
                Text(entry.weatherData.weatherDescription)
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.8))
            }
            .padding()
        }
    }
    
    private var backgroundColors: [Color] {
        let code = entry.weatherData.weatherCode
        switch code {
        case 0: return [.blue, .cyan]  // Clear
        case 1, 2, 3: return [.blue.opacity(0.8), .gray]  // Partly cloudy
        case 45, 48: return [.gray, .gray.opacity(0.7)]  // Fog
        case 51...67: return [.blue.opacity(0.7), .indigo]  // Rain
        case 71...86: return [.cyan, .white]  // Snow
        case 95...99: return [.purple, .indigo]  // Thunder
        default: return [.blue, .cyan]
        }
    }
}

// MARK: - Timeline Provider

struct WeatherTimelineProvider: TimelineProvider {
    func placeholder(in context: Context) -> WeatherTimelineEntry {
        WeatherTimelineEntry(date: Date(), weatherData: .placeholder)
    }

    func getSnapshot(in context: Context, completion: @escaping (WeatherTimelineEntry) -> Void) {
        let entry = WeatherTimelineEntry(
            date: Date(),
            weatherData: WidgetDataManager.loadWeatherData() ?? .placeholder
        )
        completion(entry)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WeatherTimelineEntry>) -> Void) {
        let currentDate = Date()
        let weatherData = WidgetDataManager.loadWeatherData() ?? .placeholder
        
        let entry = WeatherTimelineEntry(date: currentDate, weatherData: weatherData)
        
        // Refresh every 30 minutes
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 30, to: currentDate)!
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        
        completion(timeline)
    }
}

struct WeatherTimelineEntry: TimelineEntry {
    let date: Date
    let weatherData: WidgetWeatherData
}

// Widget #Preview macros were removed here.
//
// `#Preview(as:widget:timeline:)` is only available in application extensions
// from iOS 17, and this extension deploys to iOS 16 — it failed to compile.
// They are Xcode-canvas conveniences with no runtime effect, so removing them
// costs nothing at runtime.
//
// To restore them, raise this target's deploymentTarget to 17.0 in
// ios/project.yml. Note that also stops the widget installing on iOS 16.
