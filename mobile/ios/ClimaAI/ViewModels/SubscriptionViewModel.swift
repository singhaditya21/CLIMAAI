//
//  SubscriptionViewModel.swift
//  ClimaAI
//
//  Subscription ViewModel - MVVM Pattern
//

import Foundation
import SwiftUI
import StoreKit
import Combine

@MainActor
class SubscriptionViewModel: ObservableObject {
    // MARK: - Published Properties
    @Published var subscriptionStatus: SubscriptionStatus?
    @Published var availableProducts: [Product] = []
    @Published var isPremium = false
    @Published var isOnTrial = false
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var showPaywall = false
    
    // MARK: - Private Properties
    private let apiClient: APIClient
    private let subscriptionManager: SubscriptionManager
    private var cancellables = Set<AnyCancellable>()
    
    // Product IDs
    private let monthlyProductId = "com.climaai.premium.monthly"
    private let annualProductId = "com.climaai.premium.annual"
    
    // MARK: - Initialization
    init(apiClient: APIClient = .shared, subscriptionManager: SubscriptionManager = .shared) {
        self.apiClient = apiClient
        self.subscriptionManager = subscriptionManager
        
        Task {
            await loadProducts()
            await fetchSubscriptionStatus()
        }
    }
    
    // MARK: - StoreKit Product Loading
    
    /// Load available products from App Store
    func loadProducts() async {
        do {
            let products = try await Product.products(for: [monthlyProductId, annualProductId])
            availableProducts = products.sorted { $0.price < $1.price }
        } catch {
            print("Error loading products: \\(error)")
            errorMessage = "Failed to load subscription plans"
        }
    }
    
    // MARK: - Subscription Status
    
    /// Fetch subscription status from backend
    func fetchSubscriptionStatus() async {
        isLoading = true
        
        do {
            let status: SubscriptionStatusResponse = try await apiClient.get("/api/v1/subscriptions/status")
            subscriptionStatus = status
            isPremium = status.isPremium
            isOnTrial = status.isOnTrial
            isLoading = false
        } catch {
            print("Error fetching subscription status: \\(error)")
            isLoading = false
        }
    }
    
    // MARK: - Purchase Flow
    
    /// Purchase a subscription
    func purchase(_ product: Product) async -> Bool {
        isLoading = true
        errorMessage = nil
        
        do {
            // Initiate purchase through StoreKit
            let result = try await product.purchase()
            
            switch result {
            case .success(let verification):
                // Verify transaction
                switch verification {
                case .verified(let transaction):
                    // Get transaction receipt
                    if let receipt = await getReceiptData() {
                        // Validate with backend
                        let success = await validatePurchase(
                            receipt: receipt,
                            productId: product.id,
                            platform: "ios"
                        )
                        
                        if success {
                            await transaction.finish()
                            await fetchSubscriptionStatus()
                            isLoading = false
                            return true
                        }
                    }
                    
                case .unverified:
                    errorMessage = "Purchase verification failed"
                }
                
            case .pending:
                errorMessage = "Purchase is pending"
                
            case .userCancelled:
                break
                
            @unknown default:
                break
            }
            
            isLoading = false
            return false
            
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
            return false
        }
    }
    
    /// Start free trial
    func startTrial() async -> Bool {
        isLoading = true
        errorMessage = nil
        
        do {
            let response: SubscriptionStatusResponse = try await apiClient.post(
                "/api/v1/subscriptions/trial/start",
                body: [String: String]()
            )
            
            subscriptionStatus = response
            isPremium = response.isPremium
            isOnTrial = response.isOnTrial
            isLoading = false
            return true
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
            return false
        }
    }
    
    /// Restore purchases
    func restorePurchases() async -> Bool {
        isLoading = true
        errorMessage = nil
        
        do {
            // Sync with App Store
            try await AppStore.sync()
            
            // Refresh subscription status
            await subscriptionManager.refreshSubscriptionStatus()
            await fetchSubscriptionStatus()
            
            isLoading = false
            return true
        } catch {
            errorMessage = "No purchases found to restore"
            isLoading = false
            return false
        }
    }
    
    /// Cancel subscription
    func cancelSubscription() async -> Bool {
        isLoading = true
        errorMessage = nil
        
        do {
            let _: MessageResponse = try await apiClient.post(
                "/api/v1/subscriptions/cancel",
                body: [String: String]()
            )
            
            await fetchSubscriptionStatus()
            isLoading = false
            return true
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
            return false
        }
    }
    
    // MARK: - Helper Methods
    
    /// Get receipt data from App Store
    private func getReceiptData() async -> String? {
        guard let appStoreReceiptURL = Bundle.main.appStoreReceiptURL,
              FileManager.default.fileExists(atPath: appStoreReceiptURL.path) else {
            return nil
        }
        
        do {
            let receiptData = try Data(contentsOf: appStoreReceiptURL)
            return receiptData.base64EncodedString()
        } catch {
            print("Error reading receipt: \\(error)")
            return nil
        }
    }
    
    /// Validate purchase with backend
    private func validatePurchase(receipt: String, productId: String, platform: String) async -> Bool {
        do {
            let response: SubscriptionStatusResponse = try await apiClient.post(
                "/api/v1/subscriptions/validate",
                body: [
                    "receipt_data": receipt,
                    "product_id": productId,
                    "platform": platform
                ]
            )
            
            subscriptionStatus = response
            isPremium = response.isPremium
            return true
        } catch {
            print("Validation error: \\(error)")
            return false
        }
    }
    
    /// Check if feature requires premium
    func requiresPremium() -> Bool {
        return !isPremium
    }
    
    /// Show paywall if premium required
    func showPaywallIfNeeded() {
        if !isPremium {
            showPaywall = true
        }
    }
    
    /// Get formatted price for product
    func formattedPrice(for product: Product) -> String {
        return product.displayPrice
    }
    
    /// Get savings percentage for annual plan
    func annualSavings() -> String? {
        guard availableProducts.count >= 2,
              let monthly = availableProducts.first(where: { $0.id == monthlyProductId }),
              let annual = availableProducts.first(where: { $0.id == annualProductId }) else {
            return nil
        }
        
        let monthlyAnnual = monthly.price * 12
        let savings = ((monthlyAnnual - annual.price) / monthlyAnnual) * 100
        return String(format: "%.0f%%", savings)
    }
}

// MARK: - Supporting Types

struct SubscriptionStatusResponse: Codable {
    let userId: Int
    let isPremium: Bool
    let isOnTrial: Bool
    let plan: String?
    let status: String?
    let expiresAt: String?
    
    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case isPremium = "is_premium"
        case isOnTrial = "is_on_trial"
        case plan, status
        case expiresAt = "expires_at"
    }
}

typealias SubscriptionStatus = SubscriptionStatusResponse
