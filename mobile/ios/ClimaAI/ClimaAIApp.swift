//
//  ClimaAIApp.swift
//  ClimaAI
//
//  App entry point
//

import SwiftUI
import UserNotifications

@main
struct ClimaAIApp: App {
    @StateObject private var authViewModel = AuthViewModel()
    @StateObject private var notificationService = NotificationService.shared
    
    init() {
        // Register notification delegate
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(authViewModel)
                .environmentObject(notificationService)
                .task {
                    // Request notification permission on first launch
                    _ = await notificationService.requestAuthorization()
                }
        }
    }
}
