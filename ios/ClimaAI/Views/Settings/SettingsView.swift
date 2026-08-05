//
//  SettingsView.swift
//  ClimaAI
//
//  App settings and preferences
//

import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @EnvironmentObject var subscriptionViewModel: SubscriptionViewModel
    @Environment(\.dismiss) var dismiss
    @State private var showingLogoutAlert = false
    
    var body: some View {
        NavigationView {
            List {
                // Profile Section
                Section {
                    if let user = authViewModel.currentUser {
                        HStack {
                            Image(systemName: "person.circle.fill")
                                .font(.largeTitle)
                                .foregroundColor(.blue)
                            
                            VStack(alignment: .leading, spacing: 4) {
                                Text(user.fullName ?? "User")
                                    .font(.headline)
                                Text(user.email)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(.vertical, 8)
                    }
                } header: {
                    Text("Account")
                }
                
                // Subscription Section
                Section {
                    HStack {
                        Image(systemName: subscriptionViewModel.isPremium ? "crown.fill" : "crown")
                            .foregroundColor(subscriptionViewModel.isPremium ? .yellow : .secondary)
                        
                        VStack(alignment: .leading, spacing: 4) {
                            Text(subscriptionViewModel.isPremium ? "Premium" : "Free")
                                .font(.subheadline)
                                .fontWeight(.semibold)
                            
                            if subscriptionViewModel.isOnTrial {
                                Text("Free trial active")
                                    .font(.caption)
                                    .foregroundColor(.green)
                            } else if !subscriptionViewModel.isPremium {
                                Text("Upgrade for AI insights")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                        
                        Spacer()
                        
                        if !subscriptionViewModel.isPremium {
                            Button("Upgrade") {
                                subscriptionViewModel.showPaywall = true
                            }
                            .buttonStyle(.borderedProminent)
                        }
                    }
                    
                    if subscriptionViewModel.isPremium {
                        NavigationLink {
                            SubscriptionManagementView()
                        } label: {
                            Label("Manage Subscription", systemImage: "gearshape")
                        }
                    }
                } header: {
                    Text("Subscription")
                }
                
                // Preferences Section
                Section {
                    NavigationLink {
                        PreferencesView()
                    } label: {
                        Label("Preferences", systemImage: "slider.horizontal.3")
                    }
                    
                    NavigationLink {
                        NotificationSettingsView()
                    } label: {
                        Label("Notifications", systemImage: "bell")
                    }
                } header: {
                    Text("Settings")
                }
                
                // About Section
                Section {
                    Link(destination: URL(string: "https://singhaditya21.github.io/CLIMAAI/terms.html")!) {
                        Label("Terms of Service", systemImage: "doc.text")
                    }
                    
                    Link(destination: URL(string: "https://singhaditya21.github.io/CLIMAAI/privacy.html")!) {
                        Label("Privacy Policy", systemImage: "hand.raised")
                    }
                    
                    HStack {
                        Label("Version", systemImage: "info.circle")
                        Spacer()
                        Text("1.0.0")
                            .foregroundColor(.secondary)
                    }
                } header: {
                    Text("About")
                }
                
                // Sign Out
                Section {
                    Button(role: .destructive) {
                        showingLogoutAlert = true
                    } label: {
                        Label("Sign Out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
            .alert("Sign Out", isPresented: $showingLogoutAlert) {
                Button("Cancel", role: .cancel) { }
                Button("Sign Out", role: .destructive) {
                    authViewModel.logout()
                    dismiss()
                }
            } message: {
                Text("Are you sure you want to sign out?")
            }
            .sheet(isPresented: $subscriptionViewModel.showPaywall) {
                PaywallView()
            }
        }
    }
}

struct PreferencesView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @State private var temperatureUnit = "celsius"
    @State private var windSpeedUnit = "km/h"
    
    var body: some View {
        Form {
            Section("Units") {
                Picker("Temperature", selection: $temperatureUnit) {
                    Text("Celsius (°C)").tag("celsius")
                    Text("Fahrenheit (°F)").tag("fahrenheit")
                }
                
                Picker("Wind Speed", selection: $windSpeedUnit) {
                    Text("km/h").tag("km/h")
                    Text("mph").tag("mph")
                    Text("m/s").tag("m/s")
                }
            }
            
            Section("Display") {
                Toggle("Show Feels Like", isOn: .constant(true))
                Toggle("Show Humidity", isOn: .constant(true))
                Toggle("Show UV Index", isOn: .constant(true))
            }
        }
        .navigationTitle("Preferences")
    }
}

struct NotificationSettingsView: View {
    @EnvironmentObject var notificationService: NotificationService
    
    @State private var notificationsEnabled = true
    @State private var morningBriefing = true
    @State private var rainAlerts = true
    @State private var severeWeather = true
    @State private var uvWarnings = true
    @State private var briefingHour = 7
    @State private var briefingMinute = 0
    
    var body: some View {
        Form {
            // Authorization Status
            Section {
                HStack {
                    Image(systemName: notificationService.isAuthorized ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .foregroundColor(notificationService.isAuthorized ? .green : .red)
                    Text(notificationService.isAuthorized ? "Notifications Enabled" : "Notifications Disabled")
                    Spacer()
                    if !notificationService.isAuthorized {
                        Button("Enable") {
                            Task {
                                _ = await notificationService.requestAuthorization()
                            }
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }
            
            // Morning Briefing
            Section {
                Toggle("Morning Weather Briefing", isOn: $morningBriefing)
                    // Single-parameter onChange: the two-parameter form is
                    // iOS 17+ and the deployment target is iOS 16.
                    .onChange(of: morningBriefing) { newValue in
                        if newValue {
                            notificationService.scheduleMorningBriefing(hour: briefingHour, minute: briefingMinute)
                        } else {
                            notificationService.cancelMorningBriefing()
                        }
                    }
                
                if morningBriefing {
                    HStack {
                        Text("Briefing Time")
                        Spacer()
                        Picker("Hour", selection: $briefingHour) {
                            ForEach(5..<12, id: \.self) { hour in
                                Text("\(hour):00 AM").tag(hour)
                            }
                        }
                        .pickerStyle(.menu)
                        .onChange(of: briefingHour) { newValue in
                            notificationService.scheduleMorningBriefing(hour: newValue, minute: briefingMinute)
                        }
                    }
                }
            } header: {
                Text("Daily Summary")
            } footer: {
                Text("Receive a weather summary each morning to plan your day")
            }
            
            // Weather Alerts
            Section {
                Toggle(isOn: $rainAlerts) {
                    Label("Rain Alerts", systemImage: "cloud.rain.fill")
                }
                
                Toggle(isOn: $severeWeather) {
                    Label("Severe Weather", systemImage: "exclamationmark.triangle.fill")
                }
                
                Toggle(isOn: $uvWarnings) {
                    Label("UV Warnings", systemImage: "sun.max.fill")
                }
            } header: {
                Text("Weather Alerts")
            } footer: {
                Text("Get notified about important weather changes in your area")
            }
            
            // Test Notification
            Section {
                Button {
                    notificationService.sendRainAlert(minutesUntilRain: 15)
                } label: {
                    Label("Send Test Notification", systemImage: "bell.badge")
                }
            } header: {
                Text("Testing")
            }
        }
        .navigationTitle("Notifications")
    }
}

struct SubscriptionManagementView: View {
    @EnvironmentObject var subscriptionViewModel: SubscriptionViewModel
    @State private var showingCancelAlert = false
    
    var body: some View {
        Form {
            Section {
                // Status/plan/dates live on the subscription row inside the
                // status payload, not at its top level.
                if let subscription = subscriptionViewModel.subscriptionStatus?.subscription {
                    HStack {
                        Text("Status")
                        Spacer()
                        Text(subscription.status.replacingOccurrences(of: "_", with: " ").capitalized)
                            .foregroundColor(.green)
                    }

                    // A trial row carries trial_end_date; a paid row
                    // subscription_end_date.
                    if let endsAt = subscription.subscriptionEndDate ?? subscription.trialEndDate {
                        HStack {
                            Text(subscription.status == "trial" ? "Trial ends" : "Renews")
                            Spacer()
                            Text(formatDate(endsAt))
                                .foregroundColor(.secondary)
                        }
                    }

                    HStack {
                        Text("Plan")
                        Spacer()
                        Text(subscription.plan.capitalized)
                            .foregroundColor(.secondary)
                    }
                } else {
                    // The status fetch failed or returned no subscription row —
                    // say so rather than inventing an "Active" placeholder.
                    Text("Subscription details unavailable")
                        .foregroundColor(.secondary)
                }
            } header: {
                Text("Current Subscription")
            }
            
            Section {
                Button("Restore Purchases") {
                    Task {
                        await subscriptionViewModel.restorePurchases()
                    }
                }
                
                Button("Cancel Subscription", role: .destructive) {
                    showingCancelAlert = true
                }
            }
        }
        .navigationTitle("Manage Subscription")
        .alert("Cancel Subscription", isPresented: $showingCancelAlert) {
            Button("Keep Subscription", role: .cancel) { }
            Button("Cancel", role: .destructive) {
                Task {
                    await subscriptionViewModel.cancelSubscription()
                }
            }
        } message: {
            Text("You'll lose access to premium features at the end of your billing period.")
        }
    }
    
    private func formatDate(_ date: Date) -> String {
        date.formatted(date: .abbreviated, time: .omitted)
    }
}

#Preview {
    SettingsView()
        .environmentObject(AuthViewModel())
        .environmentObject(SubscriptionViewModel())
}
