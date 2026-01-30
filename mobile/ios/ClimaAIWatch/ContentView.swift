//
//  ContentView.swift
//  ClimaAIWatch
//
//  Main watch app view showing current weather
//

import SwiftUI

struct ContentView: View {
    @StateObject private var weatherManager = WatchWeatherManager()
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    // Current conditions
                    currentWeatherCard
                    
                    // Quick stats
                    statsGrid
                    
                    // Hourly preview
                    hourlyPreview
                }
                .padding(.horizontal, 4)
            }
            .navigationTitle("ClimaAI")
            .navigationBarTitleDisplayMode(.inline)
        }
        .onAppear {
            weatherManager.loadWeatherData()
        }
    }
    
    // MARK: - Current Weather Card
    
    private var currentWeatherCard: some View {
        VStack(spacing: 4) {
            // Location
            Text(weatherManager.locationName)
                .font(.caption2)
                .foregroundColor(.secondary)
            
            // Temperature + Icon
            HStack(spacing: 8) {
                Image(systemName: weatherManager.weatherIcon)
                    .font(.title2)
                    .foregroundColor(.blue)
                
                Text("\(Int(weatherManager.temperature))°")
                    .font(.system(size: 44, weight: .semibold))
            }
            
            // Description
            Text(weatherManager.weatherDescription)
                .font(.caption)
                .foregroundColor(.secondary)
            
            // Feels like
            Text("Feels \(Int(weatherManager.feelsLike))°")
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .padding(.vertical, 8)
    }
    
    // MARK: - Stats Grid
    
    private var statsGrid: some View {
        HStack(spacing: 8) {
            statItem(icon: "drop.fill", value: "\(weatherManager.humidity)%", label: "Humidity")
            statItem(icon: "wind", value: "\(Int(weatherManager.windSpeed))", label: "Wind")
            statItem(icon: "sun.max.fill", value: "\(weatherManager.uvIndex)", label: "UV")
        }
    }
    
    private func statItem(icon: String, value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundColor(.blue)
            Text(value)
                .font(.caption2)
                .fontWeight(.semibold)
            Text(label)
                .font(.system(size: 8))
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
        .background(Color(.darkGray).opacity(0.3))
        .cornerRadius(8)
    }
    
    // MARK: - Hourly Preview
    
    private var hourlyPreview: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Next Hours")
                .font(.caption2)
                .foregroundColor(.secondary)
            
            HStack(spacing: 6) {
                ForEach(weatherManager.hourlyForecast.prefix(4), id: \.hour) { hour in
                    VStack(spacing: 2) {
                        Text(hour.hour)
                            .font(.system(size: 9))
                            .foregroundColor(.secondary)
                        
                        Image(systemName: hour.icon)
                            .font(.caption2)
                            .foregroundColor(.blue)
                        
                        Text("\(Int(hour.temperature))°")
                            .font(.caption2)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(.vertical, 6)
            .background(Color(.darkGray).opacity(0.3))
            .cornerRadius(8)
        }
    }
}

// MARK: - Weather Manager

class WatchWeatherManager: ObservableObject {
    @Published var locationName = "Loading..."
    @Published var temperature: Double = 0
    @Published var feelsLike: Double = 0
    @Published var weatherDescription = ""
    @Published var weatherIcon = "sun.max.fill"
    @Published var humidity = 0
    @Published var windSpeed: Double = 0
    @Published var uvIndex = 0
    @Published var hourlyForecast: [HourlyItem] = []
    
    struct HourlyItem: Identifiable {
        let id = UUID()
        let hour: String
        let temperature: Double
        let icon: String
    }
    
    func loadWeatherData() {
        // Load from shared App Group (same as widgets)
        guard let groupURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: "group.com.climaai.shared"
        ) else {
            loadPlaceholderData()
            return
        }
        
        let fileURL = groupURL.appendingPathComponent("weather_data.json")
        
        do {
            let data = try Data(contentsOf: fileURL)
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let weatherData = try decoder.decode(WatchWeatherData.self, from: data)
            
            DispatchQueue.main.async {
                self.locationName = weatherData.locationName
                self.temperature = weatherData.temperature
                self.feelsLike = weatherData.feelsLike
                self.weatherDescription = weatherData.weatherDescription
                self.weatherIcon = self.iconForCode(weatherData.weatherCode)
                self.humidity = weatherData.humidity
                self.windSpeed = weatherData.windSpeed
                
                self.hourlyForecast = weatherData.hourlyForecast.map { hour in
                    HourlyItem(
                        hour: hour.hour,
                        temperature: hour.temperature,
                        icon: self.iconForCode(hour.weatherCode)
                    )
                }
            }
        } catch {
            loadPlaceholderData()
        }
    }
    
    private func loadPlaceholderData() {
        locationName = "San Francisco"
        temperature = 18
        feelsLike = 17
        weatherDescription = "Partly Cloudy"
        weatherIcon = "cloud.sun.fill"
        humidity = 65
        windSpeed = 12
        uvIndex = 4
        
        hourlyForecast = [
            HourlyItem(hour: "Now", temperature: 18, icon: "cloud.sun.fill"),
            HourlyItem(hour: "2PM", temperature: 20, icon: "sun.max.fill"),
            HourlyItem(hour: "3PM", temperature: 21, icon: "sun.max.fill"),
            HourlyItem(hour: "4PM", temperature: 19, icon: "cloud.fill")
        ]
    }
    
    private func iconForCode(_ code: Int) -> String {
        switch code {
        case 0: return "sun.max.fill"
        case 1, 2: return "cloud.sun.fill"
        case 3: return "cloud.fill"
        case 45, 48: return "cloud.fog.fill"
        case 51, 53, 55, 61, 63, 65: return "cloud.rain.fill"
        case 71, 73, 75, 77: return "cloud.snow.fill"
        case 80, 81, 82: return "cloud.heavyrain.fill"
        case 95, 96, 99: return "cloud.bolt.rain.fill"
        default: return "sun.max.fill"
        }
    }
}

// MARK: - Shared Data Models

struct WatchWeatherData: Codable {
    let temperature: Double
    let feelsLike: Double
    let weatherCode: Int
    let weatherDescription: String
    let humidity: Int
    let windSpeed: Double
    let locationName: String
    let hourlyForecast: [WatchHourlyData]
}

struct WatchHourlyData: Codable {
    let hour: String
    let temperature: Double
    let weatherCode: Int
}

#Preview {
    ContentView()
}
