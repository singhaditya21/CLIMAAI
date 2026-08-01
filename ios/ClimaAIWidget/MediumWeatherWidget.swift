//
//  MediumWeatherWidget.swift
//  ClimaAIWidget
//
//  Medium widget showing current weather + hourly forecast
//

import WidgetKit
import SwiftUI

struct MediumWeatherWidget: Widget {
    let kind: String = "MediumWeatherWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: WeatherTimelineProvider()) { entry in
            MediumWeatherWidgetView(entry: entry)
                .widgetContainerBackground()
        }
        .configurationDisplayName("Weather + Hourly")
        .description("Current conditions with hourly forecast")
        .supportedFamilies([.systemMedium])
    }
}

struct MediumWeatherWidgetView: View {
    var entry: WeatherTimelineEntry
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: backgroundColors,
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            
            HStack(spacing: 16) {
                // Current weather (left side)
                VStack(alignment: .leading, spacing: 4) {
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
                    
                    // Temperature + icon
                    HStack(alignment: .top, spacing: 4) {
                        Text("\(Int(entry.weatherData.temperature))°")
                            .font(.system(size: 40, weight: .medium))
                        
                        Image(systemName: entry.weatherData.weatherIcon)
                            .font(.title2)
                    }
                    .foregroundColor(.white)
                    
                    Text(entry.weatherData.weatherDescription)
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                    
                    Text("Feels like \(Int(entry.weatherData.feelsLike))°")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.7))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                
                // Hourly forecast (right side)
                VStack(spacing: 0) {
                    if let hourly = entry.weatherData.hourlyForecast {
                        ForEach(hourly.prefix(4), id: \.hour) { hour in
                            HStack {
                                Text(hour.hour)
                                    .font(.caption2)
                                    .foregroundColor(.white.opacity(0.8))
                                    .frame(width: 35, alignment: .leading)
                                
                                Image(systemName: hour.weatherIcon)
                                    .font(.caption)
                                    .foregroundColor(.white)
                                    .frame(width: 20)
                                
                                Text("\(Int(hour.temperature))°")
                                    .font(.caption)
                                    .fontWeight(.medium)
                                    .foregroundColor(.white)
                                    .frame(width: 30, alignment: .trailing)
                            }
                            .padding(.vertical, 4)
                            
                            if hour.hour != hourly.prefix(4).last?.hour {
                                Divider()
                                    .background(Color.white.opacity(0.3))
                            }
                        }
                    }
                }
                .padding(8)
                .background(Color.white.opacity(0.15))
                .cornerRadius(12)
            }
            .padding()
        }
    }
    
    private var backgroundColors: [Color] {
        let code = entry.weatherData.weatherCode
        switch code {
        case 0: return [.blue, .cyan]
        case 1, 2, 3: return [.blue.opacity(0.8), .gray]
        case 45, 48: return [.gray, .gray.opacity(0.7)]
        case 51...67: return [.blue.opacity(0.7), .indigo]
        case 71...86: return [.cyan, .white]
        case 95...99: return [.purple, .indigo]
        default: return [.blue, .cyan]
        }
    }
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
