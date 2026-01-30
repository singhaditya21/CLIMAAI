//
//  ComplicationViews.swift
//  ClimaAIWatch
//
//  Watch complications for ClockKit
//

import SwiftUI
import WidgetKit

// MARK: - Circular Complication

struct CircularComplicationView: View {
    let temperature: Double
    let weatherCode: Int
    
    var body: some View {
        ZStack {
            AccessoryWidgetBackground()
            
            VStack(spacing: 0) {
                Image(systemName: iconForCode(weatherCode))
                    .font(.caption)
                Text("\(Int(temperature))°")
                    .font(.headline)
                    .fontWeight(.semibold)
            }
        }
    }
    
    private func iconForCode(_ code: Int) -> String {
        switch code {
        case 0: return "sun.max.fill"
        case 1, 2: return "cloud.sun.fill"
        case 3: return "cloud.fill"
        case 45, 48: return "cloud.fog.fill"
        case 51...65: return "cloud.rain.fill"
        case 71...77: return "cloud.snow.fill"
        case 80...82: return "cloud.heavyrain.fill"
        case 95...99: return "cloud.bolt.rain.fill"
        default: return "sun.max.fill"
        }
    }
}

// MARK: - Rectangular Complication

struct RectangularComplicationView: View {
    let temperature: Double
    let feelsLike: Double
    let weatherDescription: String
    let locationName: String
    
    var body: some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(locationName)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                
                Text("\(Int(temperature))°")
                    .font(.title2)
                    .fontWeight(.semibold)
            }
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 2) {
                Text(weatherDescription)
                    .font(.caption2)
                Text("Feels \(Int(feelsLike))°")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
    }
}

// MARK: - Corner Complication

struct CornerComplicationView: View {
    let temperature: Double
    let weatherCode: Int
    
    var body: some View {
        VStack(spacing: 0) {
            Text("\(Int(temperature))°")
                .font(.title3)
                .fontWeight(.bold)
        }
        .widgetLabel {
            Image(systemName: iconForCode(weatherCode))
        }
    }
    
    private func iconForCode(_ code: Int) -> String {
        switch code {
        case 0: return "sun.max.fill"
        case 1, 2: return "cloud.sun.fill"
        case 3: return "cloud.fill"
        default: return "cloud.fill"
        }
    }
}

// MARK: - Inline Complication

struct InlineComplicationView: View {
    let temperature: Double
    let weatherDescription: String
    
    var body: some View {
        Text("\(Int(temperature))° \(weatherDescription)")
    }
}

#Preview("Circular") {
    CircularComplicationView(temperature: 18, weatherCode: 1)
}

#Preview("Rectangular") {
    RectangularComplicationView(
        temperature: 18,
        feelsLike: 16,
        weatherDescription: "Partly Cloudy",
        locationName: "San Francisco"
    )
}
