//
//  ContentView.swift
//  ClimaAI
//
//  Root view with tab navigation
//

import SwiftUI

struct ContentView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @StateObject private var weatherViewModel = WeatherViewModel()
    @StateObject private var aiInsightsViewModel = AIInsightsViewModel()
    @StateObject private var subscriptionViewModel = SubscriptionViewModel()
    @State private var selectedTab = 0
    @State private var showingOnboarding = false
    
    var body: some View {
        Group {
            if authViewModel.isAuthenticated {
                TabView(selection: $selectedTab) {
                    // Home Tab
                    HomeView()
                        .tabItem {
                            Label("Home", systemImage: "house.fill")
                        }
                        .tag(0)
                    
                    // AI Insights Tab
                    NavigationView {
                        if subscriptionViewModel.isPremium {
                            AIInsightsView()
                        } else {
                            PremiumRequiredView()
                        }
                    }
                    .tabItem {
                        Label("AI Insights", systemImage: "sparkles")
                    }
                    .tag(1)
                    
                    // Settings Tab
                    SettingsView()
                        .tabItem {
                            Label("Settings", systemImage: "gearshape.fill")
                        }
                        .tag(2)
                }
                .environmentObject(weatherViewModel)
                .environmentObject(aiInsightsViewModel)
                .environmentObject(subscriptionViewModel)
            } else {
                // Not authenticated - show login
                LoginView()
            }
        }
        .onAppear {
            checkOnboardingStatus()
        }
        .sheet(isPresented: $showingOnboarding) {
            OnboardingView()
        }
    }
    
    private func checkOnboardingStatus() {
        let hasCompletedOnboarding = UserDefaults.standard.bool(forKey: "hasCompletedOnboarding")
        if !hasCompletedOnboarding {
            showingOnboarding = true
        }
    }
}

struct PremiumRequiredView: View {
    @EnvironmentObject var subscriptionViewModel: SubscriptionViewModel
    
    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "crown.fill")
                .font(.system(size: 80))
                .foregroundStyle(
                    .linearGradient(
                        colors: [.yellow, .orange],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            
            VStack(spacing: 12) {
                Text("Premium Feature")
                    .font(.title)
                    .fontWeight(.bold)
                
                Text("AI Insights are available to Premium subscribers")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }
            
            Button {
                subscriptionViewModel.showPaywall = true
            } label: {
                Text("Unlock Premium")
                    .fontWeight(.semibold)
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
            .padding(.horizontal, 32)
        }
        .navigationTitle("AI Insights")
        .sheet(isPresented: $subscriptionViewModel.showPaywall) {
            PaywallView()
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(AuthViewModel())
}
