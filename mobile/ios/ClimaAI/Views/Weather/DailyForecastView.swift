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
        ScrollView {
            VStack(spacing: 16) {
                // Daily forecast list
                ForEach(Array(weatherViewModel.dailyForecast.prefix(displayedDays).enumerated()), id: \.offset) { index, day in
                    DailyForecastRow(day: day, dayIndex: index)
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
                
                // Precipitation probability
                if day.precipitationProbability > 0 {
                    HStack(spacing: 4) {
                        Image(systemName: "drop.fill")
                            .font(.caption)
                            .foregroundColor(.blue)
                        Text("\(day.precipitationProbability)%")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .frame(width: 60)
                }
                
                // Temperature range
                HStack(spacing: 8) {
                    Text("\(Int(day.temperatureMin))°")
                        .foregroundColor(.secondary)
                    
                    // Temperature bar
                    GeometryReader { geometry in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 2)
                                .fill(Color(.systemGray5))
                                .frame(height: 4)
                            
                            RoundedRectangle(cornerRadius: 2)
                                .fill(temperatureGradient)
                                .frame(width: geometry.size.width * 0.7, height: 4)
                        }
                    }
                    .frame(width: 60, height: 4)
                    
                    Text("\(Int(day.temperatureMax))°")
                        .fontWeight(.semibold)
                }
                .frame(width: 140)
            }
            .padding()
        }
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(.systemGray6))
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
