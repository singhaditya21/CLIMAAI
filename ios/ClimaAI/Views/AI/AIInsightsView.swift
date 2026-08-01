//
//  AIInsightsView.swift
//  ClimaAI
//
//  AI-powered weather insights
//

import SwiftUI

struct AIInsightsView: View {
    @EnvironmentObject var aiInsightsViewModel: AIInsightsViewModel
    @EnvironmentObject var weatherViewModel: WeatherViewModel
    @State private var selectedTab = 0
    
    let tabs = ["Summary", "Outfit", "Activities", "Health"]
    
    var body: some View {
        VStack(spacing: 0) {
            // Tab selector
            Picker("Insights", selection: $selectedTab) {
                ForEach(0..<tabs.count, id: \.self) { index in
                    Text(tabs[index]).tag(index)
                }
            }
            .pickerStyle(.segmented)
            .padding()
            
            // Content
            ScrollView {
                VStack(spacing: 20) {
                    switch selectedTab {
                    case 0:
                        dailySummaryView
                    case 1:
                        outfitView
                    case 2:
                        activitiesView
                    case 3:
                        healthView
                    default:
                        EmptyView()
                    }
                }
                .padding()
            }
        }
        .navigationTitle("AI Insights")
        .navigationBarTitleDisplayMode(.large)
        .task {
            await loadInsights()
        }
    }
    
    @ViewBuilder
    private var dailySummaryView: some View {
        if let summary = aiInsightsViewModel.dailySummary {
            VStack(alignment: .leading, spacing: 16) {
                // Title
                Text(summary.title)
                    .font(.title2)
                    .fontWeight(.bold)
                
                // Summary
                Text(summary.summary)
                    .font(.body)
                    .foregroundColor(.secondary)
                
                // Highlights
                if !summary.highlights.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Key Points")
                            .font(.headline)
                        
                        ForEach(summary.highlights, id: \.self) { highlight in
                            HStack(alignment: .top, spacing: 8) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.green)
                                Text(highlight)
                                    .font(.subheadline)
                            }
                        }
                    }
                }
                
                // Warnings
                if !summary.warnings.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Warnings")
                            .font(.headline)
                        
                        ForEach(summary.warnings, id: \.self) { warning in
                            HStack(alignment: .top, spacing: 8) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundColor(.orange)
                                Text(warning)
                                    .font(.subheadline)
                            }
                        }
                    }
                    .padding()
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.orange.opacity(0.1))
                    )
                }
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(.ultraThinMaterial)
            )
        } else {
            loadingOrErrorView
        }
    }
    
    @ViewBuilder
    private var outfitView: some View {
        if let outfit = aiInsightsViewModel.outfitRecommendation {
            VStack(alignment: .leading, spacing: 16) {
                Text(outfit.summary)
                    .font(.title3)
                    .fontWeight(.semibold)
                
                Text(outfit.details)
                    .font(.body)
                    .foregroundColor(.secondary)
                
                if !outfit.accessories.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Accessories")
                            .font(.headline)
                        
                        FlowLayout(spacing: 8) {
                            ForEach(outfit.accessories, id: \.self) { accessory in
                                Text(accessory)
                                    .font(.caption)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(Color.blue.opacity(0.2))
                                    .foregroundColor(.blue)
                                    .cornerRadius(16)
                            }
                        }
                    }
                }
                
                Text(outfit.layerRecommendation)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color(.systemGray6))
                    )
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(.ultraThinMaterial)
            )
        } else {
            loadingOrErrorView
        }
    }
    
    @ViewBuilder
    private var activitiesView: some View {
        if !aiInsightsViewModel.activityRecommendations.isEmpty {
            ForEach(aiInsightsViewModel.activityRecommendations, id: \.activity) { activity in
                ActivityCard(activity: activity)
            }
        } else {
            loadingOrErrorView
        }
    }
    
    @ViewBuilder
    private var healthView: some View {
        if let health = aiInsightsViewModel.healthInsight {
            VStack(spacing: 16) {
                // UV Risk
                HealthRiskCard(
                    icon: "sun.max.fill",
                    title: "UV Risk",
                    level: health.uvRisk,
                    advice: health.uvAdvice
                )
                
                // Air Quality Risk
                HealthRiskCard(
                    icon: "aqi.medium",
                    title: "Air Quality",
                    level: health.airQualityRisk,
                    advice: health.airQualityAdvice
                )
                
                // Heat Stress Risk
                HealthRiskCard(
                    icon: "thermometer.sun.fill",
                    title: "Heat Stress",
                    level: health.heatStressRisk,
                    advice: health.heatStressAdvice
                )
                
                // General tips
                if !health.generalHealthTips.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Health Tips")
                            .font(.headline)
                        
                        ForEach(health.generalHealthTips, id: \.self) { tip in
                            HStack(alignment: .top, spacing: 8) {
                                Image(systemName: "info.circle.fill")
                                    .foregroundColor(.blue)
                                Text(tip)
                                    .font(.subheadline)
                            }
                        }
                    }
                    .padding()
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(.ultraThinMaterial)
                    )
                }
            }
        } else {
            loadingOrErrorView
        }
    }
    
    @ViewBuilder
    private var loadingOrErrorView: some View {
        VStack(spacing: 16) {
            if aiInsightsViewModel.isLoading {
                ProgressView()
                Text("Generating AI insights...")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            } else if let error = aiInsightsViewModel.errorMessage {
                Image(systemName: "exclamationmark.triangle")
                    .font(.largeTitle)
                    .foregroundColor(.orange)
                Text(error)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 200)
    }
    
    private func loadInsights() async {
        guard let location = weatherViewModel.currentLocation else { return }
        
        await aiInsightsViewModel.fetchCompleteInsights(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            locationName: weatherViewModel.locationName
        )
    }
}

