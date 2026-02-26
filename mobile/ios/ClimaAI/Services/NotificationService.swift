//
//  NotificationService.swift
//  ClimaAI
//
//  Smart notification service for weather alerts and briefings
//

import Foundation
import UserNotifications
import UIKit

@MainActor
class NotificationService: ObservableObject {
    static let shared = NotificationService()
    
    @Published var isAuthorized = false
    @Published var pendingNotifications: [UNNotificationRequest] = []
    
    private let notificationCenter = UNUserNotificationCenter.current()
    
    // Notification identifiers
    private enum NotificationID {
        static let morningBriefing = "morning-weather-briefing"
        static let rainAlert = "rain-alert"
        static let severeWeather = "severe-weather"
        static let uvWarning = "uv-warning"
    }
    
    private init() {
        Task {
            await checkAuthorizationStatus()
        }
    }
    
    // MARK: - Authorization
    
    func requestAuthorization() async -> Bool {
        do {
            let granted = try await notificationCenter.requestAuthorization(
                options: [.alert, .sound, .badge, .provisional]
            )
            isAuthorized = granted
            
            if granted {
                await registerForRemoteNotifications()
            }
            
            return granted
        } catch {
            print("Notification authorization error: \(error)")
            return false
        }
    }
    
    private func checkAuthorizationStatus() async {
        let settings = await notificationCenter.notificationSettings()
        isAuthorized = settings.authorizationStatus == .authorized
    }
    
    private func registerForRemoteNotifications() async {
        await MainActor.run {
            UIApplication.shared.registerForRemoteNotifications()
        }
    }
    
    // MARK: - Morning Briefing
    
    /// Schedule a daily morning weather briefing notification
    func scheduleMorningBriefing(hour: Int = 7, minute: Int = 0) {
        // Remove existing morning briefing
        notificationCenter.removePendingNotificationRequests(
            withIdentifiers: [NotificationID.morningBriefing]
        )
        
        var dateComponents = DateComponents()
        dateComponents.hour = hour
        dateComponents.minute = minute
        
        let trigger = UNCalendarNotificationTrigger(
            dateMatching: dateComponents,
            repeats: true
        )
        
        let content = UNMutableNotificationContent()
        content.title = "☀️ Good Morning!"
        content.body = "Check today's weather and AI insights"
        content.sound = .default
        content.categoryIdentifier = "MORNING_BRIEFING"
        
        // Add action buttons
        let viewAction = UNNotificationAction(
            identifier: "VIEW_WEATHER",
            title: "View Weather",
            options: .foreground
        )
        let dismissAction = UNNotificationAction(
            identifier: "DISMISS",
            title: "Dismiss",
            options: .destructive
        )
        
        let category = UNNotificationCategory(
            identifier: "MORNING_BRIEFING",
            actions: [viewAction, dismissAction],
            intentIdentifiers: [],
            options: []
        )
        
        notificationCenter.setNotificationCategories([category])
        
        let request = UNNotificationRequest(
            identifier: NotificationID.morningBriefing,
            content: content,
            trigger: trigger
        )
        
        notificationCenter.add(request) { error in
            if let error = error {
                print("Error scheduling morning briefing: \(error)")
            } else {
                print("Morning briefing scheduled for \(hour):\(String(format: "%02d", minute))")
            }
        }
    }
    
    /// Cancel morning briefing notifications
    func cancelMorningBriefing() {
        notificationCenter.removePendingNotificationRequests(
            withIdentifiers: [NotificationID.morningBriefing]
        )
    }
    
    // MARK: - Rain Alert
    
    /// Send an immediate rain alert notification
    func sendRainAlert(
        minutesUntilRain: Int,
        intensity: String = "light"
    ) {
        let content = UNMutableNotificationContent()
        content.title = "🌧️ Rain Alert"
        
        if minutesUntilRain <= 5 {
            content.body = "Rain starting soon! Grab an umbrella."
        } else if minutesUntilRain <= 15 {
            content.body = "Rain expected in about \(minutesUntilRain) minutes"
        } else {
            content.body = "Rain expected within the hour"
        }
        
        content.sound = .default
        content.interruptionLevel = .timeSensitive
        
        // Immediate trigger (1 second delay)
        let trigger = UNTimeIntervalNotificationTrigger(
            timeInterval: 1,
            repeats: false
        )
        
        let request = UNNotificationRequest(
            identifier: "\(NotificationID.rainAlert)-\(Date().timeIntervalSince1970)",
            content: content,
            trigger: trigger
        )
        
        notificationCenter.add(request)
    }
    
