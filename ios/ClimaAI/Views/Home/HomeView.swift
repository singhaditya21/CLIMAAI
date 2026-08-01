//
//  HomeView.swift
//  ClimaAI
//
//  Main weather dashboard
//

import SwiftUI

struct HomeView: View {
    @EnvironmentObject var weatherViewModel: WeatherViewModel
    @EnvironmentObject var aiInsightsViewModel: AIInsightsViewModel
    @EnvironmentObject var subscriptionViewModel: SubscriptionViewModel
    @StateObject private var themeManager = ThemeManager.shared
    @State private var showingLocationSearch = false
    @State private var showingSettings = false
    
    var body: some View {
        NavigationView {
            ZStack {
                // Animated weather background
                if let weather = weatherViewModel.currentWeather {
                    WeatherBackground(
                        weatherCode: weather.weatherCode,
                        isDay: weather.isDay
                    )
                } else {
                    weatherBackgroundGradient
                        .ignoresSafeArea()
                }
                
                ScrollView {
                    VStack(spacing: 20) {
                        // Location header
                        locationHeader
                        
                        // Precipitation alert banner
                        if let nowcast = weatherViewModel.precipitationNowcast {
                            PrecipitationBanner(nowcast: nowcast)
                        }
                        
                        // Current weather card
                        if let weather = weatherViewModel.currentWeather {
                            currentWeatherCard(weather: weather)
                        }
                        
                        // Horizontal hourly forecast
                        if !weatherViewModel.hourlyForecast.isEmpty {
                            HourlyScrollView(forecast: weatherViewModel.hourlyForecast)
                        }
                        
                        // Sunrise/Sunset card
                        if let today = weatherViewModel.dailyForecast.first {
                            sunriseSunsetCard(today: today)
                        }
                        
                        // AI Insights preview (premium feature)
                        if subscriptionViewModel.isPremium,
                           let summary = aiInsightsViewModel.dailySummary {
                            aiInsightsPreview(summary: summary)
                        } else if !subscriptionViewModel.isPremium {
                            premiumUpsellCard
                        }
                        
                        // Quick forecast links
                        forecastLinksCard
                        
                        // Air Quality
                        if let airQuality = weatherViewModel.airQuality {
                            airQualityCard(airQuality: airQuality)
                        }
                        
                        Spacer(minLength: 20)
                    }
                    .padding()
                }
                .refreshable {
                    await refreshData()
                }
                
                // Loading overlay
                if weatherViewModel.isLoading {
                    LoadingOverlay()
                }
            }
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showingSettings = true
                    } label: {
                        Image(systemName: "gearshape.fill")
                            .foregroundColor(.primary)
                    }
                }
            }
            .sheet(isPresented: $showingLocationSearch) {
                LocationSwitcherView()
            }
            .sheet(isPresented: $showingSettings) {
                SettingsView()
            }
        }
        .task {
            await loadData()
        }
    }
    
    // MARK: - Subviews
    
    private var locationHeader: some View {
        Button {
            showingLocationSearch = true
        } label: {
            HStack {
                Image(systemName: "location.fill")
                    .font(.caption)
                Text(weatherViewModel.locationName)
                    .font(.title3)
                    .fontWeight(.semibold)
                Image(systemName: "chevron.down")
                    .font(.caption)
            }
            .foregroundColor(.primary)
        }
    }
    
    private func currentWeatherCard(weather: CurrentWeather) -> some View {
        VStack(spacing: 12) {
            // Temperature - refined typography
            VStack(spacing: 4) {
                Text("\(Int(weather.temperature))°")
                    .font(.system(size: 56, weight: .ultraLight, design: .rounded))
                    .foregroundStyle(
                        .linearGradient(
                            colors: [.white, .white.opacity(0.7)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .tracking(-2)
                
                Text(weather.weatherDescription)
                    .font(.system(size: 15, weight: .medium))
                    .tracking(0.5)
                
                HStack(spacing: 8) {
                    Text("Feels like \(Int(weather.feelsLike))°")
                        .font(.system(size: 12, weight: .regular))
                        .foregroundColor(.secondary)

                    if let shade = weather.feelsLikeShade {
                        Text("•")
                            .foregroundColor(.secondary.opacity(0.5))
                        Text("Shade \(Int(shade))°")
                            .font(.system(size: 12, weight: .regular))
                            .foregroundColor(.secondary)
                    }
                }
            }
            
            // Subtle separator
            Rectangle()
                .fill(.white.opacity(0.1))
                .frame(height: 1)
                .padding(.horizontal, 20)
            
            // Weather details - compact 2-row grid
            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 12) {
                WeatherDetailItem(icon: "humidity.fill", label: "Humidity", value: "\(weather.humidity)%")
                WeatherDetailItem(icon: "wind", label: "Wind", value: "\(Int(weather.windSpeed))")
                WeatherDetailItem(icon: "drop.fill", label: "Rain", value: String(format: "%.1f", weather.precipitation))
                WeatherDetailItem(icon: "sun.max.fill", label: "UV", value: "\(Int(weather.uvIndex))")
            }
        }
        .padding(.vertical, 20)
        .padding(.horizontal, 16)
        .background(
            RoundedRectangle(cornerRadius: 24)
                .fill(.ultraThinMaterial)
                .overlay(
                    RoundedRectangle(cornerRadius: 24)
                        .stroke(.white.opacity(0.1), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.2), radius: 20, y: 10)
        )
    }
    
    private func aiInsightsPreview(summary: DailySummary) -> some View {
        NavigationLink {
            AIInsightsView()
        } label: {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Image(systemName: "sparkles")
                        .foregroundStyle(
                            .linearGradient(
                                colors: [.purple, .pink],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                    Text("AI Insights")
                        .font(.headline)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Text(summary.summary)
                    .font(.subheadline)
                    .foregroundColor(.primary)
                    .lineLimit(3)
                
                if !summary.highlights.isEmpty {
                    HStack {
                        ForEach(summary.highlights.prefix(2), id: \.self) { highlight in
                            Label(highlight, systemImage: "checkmark.circle.fill")
                                .font(.caption)
                                .foregroundColor(.green)
                                .lineLimit(1)
                        }
                    }
                }
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(.ultraThinMaterial)
            )
        }
        .buttonStyle(.plain)
    }
    
    private var premiumUpsellCard: some View {
        Button {
            subscriptionViewModel.showPaywall = true
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Image(systemName: "crown.fill")
                            .foregroundColor(.yellow)
                        Text("Unlock AI Insights")
                            .font(.headline)
                    }
                    
                    Text("Get personalized outfit, activity, and health recommendations")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .foregroundColor(.secondary)
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(.ultraThinMaterial)
            )
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $subscriptionViewModel.showPaywall) {
            PaywallView()
        }
    }
    
    private func sunriseSunsetCard(today: DailyWeather) -> some View {
        HStack(spacing: 24) {
            // Sunrise
            VStack(spacing: 8) {
                Image(systemName: "sunrise.fill")
                    .font(.title2)
                    .foregroundStyle(
                        .linearGradient(
                            colors: [.orange, .yellow],
                            startPoint: .bottom,
                            endPoint: .top
                        )
                    )
                Text("Sunrise")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(formatTime(today.sunrise))
                    .font(.subheadline)
                    .fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
            
            Divider()
                .frame(height: 50)
            
            // Sunset
            VStack(spacing: 8) {
                Image(systemName: "sunset.fill")
                    .font(.title2)
                    .foregroundStyle(
                        .linearGradient(
                            colors: [.orange, .red],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                Text("Sunset")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(formatTime(today.sunset))
                    .font(.subheadline)
                    .fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(.ultraThinMaterial)
        )
    }
    
    private func formatTime(_ isoString: String) -> String {
        // Parse ISO 8601 time and format as HH:mm
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate, .withTime, .withColonSeparatorInTime]
        
        if let date = formatter.date(from: isoString) {
            let displayFormatter = DateFormatter()
            displayFormatter.dateFormat = "h:mm a"
            return displayFormatter.string(from: date)
        }
        
        // Fallback: try to extract time portion
        if let timeRange = isoString.range(of: "T") {
            let timePart = String(isoString[timeRange.upperBound...].prefix(5))
            return timePart
        }
        
        return isoString
    }
    
    private var forecastLinksCard: some View {
        HStack(spacing: 12) {
            NavigationLink {
                HourlyForecastView()
            } label: {
                ForecastLinkCard(icon: "clock.fill", title: "Hourly", gradient: [.blue, .cyan])
            }
            
            NavigationLink {
                DailyForecastView()
            } label: {
                ForecastLinkCard(icon: "calendar", title: "Daily", gradient: [.orange, .red])
            }
        }
    }
    
    private func airQualityCard(airQuality: AirQuality) -> some View {
        NavigationLink {
            AirQualityView()
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Image(systemName: "aqi.medium")
                        Text("Air Quality")
                            .font(.headline)
                    }
                    
                    HStack {
                        Text("AQI \(airQuality.aqi)")
                            .font(.title2)
                            .fontWeight(.bold)
                        Text(airQuality.category)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                }
                
                Spacer()
                
                Circle()
                    .fill(aqiColor(for: airQuality.aqi))
                    .frame(width: 50, height: 50)
                    .overlay(
                        Text("\(airQuality.aqi)")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                    )
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(.ultraThinMaterial)
            )
        }
        .buttonStyle(.plain)
    }
    
    private var weatherBackgroundGradient: some View {
        LinearGradient(
            colors: backgroundColors,
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .opacity(0.3)
    }
    
    private var backgroundColors: [Color] {
        guard let weather = weatherViewModel.currentWeather else {
            return [.blue, .cyan]
        }
        
        // Determine colors based on weather condition
        let description = weather.weatherDescription.lowercased()
        
        if description.contains("clear") || description.contains("sunny") {
            return [.yellow, .orange]
        } else if description.contains("cloud") {
            return [.gray, .blue]
        } else if description.contains("rain") || description.contains("drizzle") {
            return [.blue, .indigo]
        } else if description.contains("snow") {
            return [.white, .cyan]
        } else if description.contains("thunder") || description.contains("storm") {
            return [.purple, .indigo]
        } else {
            return [.blue, .cyan]
        }
    }
    
    private func aqiColor(for aqi: Int) -> Color {
        switch aqi {
        case 0...50: return .green
        case 51...100: return .yellow
        case 101...150: return .orange
        case 151...200: return .red
        default: return .purple
        }
    }
    
    // MARK: - Data Loading
    
    private func loadData() async {
        weatherViewModel.requestLocationPermission()
        await weatherViewModel.fetchWeatherForCurrentLocation()
        
        if subscriptionViewModel.isPremium,
           let location = weatherViewModel.currentLocation {
            await aiInsightsViewModel.fetchDailySummary(
                latitude: location.coordinate.latitude,
                longitude: location.coordinate.longitude,
                locationName: weatherViewModel.locationName
            )
        }
    }
    
    private func refreshData() async {
        await weatherViewModel.refresh()
        if subscriptionViewModel.isPremium,
           let location = weatherViewModel.currentLocation {
            await aiInsightsViewModel.fetchDailySummary(
                latitude: location.coordinate.latitude,
                longitude: location.coordinate.longitude,
                locationName: weatherViewModel.locationName
            )
        }
    }
}

// MARK: - Supporting Views

struct WeatherDetailItem: View {
    let icon: String
    let label: String
    let value: String
    
    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(.blue)
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            Text(value)
                .font(.subheadline)
                .fontWeight(.semibold)
        }
    }
}

struct ForecastLinkCard: View {
    let icon: String
    let title: String
    let gradient: [Color]
    
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(
                    .linearGradient(
                        colors: gradient,
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Text(title)
                .font(.subheadline)
                .fontWeight(.medium)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(.ultraThinMaterial)
        )
    }
}

struct LoadingOverlay: View {
    var body: some View {
        ZStack {
            Color.black.opacity(0.3)
                .ignoresSafeArea()
            
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                .scaleEffect(1.5)
        }
    }
}

#Preview {
    HomeView()
        .environmentObject(WeatherViewModel())
        .environmentObject(AIInsightsViewModel())
        .environmentObject(SubscriptionViewModel())
}