struct ActivityCard: View {
    let activity: ActivityRecommendation
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(activity.activity)
                    .font(.headline)
                
                Spacer()
                
                // Suitability score
                ZStack {
                    Circle()
                        .stroke(Color(.systemGray5), lineWidth: 4)
                    Circle()
                        .trim(from: 0, to: CGFloat(activity.suitabilityScore) / 100)
                        .stroke(scoreColor, lineWidth: 4)
                        .rotationEffect(.degrees(-90))
                    
                    Text("\(activity.suitabilityScore)")
                        .font(.caption)
                        .fontWeight(.bold)
                }
                .frame(width: 50, height: 50)
            }
            
            Text(activity.reasoning)
                .font(.subheadline)
                .foregroundColor(.secondary)
            
            HStack {
                Image(systemName: "clock")
                    .font(.caption)
                Text(activity.bestTime)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            if !activity.precautions.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Safety Tips")
                        .font(.caption)
                        .fontWeight(.semibold)
                    
                    ForEach(activity.precautions, id: \.self) { precaution in
                        HStack(spacing: 4) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.caption2)
                                .foregroundColor(.blue)
                            Text(precaution)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .padding(.top, 4)
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(.ultraThinMaterial)
        )
    }
    
    private var scoreColor: Color {
        if activity.suitabilityScore >= 75 {
            return .green
        } else if activity.suitabilityScore >= 50 {
            return .yellow
        } else {
            return .red
        }
    }
}

struct HealthRiskCard: View {
    let icon: String
    let title: String
    let level: RiskLevel
    let advice: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(riskColor)
                .frame(width: 40)
            
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(title)
                        .font(.headline)
                    Spacer()
                    Text(level.rawValue)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(riskColor)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(riskColor.opacity(0.2))
                        .cornerRadius(8)
                }
                
                Text(advice)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(.ultraThinMaterial)
        )
    }
    
    private var riskColor: Color {
        switch level {
        case .low: return .green
        case .moderate: return .yellow
        case .high: return .orange
        case .veryHigh: return .red
        }
    }
}

// Flow layout for accessories
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        var totalHeight: CGFloat = 0
        var totalWidth: CGFloat = 0
        var lineWidth: CGFloat = 0
        var lineHeight: CGFloat = 0
        
        for size in sizes {
            if lineWidth + size.width > proposal.width ?? 0 {
                totalWidth = max(totalWidth, lineWidth)
                totalHeight += lineHeight + spacing
                lineWidth = size.width
                lineHeight = size.height
            } else {
                lineWidth += size.width + spacing
                lineHeight = max(lineHeight, size.height)
            }
        }
        
        totalWidth = max(totalWidth, lineWidth)
        totalHeight += lineHeight
        
        return CGSize(width: totalWidth, height: totalHeight)
    }
    
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var lineHeight: CGFloat = 0
        
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            
            if x + size.width > bounds.maxX {
                x = bounds.minX
                y += lineHeight + spacing
                lineHeight = 0
            }
            
            subview.place(at: CGPoint(x: x, y: y), proposal: .unspecified)
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
    }
}

#Preview {
    NavigationView {
        AIInsightsView()
            .environmentObject(AIInsightsViewModel())
            .environmentObject(WeatherViewModel())
    }
}
