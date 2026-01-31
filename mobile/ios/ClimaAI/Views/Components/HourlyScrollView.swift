//
//  HourlyScrollView.swift
//  ClimaAI
//
//  Horizontal scrollable hourly forecast for home screen
//

import SwiftUI

struct HourlyScrollView: View {
    let forecast: [HourlyWeather]
    @State private var scrollPosition: Int?
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Hourly Forecast")
                .font(.headline)
                .foregroundColor(.primary)
                .padding(.horizontal)
            
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
                    ForEach(Array(forecast.prefix(24).enumerated()), id: \.element.id) { index, hour in
                        HourlyItemView(hour: hour, isNow: index == 0)
                            .id(index)
                    }
                }
                .padding(.horizontal)
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.viewAligned)
        }
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(.ultraThinMaterial)
        )
    }
}

struct HourlyItemView: View {
    let hour: HourlyWeather
    let isNow: Bool
    
    var body: some View {
        VStack(spacing: 8) {
            // Time
            Text(isNow ? "Now" : formatHour(hour.time))
                .font(.caption)
                .fontWeight(isNow ? .bold : .regular)
                .foregroundColor(isNow ? .primary : .secondary)
            
            // Weather icon
            Image(systemName: weatherIcon(for: hour.weatherCode))
                .font(.title2)
                .foregroundStyle(iconGradient(for: hour.weatherCode))
                .frame(height: 30)
            
            // Temperature
            Text("\(Int(hour.temperature))°")
                .font(.title3)
                .fontWeight(.semibold)
            
            // Precipitation probability
            if hour.precipitationProbability > 0 {
                HStack(spacing: 2) {
                    Image(systemName: "drop.fill")
                        .font(.caption2)
                    Text("\(hour.precipitationProbability)%")
                        .font(.caption2)
                }
                .foregroundColor(.blue)
            } else {
                Text(" ")
                    .font(.caption2)
            }
        }
        .frame(width: 65)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(isNow ? Color.blue.opacity(0.15) : Color.clear)
        )
    }
    
    private func formatHour(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "ha"
        return formatter.string(from: date).lowercased()
    }
    
    private func weatherIcon(for code: Int) -> String {
        switch code {
        case 0: return "sun.max.fill"
        case 1, 2, 3: return "cloud.sun.fill"
        case 45, 48: return "cloud.fog.fill"
        case 51, 53, 55: return "cloud.drizzle.fill"
        case 61, 63, 65: return "cloud.rain.fill"
        case 71, 73, 75, 77: return "cloud.snow.fill"
        case 80, 81, 82: return "cloud.heavyrain.fill"
        case 85, 86: return "cloud.snow.fill"
        case 95, 96, 99: return "cloud.bolt.rain.fill"
        default: return "cloud.fill"
        }
    }
    
    private func iconGradient(for code: Int) -> LinearGradient {
        let colors: [Color]
        switch code {
        case 0: colors = [.yellow, .orange]
        case 1, 2, 3: colors = [.gray, .blue]
        case 45, 48: colors = [.gray, .white]
        case 51...67: colors = [.blue, .cyan]
        case 71...86: colors = [.white, .cyan]
        case 95...99: colors = [.purple, .yellow]
        default: colors = [.gray, .blue]
        }
        return LinearGradient(colors: colors, startPoint: .top, endPoint: .bottom)
    }
}

// MARK: - Preview

#Preview {
    ZStack {
        Color.gray.opacity(0.2).ignoresSafeArea()
        
        HourlyScrollView(forecast: [
            HourlyWeather(
                id: UUID(),
                time: Date(),
                temperature: 22,
                feelsLike: 24,
                precipitationProbability: 10,
                precipitation: 0,
                weatherCode: 0,
                weatherDescription: "Clear",
                windSpeed: 10,
                windDirection: 180,
                humidity: 50,
                cloudCover: 10,
                uvIndex: 5
            ),
            HourlyWeather(
                id: UUID(),
                time: Date().addingTimeInterval(3600),
                temperature: 23,
                feelsLike: 25,
                precipitationProbability: 20,
                precipitation: 0,
                weatherCode: 1,
                weatherDescription: "Partly Cloudy",
                windSpeed: 12,
                windDirection: 190,
                humidity: 55,
                cloudCover: 30,
                uvIndex: 6
            ),
            HourlyWeather(
                id: UUID(),
                time: Date().addingTimeInterval(7200),
                temperature: 24,
                feelsLike: 26,
                precipitationProbability: 60,
                precipitation: 0.5,
                weatherCode: 61,
                weatherDescription: "Light Rain",
                windSpeed: 15,
                windDirection: 200,
                humidity: 70,
                cloudCover: 80,
                uvIndex: 2
            )
        ])
        .padding()
    }
}
