//
//  DailyForecastView.swift
//  ClimaAI
//
//  7/14-day forecast
//

import SwiftUI

struct DailyForecastView: View {
    @EnvironmentObject var weatherViewModel: WeatherViewModel
    @EnvironmentObject var subscriptionViewModel: SubscriptionViewModel
    
    var displayedDays: Int {
        subscriptionViewModel.isPremium ? 14 : 7
    }
    
    var body: some View {
        let slice = Array(weatherViewModel.dailyForecast.prefix(displayedDays))
        let weekMin = slice.map(\.temperatureMin).min() ?? 0
        let weekMax = slice.map(\.temperatureMax).max() ?? 100

        ScrollView {
            VStack(spacing: 12) {
                // Daily forecast list
                ForEach(Array(slice.enumerated()), id: \.offset) { index, day in
                    DailyForecastRow(day: day, dayIndex: index, weekMin: weekMin, weekMax: weekMax)
                }
                
                // Premium upsell for extended forecast
                if !subscriptionViewModel.isPremium && weatherViewModel.dailyForecast.count > 7 {
                    Button {
                        subscriptionViewModel.showPaywall = true
                    } label: {
                        VStack(spacing: 12) {
                            HStack {
                                Image(systemName: "crown.fill")
                                    .foregroundColor(.yellow)
                                Text("Unlock 14-Day Forecast")
                                    .fontWeight(.semibold)
                            }
                            
                            Text("Get extended forecasts and more with Premium")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(.ultraThinMaterial)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color.yellow, lineWidth: 2)
                                )
                        )
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal)
                }
            }
            .padding(.vertical)
        }
        .navigationTitle("Daily Forecast")
        .navigationBarTitleDisplayMode(.large)
        .sheet(isPresented: $subscriptionViewModel.showPaywall) {
            PaywallView()
        }
    }
}

struct DailyForecastRow: View {
    let day: DailyWeather
    let dayIndex: Int
    var weekMin: Double = 0
    var weekMax: Double = 40
    
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                // Day name
                VStack(alignment: .leading, spacing: 4) {
                    Text(dayName)
                        .font(.headline)
                    Text(dateString)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .frame(width: 100, alignment: .leading)
                
                // Weather icon
                Image(systemName: weatherIcon)
                    .font(.title2)
                    .foregroundColor(.blue)
                    .frame(width: 50)
                
                Spacer()
                
                // Precipitation probability & Snow
                VStack(alignment: .trailing, spacing: 2) {
                    if day.precipitationProbability > 0 {
                        HStack(spacing: 4) {
                            Image(systemName: "drop.fill")
                                .font(.caption)
                                .foregroundColor(.blue)
                            Text("\(day.precipitationProbability)%")
                                .font(.caption)
                                .monospacedDigit()
                                .foregroundColor(.secondary)
                        }
                    }

                    if let snow = day.snowAccumulation, snow > 0.1 {
                        HStack(spacing: 4) {
                            Image(systemName: "snowflake")
                                .font(.caption)
                                .foregroundColor(.cyan)
                            Text("\(String(format: "%.1f", snow))mm")
                                .font(.caption2)
                                .monospacedDigit()
                                .foregroundColor(.cyan)
                        }
                    }
                }
                .frame(width: 60)
                
                // Temperature range with proportional Apple Weather-style pill bar
                HStack(spacing: 8) {
                    Text("\(Int(day.temperatureMin))°")
                        .font(.subheadline)
                        .monospacedDigit()
                        .foregroundColor(.secondary)
                        .frame(width: 34, alignment: .trailing)
                    
                    GeometryReader { geometry in
                        let span = max(1.0, weekMax - weekMin)
                        let startFraction = min(1.0, max(0.0, (day.temperatureMin - weekMin) / span))
                        let endFraction = min(1.0, max(startFraction, (day.temperatureMax - weekMin) / span))
                        let barWidth = max(6.0, CGFloat(endFraction - startFraction) * geometry.size.width)
                        let barOffset = CGFloat(startFraction) * geometry.size.width

                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(Color(.systemGray5).opacity(0.6))
                                .frame(height: 6)
                            
                            Capsule()
                                .fill(temperatureGradient)
                                .frame(width: barWidth, height: 6)
                                .offset(x: barOffset)
                        }
                    }
                    .frame(width: 64, height: 6)
                    
                    Text("\(Int(day.temperatureMax))°")
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .monospacedDigit()
                        .frame(width: 34, alignment: .trailing)
                }
                .frame(width: 148)
            }
            .padding()
        }
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(.ultraThinMaterial)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(
                            LinearGradient(
                                colors: [.white.opacity(0.24), .white.opacity(0.05)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                )
        )
        .padding(.horizontal)
    }
    
    private var dayName: String {
        if dayIndex == 0 {
            return "Today"
        } else if dayIndex == 1 {
            return "Tomorrow"
        }
        
        let calendar = Calendar.current
        guard let date = calendar.date(byAdding: .day, value: dayIndex, to: Date()) else {
            return ""
        }
        
        let formatter = DateFormatter()
        formatter.dateFormat = "EEEE"
        return formatter.string(from: date)
    }
    
    private var dateString: String {
        let calendar = Calendar.current
        guard let date = calendar.date(byAdding: .day, value: dayIndex, to: Date()) else {
            return ""
        }
        
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d"
        return formatter.string(from: date)
    }
    
    private var weatherIcon: String {
        if day.precipitationProbability > 60 {
            return "cloud.rain.fill"
        } else if day.precipitationProbability > 30 {
            return "cloud.drizzle.fill"
        } else {
            return "sun.max.fill"
        }
    }
    
    private var temperatureGradient: LinearGradient {
        LinearGradient(
            colors: [.blue, .orange],
            startPoint: .leading,
            endPoint: .trailing
        )
    }
}

#Preview {
    NavigationView {
        DailyForecastView()
            .environmentObject(WeatherViewModel())
            .environmentObject(SubscriptionViewModel())
    }
}
