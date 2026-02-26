//
//  AirQualityView.swift
//  ClimaAI
//
//  Air quality details
//

import SwiftUI

struct AirQualityView: View {
    @EnvironmentObject var weatherViewModel: WeatherViewModel
    
    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                if let airQuality = weatherViewModel.airQuality {
                    // AQI Gauge
                    VStack(spacing: 16) {
                        ZStack {
                            // Background circles
                            ForEach(0..<5, id: \.self) { index in
                                Circle()
                                    .stroke(aqiColor(for: (index + 1) * 50).opacity(0.3), lineWidth: 20)
                                    .frame(width: CGFloat(200 - index * 30))
                            }
                            
                            // Main circle
                            Circle()
                                .fill(aqiColor(for: airQuality.aqi))
                                .frame(width: 150)
                                .shadow(color: aqiColor(for: airQuality.aqi).opacity(0.5), radius: 20)
                            
                            VStack(spacing: 8) {
                                Text("\(airQuality.aqi)")
                                    .font(.system(size: 60, weight: .bold))
                                    .foregroundColor(.white)
                                
                                Text(airQuality.category)
                                    .font(.headline)
                                    .foregroundColor(.white)
                            }
                        }
                        .padding(.vertical, 40)
                        
                        Text(airQuality.healthRecommendation)
                            .font(.body)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                    .padding()
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(.ultraThinMaterial)
                    )
                    .padding(.horizontal)
                    
                    // Pollutant breakdown
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Pollutant Levels")
                            .font(.headline)
                        
                        PollutantRow(name: "PM2.5", value: airQuality.pm25, unit: "μg/m³", level: pollutantLevel(airQuality.pm25, thresholds: [12, 35, 55]))
                        PollutantRow(name: "PM10", value: airQuality.pm10, unit: "μg/m³", level: pollutantLevel(airQuality.pm10, thresholds: [54, 154, 254]))
                        PollutantRow(name: "O₃", value: airQuality.ozone ?? 0, unit: "μg/m³", level: pollutantLevel(airQuality.ozone ?? 0, thresholds: [100, 160, 200]))
                        PollutantRow(name: "NO₂", value: airQuality.nitrogenDioxide ?? 0, unit: "μg/m³", level: pollutantLevel(airQuality.nitrogenDioxide ?? 0, thresholds: [50, 100, 200]))
                    }
                    .padding()
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(.ultraThinMaterial)
                    )
                    .padding(.horizontal)
                    
                    // AQI scale reference
                    VStack(alignment: .leading, spacing: 12) {
                        Text("AQI Scale")
                            .font(.headline)
                        
                        AQIScaleRow(range: "0-50", category: "Good", color: .green)
                        AQIScaleRow(range: "51-100", category: "Moderate", color: .yellow)
                        AQIScaleRow(range: "101-150", category: "Unhealthy (Sensitive)", color: .orange)
                        AQIScaleRow(range: "151-200", category: "Unhealthy", color: .red)
                        AQIScaleRow(range: "200+", category: "Very Unhealthy", color: .purple)
                    }
                    .padding()
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(.ultraThinMaterial)
                    )
                    .padding(.horizontal)
                }
            }
            .padding(.vertical)
        }
        .navigationTitle("Air Quality")
        .navigationBarTitleDisplayMode(.large)
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
    
    private func pollutantLevel(_ value: Double, thresholds: [Double]) -> Color {
        if value < thresholds[0] {
            return .green
        } else if value < thresholds[1] {
            return .yellow
        } else if value < thresholds[2] {
            return .orange
        } else {
            return .red
        }
    }
}

struct PollutantRow: View {
    let name: String
    let value: Double
    let unit: String
    let level: Color
    
    var body: some View {
        HStack {
            Text(name)
                .font(.subheadline)
                .fontWeight(.medium)
                .frame(width: 60, alignment: .leading)
            
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color(.systemGray5))
                    
                    RoundedRectangle(cornerRadius: 4)
                        .fill(level)
                        .frame(width: min(geometry.size.width * 0.7, geometry.size.width))
                }
            }
            .frame(height: 8)
            
            Text("\(Int(value)) \(unit)")
                .font(.caption)
                .foregroundColor(.secondary)
                .frame(width: 90, alignment: .trailing)
        }
    }
}

struct AQIScaleRow: View {
    let range: String
    let category: String
    let color: Color
    
    var body: some View {
        HStack {
            Circle()
                .fill(color)
                .frame(width: 12, height: 12)
            
            Text(range)
                .font(.caption)
                .foregroundColor(.secondary)
                .frame(width: 60, alignment: .leading)
            
            Text(category)
                .font(.caption)
            
            Spacer()
        }
    }
}

#Preview {
    NavigationView {
        AirQualityView()
            .environmentObject(WeatherViewModel())
    }
}
