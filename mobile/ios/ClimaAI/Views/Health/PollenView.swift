//
//  PollenView.swift
//  ClimaAI
//
//  Pollen and allergy information view
//

import SwiftUI

struct PollenView: View {
    @EnvironmentObject var weatherViewModel: WeatherViewModel
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Overall Risk Card
                overallRiskCard
                
                // Pollen Types
                pollenTypesSection
                
                // Health Tips
                healthTipsSection
            }
            .padding()
        }
        .navigationTitle("Pollen & Allergies")
        .navigationBarTitleDisplayMode(.large)
    }
    
    // MARK: - Overall Risk Card
    
    private var overallRiskCard: some View {
        VStack(spacing: 16) {
            HStack {
                Image(systemName: "leaf.fill")
                    .font(.title)
                    .foregroundColor(riskColor(for: overallRiskLevel))
                
                VStack(alignment: .leading) {
                    Text("Allergy Risk")
                        .font(.headline)
                    Text(overallRiskLevel)
                        .font(.largeTitle)
                        .fontWeight(.bold)
                        .foregroundColor(riskColor(for: overallRiskLevel))
                }
                
                Spacer()
            }
            
            Text(riskDescription(for: overallRiskLevel))
                .font(.subheadline)
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(.systemBackground))
                .shadow(color: .black.opacity(0.1), radius: 8, y: 2)
        )
    }
    
    // MARK: - Pollen Types Section
    
    private var pollenTypesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Pollen Levels")
                .font(.headline)
            
            VStack(spacing: 12) {
                pollenTypeRow(
                    icon: "leaf.circle.fill",
                    name: "Grass",
                    level: grassLevel,
                    value: grassPollen
                )
                
                Divider()
                
                pollenTypeRow(
                    icon: "tree.circle.fill",
                    name: "Tree",
                    level: treeLevel,
                    value: treePollen
                )
                
                Divider()
                
                pollenTypeRow(
                    icon: "wind.circle.fill",
                    name: "Weed",
                    level: weedLevel,
                    value: weedPollen
                )
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(.systemGray6))
            )
        }
    }
    
    private func pollenTypeRow(icon: String, name: String, level: String, value: Double) -> some View {
        HStack {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(riskColor(for: level))
            
            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(.subheadline)
                Text("\(Int(value)) grains/m³")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Text(level)
                .font(.subheadline)
                .fontWeight(.semibold)
                .padding(.horizontal, 12)
                .padding(.vertical, 4)
                .background(
                    Capsule()
                        .fill(riskColor(for: level).opacity(0.2))
                )
                .foregroundColor(riskColor(for: level))
        }
    }
    
    // MARK: - Health Tips Section
    
    private var healthTipsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Health Tips")
                .font(.headline)
            
            VStack(alignment: .leading, spacing: 8) {
                tipRow(icon: "clock", text: "Peak pollen hours: 5-10 AM")
                tipRow(icon: "eye", text: "Wear sunglasses to protect eyes")
                tipRow(icon: "house", text: "Keep windows closed during high pollen")
                tipRow(icon: "shower", text: "Shower after outdoor activities")
                tipRow(icon: "pills", text: "Take antihistamines before symptoms start")
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.blue.opacity(0.1))
            )
        }
    }
    
    private func tipRow(icon: String, text: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(.blue)
                .frame(width: 24)
            
            Text(text)
                .font(.subheadline)
        }
    }
    
    // MARK: - Helpers
    
    private func riskColor(for level: String) -> Color {
        switch level.lowercased() {
        case "low":
            return .green
        case "moderate":
            return .yellow
        case "high":
            return .orange
        case "very high", "extreme":
            return .red
        default:
            return .gray
        }
    }
    
    private func riskDescription(for level: String) -> String {
        switch level.lowercased() {
        case "low":
            return "Good conditions for allergy sufferers. Minimal precautions needed."
        case "moderate":
            return "Those with allergies may experience mild symptoms. Consider preventive measures."
        case "high":
            return "Allergy sufferers should limit outdoor time and take medication."
        case "very high", "extreme":
            return "Stay indoors if possible. Take antihistamines and avoid outdoor exercise."
        default:
            return "Pollen data unavailable for this location."
        }
    }
    
    // MARK: - Mock Data (replace with actual data)
    
    private var overallRiskLevel: String {
        // TODO: Get from weatherViewModel.pollen
        "Moderate"
    }
    
    private var grassLevel: String { "Low" }
    private var treeLevel: String { "Moderate" }
    private var weedLevel: String { "Low" }
    
    private var grassPollen: Double { 15 }
    private var treePollen: Double { 45 }
    private var weedPollen: Double { 8 }
}

#Preview {
    NavigationView {
        PollenView()
            .environmentObject(WeatherViewModel())
    }
}
