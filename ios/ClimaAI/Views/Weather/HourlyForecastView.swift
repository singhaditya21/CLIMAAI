//
//  HourlyForecastView.swift
//  ClimaAI
//
//  48-hour forecast with charts
//

import SwiftUI
import Charts

struct HourlyForecastView: View {
    @EnvironmentObject var weatherViewModel: WeatherViewModel
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Temperature chart
                if !weatherViewModel.hourlyForecast.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Temperature")
                            .font(.headline)
                            .padding(.horizontal)
                        
                        Chart {
                            ForEach(Array(weatherViewModel.hourlyForecast.prefix(24).enumerated()), id: \.offset) { index, hour in
                                LineMark(
                                    x: .value("Hour", index),
                                    y: .value("Temp", hour.temperature)
                                )
                                .foregroundStyle(.blue.gradient)
                                .interpolationMethod(.catmullRom)
                                
                                AreaMark(
                                    x: .value("Hour", index),
                                    y: .value("Temp", hour.temperature)
                                )
                                .foregroundStyle(.blue.opacity(0.1).gradient)
                                .interpolationMethod(.catmullRom)
                            }
                        }
                        .frame(height: 200)
                        .chartXAxis {
                            AxisMarks(values: .stride(by: 3)) { value in
                                if let index = value.as(Int.self) {
                                    AxisValueLabel {
                                        Text(hourLabel(for: index))
                                    }
                                }
                            }
                        }
                        .chartYAxis {
                            AxisMarks { value in
                                AxisValueLabel {
                                    if let temp = value.as(Double.self) {
                                        Text("\(Int(temp))°")
                                    }
                                }
                            }
                        }
                        .padding()
                    }
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(.ultraThinMaterial)
                    )
                    .padding(.horizontal)
                    
                    // Hourly list
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Next 48 Hours")
                            .font(.headline)
                            .padding(.horizontal)
                        
                        ForEach(Array(weatherViewModel.hourlyForecast.prefix(48).enumerated()), id: \.offset) { index, hour in
                            HourlyRow(hour: hour, hourIndex: index)
                        }
                    }
                    .padding(.vertical)
                }
            }
            .padding(.vertical)
        }
        .navigationTitle("Hourly Forecast")
        .navigationBarTitleDisplayMode(.large)
    }
    
    private func hourLabel(for index: Int) -> String {
        let calendar = Calendar.current
        guard let date = calendar.date(byAdding: .hour, value: index, to: Date()) else {
            return "\(index)h"
        }
        
        let formatter = DateFormatter()
        formatter.dateFormat = "ha"
        return formatter.string(from: date).lowercased()
    }
}

struct HourlyRow: View {
    let hour: HourlyWeather
    let hourIndex: Int
    
    var body: some View {
        HStack {
            // Time
            Text(timeString)
                .font(.subheadline)
                .frame(width: 60, alignment: .leading)
            
            // Weather icon
            Image(systemName: weatherIcon)
                .font(.title3)
                .foregroundColor(.blue)
                .frame(width: 40)
            
            // Temperature
            Text("\(Int(hour.temperature))°")
                .font(.title3)
                .fontWeight(.semibold)
                .frame(width: 50, alignment: .trailing)
            
            Spacer()
            
            // Precipitation
            if hour.precipitation > 0 {
                HStack(spacing: 4) {
                    Image(systemName: "drop.fill")
                        .font(.caption)
                        .foregroundColor(.blue)
                    Text("\(Int(hour.precipitation))mm")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            
            // Wind
            HStack(spacing: 4) {
                Image(systemName: "wind")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text("\(Int(hour.windSpeed))km/h")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(.systemGray6))
        )
        .padding(.horizontal)
    }
    
    private var timeString: String {
        if hourIndex == 0 {
            return "Now"
        }
        
        let calendar = Calendar.current
        guard let date = calendar.date(byAdding: .hour, value: hourIndex, to: Date()) else {
            return "+\(hourIndex)h"
        }
        
        let formatter = DateFormatter()
        formatter.dateFormat = "ha"
        return formatter.string(from: date).lowercased()
    }
    
    private var weatherIcon: String {
        if hour.precipitation > 5 {
            return "cloud.rain.fill"
        } else if hour.precipitation > 0 {
            return "cloud.drizzle.fill"
        } else if hour.cloudCover > 70 {
            return "cloud.fill"
        } else if hour.cloudCover > 30 {
            return "cloud.sun.fill"
        } else {
            return "sun.max.fill"
        }
    }
}

#Preview {
    NavigationView {
        HourlyForecastView()
            .environmentObject(WeatherViewModel())
    }
}
