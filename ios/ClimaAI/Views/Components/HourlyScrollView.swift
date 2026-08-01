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
        VStack(alignment: .leading, spacing: 10) {
            // Section header - refined
            Text("HOURLY")
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(.secondary)
                .tracking(1.5)
                .padding(.horizontal, 16)
            
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 8) {
                    ForEach(Array(forecast.prefix(24).enumerated()), id: \.element.id) { index, hour in
                        HourlyItemView(hour: hour, isNow: index == 0)
                            .id(index)
                    }
                }
                .padding(.horizontal, 16)
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.viewAligned)
        }
        .padding(.vertical, 14)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(.ultraThinMaterial)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(.white.opacity(0.08), lineWidth: 1)
                )
        )
    }
}

struct HourlyItemView: View {
    let hour: HourlyWeather
    let isNow: Bool
    
    var body: some View {
        VStack(spacing: 6) {
            // Time
            Text(isNow ? "Now" : formatHour(hour.time))
                .font(.system(size: 10, weight: isNow ? .semibold : .medium))
                .foregroundColor(isNow ? .white : .secondary)
            
            // Weather icon
            Image(systemName: weatherIcon(for: hour.weatherCode))
                .font(.system(size: 18))
                .foregroundStyle(iconGradient(for: hour.weatherCode))
                .frame(height: 22)
            
            // Temperature
            Text("\(Int(hour.temperature))°")
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(isNow ? .white : .primary)
            
            // Precipitation probability
            if hour.precipitationProbability > 0 {
                Text("\(hour.precipitationProbability)%")
                    .font(.system(size: 9, weight: .medium))
                    .foregroundColor(.cyan)
            } else {
                Text(" ")
                    .font(.system(size: 9))
            }
        }
        .frame(width: 48)
        .padding(.vertical, 10)
        .background(
            Capsule()
                .fill(isNow ? Color.indigo : Color.white.opacity(0.05))
                .overlay(
                    Capsule()
                        .stroke(isNow ? Color.indigo.opacity(0.5) : Color.white.opacity(0.05), lineWidth: 1)
                )
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
                time: Date(),
                temperature: 22,
                feelsLike: 24,
                feelsLikeShade: nil,
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
                time: Date().addingTimeInterval(3600),
                temperature: 23,
                feelsLike: 25,
                feelsLikeShade: nil,
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
                time: Date().addingTimeInterval(7200),
                temperature: 24,
                feelsLike: 26,
                feelsLikeShade: nil,
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
