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
            print("Error loading products: \(error)")
            errorMessage = "Failed to load subscription plans"
        }
    }
    
    // MARK: - Subscription Status
    
    /// Fetch subscription status from backend
    func fetchSubscriptionStatus() async {
        isLoading = true

        do {
            // GET /api/subscriptions/status returns SubscriptionStatusResponse
            // (has_active_subscription / is_premium / subscription / features),
            // which is what Models.swift's SubscriptionStatus models.
            let status: SubscriptionStatus = try await apiClient.get("/api/subscriptions/status")
            subscriptionStatus = status
            isPremium = status.isPremium
            // The wire has no top-level trial flag; trial-ness lives on the
            // subscription row's status.
            isOnTrial = status.subscription?.status == "trial"
            isLoading = false
        } catch {
            print("Error fetching subscription status: \(error)")
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
                        // Validate with backend. "apple", not "ios": the wire
                        // enum is SubscriptionPlatform (apple/google/web).
                        let success = await validatePurchase(
                            receipt: receipt,
                            productId: product.id,
                            platform: "apple"
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
            // POST /api/subscriptions/trial returns the created subscription
            // row (SubscriptionResponse), not a status payload; the premium
            // flags and feature set come from a follow-up /status fetch.
            let _: Subscription = try await apiClient.post(
                "/api/subscriptions/trial",
                body: [
                    "platform": "apple",
                    "plan": "monthly",
                    // Required by the SubscriptionCreate schema; a trial has
                    // no store receipt yet.
                    "receipt_data": ""
                ]
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
            // DELETE, not POST: that is the verb the backend registers for
            // /api/subscriptions/cancel, and it answers with the updated
            // subscription row.
            let _: Subscription = try await apiClient.delete("/api/subscriptions/cancel")

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
            print("Error reading receipt: \(error)")
            return nil
        }
    }
    
    /// Validate purchase with backend
    private func validatePurchase(receipt: String, productId: String, platform: String) async -> Bool {
        do {
            // POST /api/subscriptions/validate returns a receipt verdict
            // ({valid, is_active, ...}), not the status object — the caller
            // refreshes premium state from /status afterwards.
            struct ReceiptValidation: Codable {
                let valid: Bool
                let isActive: Bool?
            }

            let response: ReceiptValidation = try await apiClient.post(
                "/api/subscriptions/validate",
                body: [
                    "receipt_data": receipt,
                    "product_id": productId,
                    "platform": platform
                ]
            )

            return response.valid && (response.isActive ?? false)
        } catch {
            print("Validation error: \(error)")
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
        // StoreKit prices are Decimal, which is not CVarArg; bridge through
        // NSDecimalNumber for the %.0f formatting.
        return String(format: "%.0f%%", NSDecimalNumber(decimal: savings).doubleValue)
    }
}

// The Swift SubscriptionStatusResponse that used to live here is gone: it
// described a wire shape (user_id / is_on_trial / expires_at) the backend
// never sends. Models.swift's SubscriptionStatus is the one matching what
// /api/subscriptions/status actually returns
// (has_active_subscription / is_premium / subscription / features).
