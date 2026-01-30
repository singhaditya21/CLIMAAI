//
//  AIInsightsViewModel.swift
//  ClimaAI
//
//  AI Insights ViewModel - MVVM Pattern
//

import Foundation
import SwiftUI
import Combine

@MainActor
class AIInsightsViewModel: ObservableObject {
    // MARK: - Published Properties
    @Published var dailySummary: DailySummary?
    @Published var outfitRecommendation: OutfitRecommendation?
    @Published var activityRecommendations: [ActivityRecommendation] = []
    @Published var healthInsight: HealthInsight?
    @Published var travelRisk: TravelRiskAnalysis?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var isPremiumRequired = false
    
    // MARK: - Private Properties
    private let apiClient: APIClient
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Initialization
    init(apiClient: APIClient = .shared) {
        self.apiClient = apiClient
    }
    
    // MARK: - Fetch AI Insights
    
    /// Fetch complete AI insights for a location
    func fetchCompleteInsights(latitude: Double, longitude: Double, locationName: String = "your location") async {
        isLoading = true
        errorMessage = nil
        isPremiumRequired = false
        
        do {
            let response: AIInsightsResponse = try await apiClient.get(
                "/api/v1/ai/insights",
                queryItems: [
                    URLQueryItem(name: "latitude", value: String(latitude)),
                    URLQueryItem(name: "longitude", value: String(longitude)),
                    URLQueryItem(name: "location_name", value: locationName)
                ]
            )
            
            dailySummary = response.dailySummary
            outfitRecommendation = response.outfit
            activityRecommendations = response.activities
            healthInsight = response.health
            travelRisk = response.travel
            
            isLoading = false
        } catch {
            handleError(error)
        }
    }
    
    /// Fetch daily summary only
    func fetchDailySummary(latitude: Double, longitude: Double, locationName: String = "your location") async {
        isLoading = true
        errorMessage = nil
        isPremiumRequired = false
        
        do {
            let response: DailySummaryResponse = try await apiClient.get(
                "/api/v1/ai/summary",
                queryItems: [
                    URLQueryItem(name: "latitude", value: String(latitude)),
                    URLQueryItem(name: "longitude", value: String(longitude)),
                    URLQueryItem(name: "location_name", value: locationName)
                ]
            )
            
            dailySummary = response.summary
            isLoading = false
        } catch {
            handleError(error)
        }
    }
    
    /// Fetch outfit recommendation only
    func fetchOutfitRecommendation(latitude: Double, longitude: Double) async {
        isLoading = true
        errorMessage = nil
        isPremiumRequired = false
        
        do {
            let outfit: OutfitRecommendation = try await apiClient.get(
                "/api/v1/ai/outfit",
                queryItems: [
                    URLQueryItem(name: "latitude", value: String(latitude)),
                    URLQueryItem(name: "longitude", value: String(longitude))
                ]
            )
            
            outfitRecommendation = outfit
            isLoading = false
        } catch {
            handleError(error)
        }
    }
    
    /// Fetch activity recommendations only
    func fetchActivityRecommendations(latitude: Double, longitude: Double) async {
        isLoading = true
        errorMessage = nil
        isPremiumRequired = false
        
        do {
            let activities: [ActivityRecommendation] = try await apiClient.get(
                "/api/v1/ai/activities",
                queryItems: [
                    URLQueryItem(name: "latitude", value: String(latitude)),
                    URLQueryItem(name: "longitude", value: String(longitude))
                ]
            )
            
            activityRecommendations = activities
            isLoading = false
        } catch {
            handleError(error)
        }
    }
    
    /// Fetch health insights only
    func fetchHealthInsights(latitude: Double, longitude: Double) async {
        isLoading = true
        errorMessage = nil
        isPremiumRequired = false
        
        do {
            let health: HealthInsight = try await apiClient.get(
                "/api/v1/ai/health",
                queryItems: [
                    URLQueryItem(name: "latitude", value: String(latitude)),
                    URLQueryItem(name: "longitude", value: String(longitude))
                ]
            )
            
            healthInsight = health
            isLoading = false
        } catch {
            handleError(error)
        }
    }
    
    /// Fetch travel risk analysis
    func fetchTravelRisk(latitude: Double, longitude: Double, destination: String = "your destination") async {
        isLoading = true
        errorMessage = nil
        isPremiumRequired = false
        
        do {
            let risk: TravelRiskAnalysis = try await apiClient.get(
                "/api/v1/ai/travel-risk",
                queryItems: [
                    URLQueryItem(name: "latitude", value: String(latitude)),
                    URLQueryItem(name: "longitude", value: String(longitude)),
                    URLQueryItem(name: "destination", value: destination)
                ]
            )
            
            travelRisk = risk
            isLoading = false
        } catch {
            handleError(error)
        }
    }
    
    // MARK: - Error Handling
    
    private func handleError(_ error: Error) {
        isLoading = false
        
        // Check if it's a premium subscription error (403)
        if let nsError = error as NSError?, nsError.code == 403 {
            isPremiumRequired = true
            errorMessage = "Premium subscription required for AI insights"
        } else {
            errorMessage = error.localizedDescription
        }
    }
    
    // MARK: - Reset
    
    func reset() {
        dailySummary = nil
        outfitRecommendation = nil
        activityRecommendations = []
        healthInsight = nil
        travelRisk = nil
        errorMessage = nil
        isPremiumRequired = false
    }
}

// MARK: - Supporting Types

struct DailySummaryResponse: Codable {
    let summary: DailySummary
}
