import StoreKit

class SubscriptionManager: NSObject, ObservableObject {
    static let shared = SubscriptionManager()
    
    @Published var subscriptionStatus: SubscriptionStatus?
    @Published var isLoading = false
    @Published var error: Error?
    
    // Product IDs - match your App Store Connect configuration
    private let monthlyProductID = "com.climaai.app.monthly"
    private let annualProductID = "com.climaai.app.annual"
    
    private var products: [Product] = []
    private var updateListenerTask: Task<Void, Error>?
    
    override init() {
        super.init()
        updateListenerTask = listenForTransactions()
        
        Task {
            await loadProducts()
        }
    }
    
    deinit {
        updateListenerTask?.cancel()
    }
    
    // MARK: - Load Products
    
    func loadProducts() async {
        do {
            let productIDs = [monthlyProductID, annualProductID]
            products = try await Product.products(for: productIDs)
            print("Loaded \(products.count) products")
        } catch {
            print("Failed to load products: \(error)")
            self.error = error
        }
    }
    
    // MARK: - Purchase
    
    func purchase(_ product: Product) async throws -> Transaction? {
        guard !isLoading else { return nil }
        
        isLoading = true
        defer { isLoading = false }
        
        let result = try await product.purchase()
        
        switch result {
        case .success(let verification):
            let transaction = try checkVerified(verification)
            
            // Send receipt to backend
            if let receiptData = try? await getReceiptData() {
                do {
                    let plan = product.id == monthlyProductID ? "monthly" : "annual"
                    _ = try await APIClient.shared.activateSubscription(
                        plan: plan,
                        receiptData: receiptData
                    )
                } catch {
                    print("Failed to activate subscription on backend: \(error)")
                }
            }
            
            await transaction.finish()
            await refreshSubscriptionStatus()
            
            return transaction
            
        case .userCancelled:
            return nil
            
        case .pending:
            return nil
            
        @unknown default:
            return nil
        }
    }
    
    // MARK: - Restore Purchases
    
    func restorePurchases() async {
        isLoading = true
        defer { isLoading = false }
        
        do {
            try await AppStore.sync()
            await refreshSubscriptionStatus()
        } catch {
            print("Failed to restore purchases: \(error)")
            self.error = error
        }
    }
    
    // MARK: - Check Subscription Status
    
    func checkSubscriptionStatus() async -> Bool {
        var isSubscribed = false
        
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result {
                if transaction.productType == .autoRenewable {
                    isSubscribed = true
                }
            }
        }
        
        return isSubscribed
    }
    
    func refreshSubscriptionStatus() async {
        do {
            subscriptionStatus = try await APIClient.shared.getSubscriptionStatus()
        } catch {
            print("Failed to refresh subscription status: \(error)")
            self.error = error
        }
    }
    
    // MARK: - Get Products
    
    func getMonthlyProduct() -> Product? {
        products.first { $0.id == monthlyProductID }
    }
    
    func getAnnualProduct() -> Product? {
        products.first { $0.id == annualProductID }
    }
    
    // MARK: - Helpers
    
    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified:
            throw NSError(domain: "SubscriptionManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "Transaction verification failed"])
        case .verified(let safe):
            return safe
        }
    }
    
    private func listenForTransactions() -> Task<Void, Error> {
        return Task.detached {
            for await result in Transaction.updates {
                do {
                    let transaction = try self.checkVerified(result)
                    await self.refreshSubscriptionStatus()
                    await transaction.finish()
                } catch {
                    print("Transaction failed verification: \(error)")
                }
            }
        }
    }
    
    private func getReceiptData() async throws -> String {
        // For iOS 15+, use App Store Server API for receipt verification
        // This is a simplified version
        if let appStoreReceiptURL = Bundle.main.appStoreReceiptURL,
           FileManager.default.fileExists(atPath: appStoreReceiptURL.path) {
            let receiptData = try Data(contentsOf: appStoreReceiptURL)
            return receiptData.base64EncodedString()
        }
        throw NSError(domain: "SubscriptionManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "No receipt found"])
    }
}
