import Foundation

class APIClient {
    static let shared = APIClient()
    
    private let baseURL: String
    private var accessToken: String?
    
    private let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return decoder
    }()
    
    private let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.keyEncodingStrategy = .convertToSnakeCase
        return encoder
    }()
    
    private init() {
        #if DEBUG
        self.baseURL = "http://localhost:8000"
        #else
        self.baseURL = "https://api.climaai.com" // Replace with production URL
        #endif
    }
    
    func setAccessToken(_ token: String?) {
        self.accessToken = token
        if let token = token {
            UserDefaults.standard.set(token, forKey: "access_token")
        } else {
            UserDefaults.standard.removeObject(forKey: "access_token")
        }
    }
    
    func loadAccessToken() {
        self.accessToken = UserDefaults.standard.string(forKey: "access_token")
    }
    
    private func createRequest(
        endpoint: String,
        method: String = "GET",
        body: Data? = nil,
        requiresAuth: Bool = false
    ) throws -> URLRequest {
        guard let url = URL(string: baseURL + endpoint) else {
            throw URLError(.badURL)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        if requiresAuth, let token = accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        if let body = body {
            request.httpBody = body
        }
        
        return request
    }
    
    private func performRequest<T: Decodable>(
        _ request: URLRequest
    ) async throws -> T {
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        
        if httpResponse.statusCode == 401 {
            // Token expired, clear it
            setAccessToken(nil)
            throw NSError(domain: "APIClient", code: 401, userInfo: [NSLocalizedDescriptionKey: "Authentication required"])
        }
        
        guard 200...299 ~= httpResponse.statusCode else {
            // Try to decode error
            if let error = try? decoder.decode(APIError.self, from: data) {
                throw error
            }
            throw NSError(
                domain: "APIClient",
                code: httpResponse.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "Server error: \(httpResponse.statusCode)"]
            )
        }
        
        return try decoder.decode(T.self, from: data)
    }
    
    // MARK: - Auth Endpoints
    
    func register(email: String, password: String, fullName: String?, deviceToken: String?) async throws -> TokenResponse {
        let registerData = UserRegister(
            email: email,
            password: password,
            fullName: fullName,
            platform: "ios",
            deviceToken: deviceToken
        )
        
        let body = try encoder.encode(registerData)
        let request = try createRequest(endpoint: "/users/register", method: "POST", body: body)
        
        let response: TokenResponse = try await performRequest(request)
        setAccessToken(response.accessToken)
        return response
    }
    
    func login(email: String, password: String) async throws -> TokenResponse {
        let loginData = UserLogin(email: email, password: password)
        let body = try encoder.encode(loginData)
        let request = try createRequest(endpoint: "/users/login", method: "POST", body: body)
        
        let response: TokenResponse = try await performRequest(request)
        setAccessToken(response.accessToken)
        return response
    }
    
    func getCurrentUser() async throws -> User {
        let request = try createRequest(endpoint: "/users/me", requiresAuth: true)
        return try await performRequest(request)
    }
    
    // MARK: - Weather Endpoints
    
    func getWeather(latitude: Double, longitude: Double) async throws -> WeatherResponse {
        let endpoint = "/weather/current?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint, requiresAuth: false)
        return try await performRequest(request)
    }
    
    func getHourlyForecast(latitude: Double, longitude: Double, hours: Int = 24) async throws -> WeatherResponse {
        let endpoint = "/weather/hourly?latitude=\(latitude)&longitude=\(longitude)&hours=\(hours)"
        let request = try createRequest(endpoint: endpoint, requiresAuth: false)
        return try await performRequest(request)
    }
    
    func getDailyForecast(latitude: Double, longitude: Double, days: Int = 7) async throws -> WeatherResponse {
        let endpoint = "/weather/daily?latitude=\(latitude)&longitude=\(longitude)&days=\(days)"
        let request = try createRequest(endpoint: endpoint, requiresAuth: false)
        return try await performRequest(request)
    }
    
    func getAirQuality(latitude: Double, longitude: Double) async throws -> AirQuality {
        let endpoint = "/weather/air-quality?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint, requiresAuth: false)
        
        struct AQResponse: Codable {
            let airQuality: AirQuality
            
            enum CodingKeys: String, CodingKey {
                case airQuality = "air_quality"
            }
        }
        
        let response: AQResponse = try await performRequest(request)
        return response.airQuality
    }
    
    // MARK: - AI Endpoints (Premium)
    
    func getAIInsights(latitude: Double, longitude: Double, locationName: String = "your location") async throws -> AIInsightsResponse {
        let endpoint = "/ai/insights?latitude=\(latitude)&longitude=\(longitude)&location_name=\(locationName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? locationName)"
        let request = try createRequest(endpoint: endpoint, requiresAuth: true)
        return try await performRequest(request)
    }
    
    func getDailySummary(latitude: Double, longitude: Double, locationName: String = "your location") async throws -> DailySummary {
        let endpoint = "/ai/summary?latitude=\(latitude)&longitude=\(longitude)&location_name=\(locationName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? locationName)"
        let request = try createRequest(endpoint: endpoint, requiresAuth: true)
        
        struct Response: Codable {
            let summary: DailySummary
        }
        
        let response: Response = try await performRequest(request)
        return response.summary
    }
    
    func getOutfitRecommendation(latitude: Double, longitude: Double) async throws -> OutfitRecommendation {
        let endpoint = "/ai/outfit?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint, requiresAuth: true)
        return try await performRequest(request)
    }
    
    func getActivityRecommendations(latitude: Double, longitude: Double) async throws -> [ActivityRecommendation] {
        let endpoint = "/ai/activities?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint, requiresAuth: true)
        return try await performRequest(request)
    }
    
    func getHealthInsights(latitude: Double, longitude: Double) async throws -> HealthInsight {
        let endpoint = "/ai/health?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint, requiresAuth: true)
        return try await performRequest(request)
    }
    
    // MARK: - Subscription Endpoints
    
    func getSubscriptionStatus() async throws -> SubscriptionStatus {
        let request = try createRequest(endpoint: "/subscriptions/status", requiresAuth: true)
        return try await performRequest(request)
    }
    
    func startTrial(receiptData: String) async throws -> Subscription {
        struct TrialRequest: Codable {
            let platform: String
            let plan: String
            let receiptData: String
            
            enum CodingKeys: String, CodingKey {
                case platform, plan
                case receiptData = "receipt_data"
            }
        }
        
        let trialData = TrialRequest(platform: "apple", plan: "monthly", receiptData: receiptData)
        let body = try encoder.encode(trialData)
        let request = try createRequest(endpoint: "/subscriptions/trial", method: "POST", body: body, requiresAuth: true)
        return try await performRequest(request)
    }
    
    func activateSubscription(plan: String, receiptData: String) async throws -> Subscription {
        struct ActivateRequest: Codable {
            let platform: String
            let plan: String
            let receiptData: String
            
            enum CodingKeys: String, CodingKey {
                case platform, plan
                case receiptData = "receipt_data"
            }
        }
        
        let activateData = ActivateRequest(platform: "apple", plan: plan, receiptData: receiptData)
        let body = try encoder.encode(activateData)
        let request = try createRequest(endpoint: "/subscriptions/activate", method: "POST", body: body, requiresAuth: true)
        return try await performRequest(request)
    }
    
    func getSubscriptionPlans() async throws -> [SubscriptionPlan] {
        let request = try createRequest(endpoint: "/subscriptions/plans")
        
        struct PlansResponse: Codable {
            let plans: [SubscriptionPlan]
        }
        
        let response: PlansResponse = try await performRequest(request)
        return response.plans
    }
}
