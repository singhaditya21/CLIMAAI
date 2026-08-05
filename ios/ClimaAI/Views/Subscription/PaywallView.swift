//
//  PaywallView.swift
//  ClimaAI
//
//  Premium subscription paywall
//

import SwiftUI
import StoreKit

struct PaywallView: View {
    @EnvironmentObject var subscriptionViewModel: SubscriptionViewModel
    @Environment(\.dismiss) var dismiss
    @State private var selectedProductIndex = 1  // Default to annual plan
    
    let features = [
        PaywallFeature(icon: "sparkles", title: "AI Insights", description: "Daily summaries, outfit & activity recommendations"),
        PaywallFeature(icon: "figure.walk", title: "Travel Risk Analysis", description: "AI-powered safety analysis for your journeys"),
        PaywallFeature(icon: "heart.text.square.fill", title: "Health Insights", description: "UV, air quality, and heat stress advisories"),
        PaywallFeature(icon: "calendar.badge.clock", title: "Extended Forecasts", description: "14-day forecasts instead of 7-day"),
        PaywallFeature(icon: "bell.badge.fill", title: "Weather Alerts", description: "Severe weather notifications"),
        PaywallFeature(icon: "location.fill.viewfinder", title: "Unlimited Locations", description: "Save favorite locations worldwide")
    ]
    
    var body: some View {
        NavigationView {
            ZStack {
                // Background gradient
                LinearGradient(
                    colors: [.blue.opacity(0.1), .purple.opacity(0.1)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                ScrollView {
                    VStack(spacing: 24) {
                        // Header
                        VStack(spacing: 12) {
                            Image(systemName: "crown.fill")
                                .font(.system(size: 60))
                                .foregroundStyle(
                                    .linearGradient(
                                        colors: [.yellow, .orange],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                            
                            Text("Upgrade to Premium")
                                .font(.largeTitle)
                                .fontWeight(.bold)
                            
                            Text("Unlock AI-powered insights & advanced features")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                        }
                        .padding(.top, 20)
                        
                        // Features list
                        VStack(spacing: 16) {
                            ForEach(features, id: \.title) { feature in
                                FeatureRow(feature: feature)
                            }
                        }
                        .padding(.horizontal)
                        
                        // Subscription plans
                        if !subscriptionViewModel.availableProducts.isEmpty {
                            VStack(spacing: 12) {
                                ForEach(Array(subscriptionViewModel.availableProducts.enumerated()), id: \.element.id) { index, product in
                                    SubscriptionPlanCard(
                                        product: product,
                                        isSelected: selectedProductIndex == index,
                                        savings: index == 1 ? subscriptionViewModel.annualSavings() : nil
                                    )
                                    .onTapGesture {
                                        selectedProductIndex = index
                                    }
                                }
                            }
                            .padding(.horizontal)
                        }
                        
                        // Trial CTA
                        VStack(spacing: 8) {
                            if let selectedProduct = subscriptionViewModel.availableProducts[safe: selectedProductIndex] {
                                Button {
                                    Task {
                                        let success = await subscriptionViewModel.purchase(selectedProduct)
                                        if success {
                                            dismiss()
                                        }
                                    }
                                } label: {
                                    HStack {
                                        if subscriptionViewModel.isLoading {
                                            ProgressView()
                                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        } else {
                                            VStack(spacing: 4) {
                                                Text("Start 7-Day Free Trial")
                                                    .fontWeight(.bold)
                                                Text("Then \(subscriptionViewModel.formattedPrice(for: selectedProduct))")
                                                    .font(.caption)
                                            }
                                        }
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding()
                                    .background(
                                        LinearGradient(
                                            colors: [.blue, .purple],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .foregroundColor(.white)
                                    .cornerRadius(16)
                                }
                                .disabled(subscriptionViewModel.isLoading)
                            }
                            
                            Text("Cancel anytime. No commitment.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        .padding(.horizontal)
                        
                        // Error message
                        if let error = subscriptionViewModel.errorMessage {
                            Text(error)
                                .font(.caption)
                                .foregroundColor(.red)
                                .padding()
                        }
                        
                        // Restore purchases
                        Button {
                            Task {
                                await subscriptionViewModel.restorePurchases()
                            }
                        } label: {
                            Text("Restore Purchases")
                                .font(.subheadline)
                                .foregroundColor(.blue)
                        }
                        .padding(.bottom, 8)
                        
                        // Terms and Privacy
                        HStack(spacing: 16) {
                            Link("Terms", destination: URL(string: "https://singhaditya21.github.io/CLIMAAI/terms.html")!)
                            Text("•")
                            Link("Privacy", destination: URL(string: "https://singhaditya21.github.io/CLIMAAI/privacy.html")!)
                        }
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.bottom, 20)
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Close") {
                        dismiss()
                    }
                }
            }
        }
    }
}

struct FeatureRow: View {
    let feature: PaywallFeature
    
    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: feature.icon)
                .font(.title2)
                .foregroundColor(.blue)
                .frame(width: 40)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(feature.title)
                    .font(.headline)
                Text(feature.description)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(.green)
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(.ultraThinMaterial)
        )
    }
}

struct SubscriptionPlanCard: View {
    let product: Product
    let isSelected: Bool
    let savings: String?
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(product.displayName)
                        .font(.headline)
                    Text(product.description)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                if let savings = savings {
                    Text("Save \(savings)")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.green)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.green.opacity(0.2))
                        .cornerRadius(8)
                }
            }
            
            HStack(alignment: .firstTextBaseline) {
                Text(product.displayPrice)
                    .font(.title)
                    .fontWeight(.bold)
                
                if product.id.contains("annual") {
                    Text("/ year")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                } else {
                    Text("/ month")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(isSelected ? .blue : .secondary)
                    .font(.title2)
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(isSelected ? Color.blue.opacity(0.1) : Color(.systemGray6))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(isSelected ? Color.blue : Color.clear, lineWidth: 2)
                )
        )
    }
}

struct PaywallFeature {
    let icon: String
    let title: String
    let description: String
}

// Safe array subscript
extension Array {
    subscript(safe index: Int) -> Element? {
        return indices.contains(index) ? self[index] : nil
    }
}

#Preview {
    PaywallView()
        .environmentObject(SubscriptionViewModel())
}
