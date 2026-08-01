//
//  LargeWeatherWidget.swift
//  ClimaAIWidget
//
//  Large widget showing full weather details + daily forecast
//

import WidgetKit
import SwiftUI

struct LargeWeatherWidget: Widget {
    let kind: String = "LargeWeatherWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: WeatherTimelineProvider()) { entry in
            LargeWeatherWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Full Weather")
        .description("Complete weather overview with forecasts")
        .supportedFamilies([.systemLarge])
    }
}

struct LargeWeatherWidgetView: View {
    var entry: WeatherTimelineEntry
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: backgroundColors,
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            
            VStack(spacing: 16) {
                // Header with location
                HStack {
                    HStack(spacing: 4) {
                        Image(systemName: "location.fill")
                            .font(.caption)
                        Text(entry.weatherData.locationName)
                            .font(.subheadline)
                            .fontWeight(.semibold)
                    }
                    
                    Spacer()
                    
                    Text("Updated \(formattedTime)")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.7))
                }
                .foregroundColor(.white)
                
                // Current weather
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(Int(entry.weatherData.temperature))°")
                            .font(.system(size: 56, weight: .thin))
                        
                        Text(entry.weatherData.weatherDescription)
                            .font(.body)
                        
                        Text("Feels like \(Int(entry.weatherData.feelsLike))°")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.8))
                    }
                    
                    Spacer()
                    
                    Image(systemName: entry.weatherData.weatherIcon)
                        .font(.system(size: 50))
                }
                .foregroundColor(.white)
                
                // Weather stats row
                HStack(spacing: 24) {
                    WeatherStatView(icon: "humidity.fill", value: "\(entry.weatherData.humidity)%", label: "Humidity")
                    WeatherStatView(icon: "wind", value: "\(Int(entry.weatherData.windSpeed))", label: "km/h")
                    
                    if let sunrise = entry.weatherData.sunrise {
                        WeatherStatView(icon: "sunrise.fill", value: sunrise, label: "Sunrise")
                    }
                    if let sunset = entry.weatherData.sunset {
                        WeatherStatView(icon: "sunset.fill", value: sunset, label: "Sunset")
                    }
                }
                .foregroundColor(.white)
                
                Divider()
                    .background(Color.white.opacity(0.3))
                
                // Hourly forecast
                if let hourly = entry.weatherData.hourlyForecast {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Today's Forecast")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.8))
                        
                        HStack(spacing: 0) {
                            ForEach(hourly.prefix(6), id: \.hour) { hour in
                                VStack(spacing: 6) {
                                    Text(hour.hour)
                                        .font(.caption2)
                                        .foregroundColor(.white.opacity(0.8))
                                    
                                    Image(systemName: hour.weatherIcon)
                                        .font(.title3)
                                    
                                    Text("\(Int(hour.temperature))°")
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                }
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                            }
                        }
                    }
                }
                
                // AI Summary (placeholder for premium users)
                HStack {
                    Image(systemName: "sparkles")
                        .foregroundStyle(.yellow)
                    Text("Great day for outdoor activities")
                        .font(.caption)
                    Spacer()
                }
                .padding(8)
                .background(Color.white.opacity(0.15))
                .cornerRadius(8)
                .foregroundColor(.white)
            }
            .padding()
        }
    }
    
    private var formattedTime: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"
        return formatter.string(from: entry.weatherData.lastUpdated)
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

struct WeatherStatView: View {
    let icon: String
    let value: String
    let label: String
    
    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .font(.caption)
            Text(value)
                .font(.caption)
                .fontWeight(.semibold)
            Text(label)
                .font(.caption2)
                .foregroundColor(.white.opacity(0.7))
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
