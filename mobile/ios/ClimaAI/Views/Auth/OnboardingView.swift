//
//  OnboardingView.swift
//  ClimaAI
//
//  App onboarding carousel
//

import SwiftUI

struct OnboardingView: View {
    @State private var currentPage = 0
    @State private var showingLogin = false
    @EnvironmentObject var authViewModel: AuthViewModel
    
    let pages: [OnboardingPage] = [
        OnboardingPage(
            icon: "cloud.sun.rain.fill",
            title: "AI-Powered Weather",
            description: "Get intelligent weather insights powered by advanced AI technology",
            gradient: [.blue, .cyan]
        ),
        OnboardingPage(
            icon: "sparkles",
            title: "Smart Recommendations",
            description: "Personalized outfit, activity, and health suggestions based on real-time conditions",
            gradient: [.purple, .pink]
        ),
        OnboardingPage(
            icon: "bell.badge.fill",
            title: "Weather Alerts",
            description: "Stay safe with severe weather notifications and travel risk analysis",
            gradient: [.orange, .red]
        ),
        OnboardingPage(
            icon: "location.fill",
            title: "Your Location",
            description: "Allow location access for hyper-accurate weather forecasts",
            gradient: [.green, .mint]
        )
    ]
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: pages[currentPage].gradient,
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            .opacity(0.1)
            
            VStack(spacing: 0) {
                // Pages
                TabView(selection: $currentPage) {
                    ForEach(0..<pages.count, id: \.self) { index in
                        OnboardingPageView(page: pages[index])
                            .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                
                // Page indicators
                HStack(spacing: 8) {
                    ForEach(0..<pages.count, id: \.self) { index in
                        Circle()
                            .fill(index == currentPage ? Color.primary : Color.secondary.opacity(0.3))
                            .frame(width: 8, height: 8)
                            .animation(.easeInOut, value: currentPage)
                    }
                }
                .padding(.bottom, 24)
                
                // Action buttons
                VStack(spacing: 12) {
                    if currentPage == pages.count - 1 {
                        // Last page - request location
                        Button {
                            requestLocationPermission()
                        } label: {
                            HStack {
                                Image(systemName: "location.fill")
                                Text("Enable Location")
                                    .fontWeight(.semibold)
                            }
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(
                                LinearGradient(
                                    colors: pages[currentPage].gradient,
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .foregroundColor(.white)
                            .cornerRadius(12)
                        }
                        
                        Button {
                            skipOnboarding()
                        } label: {
                            Text("Skip for now")
                                .foregroundColor(.secondary)
                        }
                    } else {
                        // Other pages - next/skip
                        Button {
                            withAnimation {
                                currentPage += 1
                            }
                        } label: {
                            Text("Continue")
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(
                                    LinearGradient(
                                        colors: pages[currentPage].gradient,
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                                .foregroundColor(.white)
                                .cornerRadius(12)
                        }
                        
                        Button {
                            skipOnboarding()
                        } label: {
                            Text("Skip")
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 32)
            }
        }
        .sheet(isPresented: $showingLogin) {
            LoginView()
        }
    }
    
    private func requestLocationPermission() {
        LocationManager.shared.requestPermission()
        // Wait a bit then proceed
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            completeOnboarding()
        }
    }
    
    private func skipOnboarding() {
        completeOnboarding()
    }
    
    private func completeOnboarding() {
        UserDefaults.standard.set(true, forKey: "hasCompletedOnboarding")
        // Show login if not authenticated
        if !authViewModel.isAuthenticated {
            showingLogin = true
        }
    }
}

struct OnboardingPageView: View {
    let page: OnboardingPage
    
    var body: some View {
        VStack(spacing: 32) {
            Spacer()
            
            // Icon
            Image(systemName: page.icon)
                .font(.system(size: 100))
                .foregroundStyle(
                    .linearGradient(
                        colors: page.gradient,
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .shadow(color: page.gradient[0].opacity(0.3), radius: 20, x: 0, y: 10)
            
            // Content
            VStack(spacing: 16) {
                Text(page.title)
                    .font(.title)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)
                
                Text(page.description)
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }
            
            Spacer()
            Spacer()
        }
        .padding()
    }
}

struct OnboardingPage {
    let icon: String
    let title: String
    let description: String
    let gradient: [Color]
}

#Preview {
    OnboardingView()
        .environmentObject(AuthViewModel())
}
