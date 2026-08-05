//
//  PrecipitationBanner.swift
//  ClimaAI
//
//  "Rain in X minutes" alert banner
//

import SwiftUI

struct PrecipitationBanner: View {
    let nowcast: PrecipitationNowcast
    var onTap: (() -> Void)?
    
    var body: some View {
        if shouldShow {
            Button(action: { onTap?() }) {
                HStack(spacing: 12) {
                    // Icon
                    precipitationIcon
                        .font(.title2)
                        .foregroundStyle(iconGradient)
                    
                    // Text
                    VStack(alignment: .leading, spacing: 2) {
                        Text(nowcast.summary)
                            .font(.subheadline)
                            .fontWeight(.medium)
                        
                        if let minutes = nowcast.precipitationInMinutes, minutes > 0 {
                            Text("Tap for details")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    Spacer()
                    
                    // Probability badge
                    if nowcast.probability > 0 {
                        Text("\(nowcast.probability)%")
                            .font(.caption)
                            .fontWeight(.bold)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(probabilityColor.opacity(0.2))
                            .foregroundColor(probabilityColor)
                            .clipShape(Capsule())
                    }
                }
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(.ultraThinMaterial)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(borderGradient, lineWidth: 1)
                        )
                )
            }
            .buttonStyle(.plain)
            .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
    
    // MARK: - Computed Properties
    
    private var shouldShow: Bool {
        nowcast.hasPrecipitation && (nowcast.precipitationInMinutes ?? 0) <= 120
    }
    
    private var precipitationIcon: Image {
        switch nowcast.precipitationType {
        case "rain":
            return Image(systemName: "cloud.rain.fill")
        case "snow":
            return Image(systemName: "cloud.snow.fill")
        case "mixed":
            return Image(systemName: "cloud.sleet.fill")
        default:
            return Image(systemName: "cloud.fill")
        }
    }
    
    private var iconGradient: LinearGradient {
        switch nowcast.precipitationType {
        case "rain":
            return LinearGradient(colors: [.blue, .cyan], startPoint: .top, endPoint: .bottom)
        case "snow":
            return LinearGradient(colors: [.white, .cyan], startPoint: .top, endPoint: .bottom)
        default:
            return LinearGradient(colors: [.gray, .blue], startPoint: .top, endPoint: .bottom)
        }
    }
    
    private var borderGradient: LinearGradient {
        switch nowcast.intensity {
        case "heavy":
            return LinearGradient(colors: [.red.opacity(0.5), .orange.opacity(0.3)], startPoint: .leading, endPoint: .trailing)
        case "moderate":
            return LinearGradient(colors: [.yellow.opacity(0.5), .orange.opacity(0.3)], startPoint: .leading, endPoint: .trailing)
        default:
            return LinearGradient(colors: [.blue.opacity(0.3), .cyan.opacity(0.2)], startPoint: .leading, endPoint: .trailing)
        }
    }
    
    private var probabilityColor: Color {
        if nowcast.probability >= 80 {
            return .red
        } else if nowcast.probability >= 50 {
            return .orange
        } else {
            return .blue
        }
    }
}

// MARK: - Precipitation Nowcast Model

struct PrecipitationNowcast: Codable {
    let hasPrecipitation: Bool
    let precipitationInMinutes: Int?
    let precipitationEndsInMinutes: Int?
    let intensity: String
    let precipitationType: String
    let probability: Int
    let summary: String
    
    enum CodingKeys: String, CodingKey {
        case hasPrecipitation
        case precipitationInMinutes
        case precipitationEndsInMinutes
        case intensity
        case precipitationType
        case probability
        case summary
    }
}

// MARK: - Preview

#Preview("Rain Coming") {
    VStack {
        PrecipitationBanner(nowcast: PrecipitationNowcast(
            hasPrecipitation: true,
            precipitationInMinutes: 30,
            precipitationEndsInMinutes: nil,
            intensity: "moderate",
            precipitationType: "rain",
            probability: 75,
            summary: "Rain expected in 30 minutes"
        ))
        .padding()
        
        PrecipitationBanner(nowcast: PrecipitationNowcast(
            hasPrecipitation: true,
            precipitationInMinutes: nil,
            precipitationEndsInMinutes: 45,
            intensity: "light",
            precipitationType: "rain",
            probability: 90,
            summary: "Rain stopping in about 45 minutes"
        ))
        .padding()
        
        PrecipitationBanner(nowcast: PrecipitationNowcast(
            hasPrecipitation: true,
            precipitationInMinutes: 15,
            precipitationEndsInMinutes: nil,
            intensity: "heavy",
            precipitationType: "snow",
            probability: 85,
            summary: "Heavy snow expected in 15 minutes"
        ))
        .padding()
    }
}