    // MARK: - Severe Weather Alert
    
    /// Send a severe weather warning notification
    func sendSevereWeatherAlert(
        title: String,
        description: String,
        severity: SeverityLevel = .moderate
    ) {
        let content = UNMutableNotificationContent()
        
        switch severity {
        case .minor:
            content.title = "⚠️ Weather Advisory"
        case .moderate:
            content.title = "🟠 Weather Warning"
        case .severe:
            content.title = "🔴 Severe Weather Alert"
        case .extreme:
            content.title = "🚨 EXTREME WEATHER"
        }
        
        content.subtitle = title
        content.body = description
        content.sound = severity == .extreme ? .defaultCritical : .default
        content.interruptionLevel = severity == .extreme ? .critical : .timeSensitive
        
        let trigger = UNTimeIntervalNotificationTrigger(
            timeInterval: 1,
            repeats: false
        )
        
        let request = UNNotificationRequest(
            identifier: "\(NotificationID.severeWeather)-\(Date().timeIntervalSince1970)",
            content: content,
            trigger: trigger
        )
        
        notificationCenter.add(request)
    }
    
    // MARK: - UV Warning
    
    /// Send a UV index warning notification
    func sendUVWarning(uvIndex: Int) {
        guard uvIndex >= 6 else { return }  // Only warn for high+ UV
        
        let content = UNMutableNotificationContent()
        content.title = "☀️ High UV Alert"
        
        if uvIndex >= 11 {
            content.body = "Extreme UV levels (\(uvIndex))! Avoid sun exposure."
        } else if uvIndex >= 8 {
            content.body = "Very high UV (\(uvIndex)). Use SPF 30+ sunscreen."
        } else {
            content.body = "High UV (\(uvIndex)). Remember to protect your skin."
        }
        
        content.sound = .default
        
        let trigger = UNTimeIntervalNotificationTrigger(
            timeInterval: 1,
            repeats: false
        )
        
        let request = UNNotificationRequest(
            identifier: "\(NotificationID.uvWarning)-\(Date().timeIntervalSince1970)",
            content: content,
            trigger: trigger
        )
        
        notificationCenter.add(request)
    }
    
    // MARK: - Precipitation Check
    
    /// Check hourly forecast for upcoming precipitation and send alert if needed
    func checkForPrecipitation(hourlyForecast: [HourlyWeather]) {
        // Find first hour with precipitation > 50%
        for (index, hour) in hourlyForecast.prefix(3).enumerated() {
            if hour.precipitationProbability >= 50 {
                let minutesUntil = index * 60
                
                // Determine intensity based on mm
                let intensity: String
                if hour.precipitation >= 5 {
                    intensity = "heavy"
                } else if hour.precipitation >= 1 {
                    intensity = "moderate"
                } else {
                    intensity = "light"
                }
                
                sendRainAlert(minutesUntilRain: minutesUntil, intensity: intensity)
                break  // Only send one alert
            }
        }
    }
    
    // MARK: - Pending Notifications
    
    func fetchPendingNotifications() async {
        let requests = await notificationCenter.pendingNotificationRequests()
        pendingNotifications = requests
    }
    
    func clearAllNotifications() {
        notificationCenter.removeAllPendingNotificationRequests()
        notificationCenter.removeAllDeliveredNotifications()
    }
}

// MARK: - Severity Level

enum SeverityLevel {
    case minor
    case moderate
    case severe
    case extreme
}

// MARK: - Notification Delegate

class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationDelegate()
    
    // Handle notification when app is in foreground
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // Show notification even when app is active
        completionHandler([.banner, .sound, .badge])
    }
    
    // Handle notification tap
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let actionIdentifier = response.actionIdentifier
        
        switch actionIdentifier {
        case "VIEW_WEATHER":
            // Navigate to home screen
            NotificationCenter.default.post(
                name: Notification.Name("OpenHomeScreen"),
                object: nil
            )
        default:
            break
        }
        
        completionHandler()
    }
}
