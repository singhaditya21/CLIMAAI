//
//  TravelRiskView.swift
//  ClimaAI
//
//  Travel risk analysis screen
//

import SwiftUI

struct TravelRiskView: View {
    @EnvironmentObject var aiInsightsViewModel: AIInsightsViewModel
    @EnvironmentObject var weatherViewModel: WeatherViewModel
    @State private var destination = ""
    
    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Destination input
                VStack(alignment: .leading, spacing: 12) {
                    Text("Travel Destination")
                        .font(.headline)
                    
                    HStack {
                        Image(systemName: "location.circle.fill")
                            .foregroundColor(.blue)
                        TextField("Enter destination", text: $destination)
                        
                        Button {
                            Task {
                                await fetchTravelRisk()
                            }
                        } label: {
                            Image(systemName: "arrow.right.circle.fill")
                                .font(.title2)
                                .foregroundColor(.blue)
                        }
                        .disabled(destination.isEmpty)
                    }
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                }
                .padding(.horizontal)
                
                // Travel risk analysis
                if let travelRisk = aiInsightsViewModel.travelRisk {
                    VStack(spacing: 20) {
                        // Risk score gauge
                        ZStack {
                            Circle()
                                .stroke(Color(.systemGray5), lineWidth: 20)
                                .frame(width: 200)
                            
                            Circle()
                                .trim(from: 0, to: CGFloat(travelRisk.riskScore) / 100)
                                .stroke(riskColor(for: travelRisk.riskScore), lineWidth: 20)
                                .frame(width: 200)
                                .rotationEffect(.degrees(-90))
                            
                            VStack(spacing: 8) {
                                Text("\(travelRisk.riskScore)")
                                    .font(.system(size: 60, weight: .bold))
                                    .foregroundColor(riskColor(for: travelRisk.riskScore))
                                
                                Text(travelRisk.overallRiskLevel.rawValue)
                                    .font(.headline)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding()
                        
                        // Summary
                        Text(travelRisk.summary)
                            .font(.body)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                        
                        // Risk factors
                        if !travelRisk.riskFactors.isEmpty {
                            VStack(alignment: .leading, spacing: 16) {
                                Text("Risk Factors")
                                    .font(.headline)
                                
                                ForEach(travelRisk.riskFactors, id: \.factor) { factor in
                                    RiskFactorCard(factor: factor)
                                }
                            }
                            .padding()
                            .background(
                                RoundedRectangle(cornerRadius: 16)
                                    .fill(.ultraThinMaterial)
                            )
                            .padding(.horizontal)
                        }
                        
                        // Recommendations
                        if !travelRisk.recommendations.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Safety Recommendations")
                                    .font(.headline)
                                
                                ForEach(travelRisk.recommendations, id: \.self) { recommendation in
                                    HStack(alignment: .top, spacing: 12) {
                                        Image(systemName: "checkmark.shield.fill")
                                            .foregroundColor(.blue)
                                        Text(recommendation)
                                            .font(.subheadline)
                                    }
                                }
                            }
                            .padding()
                            .background(
                                RoundedRectangle(cornerRadius: 16)
                                    .fill(Color.blue.opacity(0.1))
                            )
                            .padding(.horizontal)
                        }
                        
                        // Travel timing
                        VStack(spacing: 16) {
                            if let bestTime = travelRisk.bestDepartureTime {
                                TimingCard(
                                    icon: "checkmark.circle.fill",
                                    title: "Best Time to Travel",
                                    time: bestTime,
                                    color: .green
                                )
                            }
                            
                            if let worstTime = travelRisk.worstConditionsExpected {
                                TimingCard(
                                    icon: "exclamationmark.triangle.fill",
                                    title: "Worst Conditions",
                                    time: worstTime,
                                    color: .orange
                                )
                            }
                        }
                        .padding(.horizontal)
                    }
                } else if aiInsightsViewModel.isLoading {
                    VStack(spacing: 16) {
                        ProgressView()
                            .scaleEffect(1.5)
                        Text("Analyzing travel conditions...")
                            .foregroundColor(.secondary)
                    }
                    .frame(maxWidth: .infinity, minHeight: 300)
                } else {
                    VStack(spacing: 16) {
                        Image(systemName: "car.fill")
                            .font(.system(size: 60))
                            .foregroundColor(.blue)
                        
                        Text("Enter your destination to get AI-powered travel risk analysis")
                            .font(.body)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                    .frame(maxWidth: .infinity, minHeight: 300)
                }
            }
            .padding(.vertical)
        }
        .navigationTitle("Travel Risk Analysis")
        .navigationBarTitleDisplayMode(.large)
    }
    
    private func fetchTravelRisk() async {
        guard let location = weatherViewModel.currentLocation else { return }
        
        await aiInsightsViewModel.fetchTravelRisk(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            destination: destination
        )
    }
    
    private func riskColor(for score: Int) -> Color {
        switch score {
        case 0...25: return .green
        case 26...50: return .yellow
        case 51...75: return .orange
        default: return .red
        }
    }
}

struct RiskFactorCard: View {
    let factor: RiskFactor
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: severityIcon)
                .font(.title3)
                .foregroundColor(severityColor)
                .frame(width: 30)
            
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(factor.factor)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                    
                    Spacer()
                    
                    Text(factor.severity.rawValue)
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(severityColor)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(severityColor.opacity(0.2))
                        .cornerRadius(8)
                }
                
                Text(factor.description)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(.systemGray6))
        )
    }
    
    private var severityIcon: String {
        switch factor.severity {
        case .LOW: return "info.circle.fill"
        case .MODERATE: return "exclamationmark.circle.fill"
        case .HIGH: return "exclamationmark.triangle.fill"
        case .VERY_HIGH: return "xmark.octagon.fill"
        }
    }
    
    private var severityColor: Color {
        switch factor.severity {
        case .LOW: return .green
        case .MODERATE: return .yellow
        case .HIGH: return .orange
        case .VERY_HIGH: return .red
        }
    }
}

struct TimingCard: View {
    let icon: String
    let title: String
    let time: String
    let color: Color
    
    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(color)
                .frame(width: 40)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(time)
                    .font(.subheadline)
                    .fontWeight(.semibold)
            }
            
            Spacer()
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(.ultraThinMaterial)
        )
    }
}

#Preview {
    NavigationView {
        TravelRiskView()
            .environmentObject(AIInsightsViewModel())
            .environmentObject(WeatherViewModel())
    }
}
