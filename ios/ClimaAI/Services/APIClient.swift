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
    
    // MARK: - Generic REST surface
    //
    // AuthViewModel and AIInsightsViewModel were written against a generic
    // client (get/post/put/delete plus token helpers) that this class never
    // exposed — every call site failed to compile with "value of type
    // 'APIClient' has no member". These are thin wrappers over the same
    // createRequest/performRequest plumbing the specific endpoints below use, so
    // there is one code path for auth, error handling and 401 token clearing.

    /// True when an access token is held, without exposing the token itself.
    var isAuthenticated: Bool {
        accessToken != nil
    }

    /// Persist a token. Alias for `setAccessToken` in the vocabulary the view
    /// models already use.
    func saveToken(_ token: String) {
        setAccessToken(token)
    }

    /// Drop the stored token, e.g. on sign-out.
    func clearToken() {
        setAccessToken(nil)
    }

    func get<T: Decodable>(_ endpoint: String, requiresAuth: Bool = true) async throws -> T {
        let request = try createRequest(endpoint: endpoint, method: "GET", requiresAuth: requiresAuth)
        return try await performRequest(request)
    }

    func post<T: Decodable>(
        _ endpoint: String,
        body: Encodable? = nil,
        requiresAuth: Bool = true
    ) async throws -> T {
        let data = try body.map { try JSONEncoder().encode(AnyEncodable($0)) }
        let request = try createRequest(
            endpoint: endpoint, method: "POST", body: data, requiresAuth: requiresAuth
        )
        return try await performRequest(request)
    }

    func put<T: Decodable>(
        _ endpoint: String,
        body: Encodable? = nil,
        requiresAuth: Bool = true
    ) async throws -> T {
        let data = try body.map { try JSONEncoder().encode(AnyEncodable($0)) }
        let request = try createRequest(
            endpoint: endpoint, method: "PUT", body: data, requiresAuth: requiresAuth
        )
        return try await performRequest(request)
    }

    func delete<T: Decodable>(_ endpoint: String, requiresAuth: Bool = true) async throws -> T {
        let request = try createRequest(endpoint: endpoint, method: "DELETE", requiresAuth: requiresAuth)
        return try await performRequest(request)
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
    
    // MARK: - Alerts Endpoints
    
    func getAlerts(latitude: Double, longitude: Double, locationName: String = "your location") async throws -> AlertsResponse {
        let endpoint = "/alerts?latitude=\(latitude)&longitude=\(longitude)&location_name=\(locationName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? locationName)"
        let request = try createRequest(endpoint: endpoint, requiresAuth: true)
        return try await performRequest(request)
    }
    
    func getAlertHistory() async throws -> AlertHistoryResponse {
        let request = try createRequest(endpoint: "/alerts/history", requiresAuth: true)
        return try await performRequest(request)
    }
    
    func dismissAlert(alertId: Int) async throws -> MessageResponse {
        let request = try createRequest(endpoint: "/alerts/\(alertId)/dismiss", method: "POST", requiresAuth: true)
        return try await performRequest(request)
    }
    
    // MARK: - Health Endpoints (Pollen, Flu Risk, Migraine, Activities)
    
    func getPollenForecast(latitude: Double, longitude: Double, days: Int = 5) async throws -> PollenForecastResponse {
        let endpoint = "/health/pollen?latitude=\(latitude)&longitude=\(longitude)&days=\(days)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    func getTodaysPollen(latitude: Double, longitude: Double) async throws -> PollenTodayResponse {
        let endpoint = "/health/pollen/today?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    func getFluRisk(latitude: Double, longitude: Double) async throws -> FluRiskResponse {
        let endpoint = "/health/flu-risk?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    func getMigraineRisk(latitude: Double, longitude: Double) async throws -> MigraineRiskResponse {
        let endpoint = "/health/migraine-risk?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    func getAllActivities(latitude: Double, longitude: Double) async throws -> AllActivitiesResponse {
        let endpoint = "/health/activities?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    func getActivityForecast(activity: String, latitude: Double, longitude: Double) async throws -> ActivityDetailResponse {
        let endpoint = "/health/activities/\(activity)?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    // MARK: - Notification Endpoints
    
    func registerDevice(token: String, platform: String = "ios", deviceInfo: [String: Any]? = nil) async throws -> DeviceRegisterResponse {
        var body: [String: Any] = ["token": token, "platform": platform]
        if let info = deviceInfo { body["device_info"] = info }
        let bodyData = try JSONSerialization.data(withJSONObject: body)
        let request = try createRequest(endpoint: "/notifications/register-device", method: "POST", body: bodyData, requiresAuth: true)
        return try await performRequest(request)
    }
    
    func unregisterDevice(token: String) async throws -> MessageResponse {
        let body = ["token": token]
        let bodyData = try encoder.encode(body)
        let request = try createRequest(endpoint: "/notifications/unregister-device", method: "POST", body: bodyData, requiresAuth: true)
        return try await performRequest(request)
    }
    
    func getNotificationPreferences() async throws -> NotificationPreferencesWrapper {
        let request = try createRequest(endpoint: "/notifications/preferences", requiresAuth: true)
        return try await performRequest(request)
    }
    
    func updateNotificationPreferences(weatherAlerts: Bool? = nil, dailySummary: Bool? = nil, severeWeather: Bool? = nil) async throws -> NotificationPreferencesUpdateResponse {
        var body: [String: Bool] = [:]
        if let wa = weatherAlerts { body["weather_alerts"] = wa }
        if let ds = dailySummary { body["daily_summary"] = ds }
        if let sw = severeWeather { body["severe_weather"] = sw }
        let bodyData = try JSONSerialization.data(withJSONObject: body)
        let request = try createRequest(endpoint: "/notifications/preferences", method: "PUT", body: bodyData, requiresAuth: true)
        return try await performRequest(request)
    }
    
    func sendTestNotification(title: String = "Test", message: String = "Test notification") async throws -> TestNotificationResponse {
        let body = ["title": title, "message": message]
        let bodyData = try encoder.encode(body)
        let request = try createRequest(endpoint: "/notifications/test", method: "POST", body: bodyData, requiresAuth: true)
        return try await performRequest(request)
    }
    
    // MARK: - Personalization Endpoints
    
    func trackEvent(eventType: String, eventData: [String: Any], weatherContext: [String: Any]? = nil) async throws -> MessageResponse {
        var body: [String: Any] = ["event_type": eventType, "event_data": eventData]
        if let ctx = weatherContext { body["weather_context"] = ctx }
        let bodyData = try JSONSerialization.data(withJSONObject: body)
        let request = try createRequest(endpoint: "/personalization/track", method: "POST", body: bodyData)
        return try await performRequest(request)
    }
    
    func getPersonalizationProfile(userId: String = "demo_user") async throws -> UserPreferenceProfileResponse {
        let endpoint = "/personalization/profile?user_id=\(userId)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    func getPersonalizedRecommendations(temperature: Double, humidity: Int, uvIndex: Double, precipProbability: Int) async throws -> PersonalizedContentResponse {
        let endpoint = "/personalization/recommendations?temperature=\(temperature)&humidity=\(humidity)&uv_index=\(uvIndex)&precipitation_probability=\(precipProbability)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    func shouldNotify(notificationType: String, userId: String = "demo_user") async throws -> ShouldNotifyResponse {
        let endpoint = "/personalization/should-notify?notification_type=\(notificationType)&user_id=\(userId)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    // MARK: - Precipitation Nowcast
    
    func getPrecipitationNowcast(latitude: Double, longitude: Double) async throws -> PrecipitationNowcastResponse {
        let endpoint = "/api/v1/weather/nowcast?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    // MARK: - NWS Weather Alerts
    
    func getWeatherAlertsNWS(latitude: Double, longitude: Double) async throws -> NWSAlertsResponse {
        let endpoint = "/api/weather/alerts?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    func getStateWeatherAlerts(state: String) async throws -> NWSStateAlertsResponse {
        let endpoint = "/api/weather/alerts/\(state)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    // MARK: - Multi-Source Weather
    
    func getMultiSourceWeather(latitude: Double, longitude: Double, sources: String? = nil) async throws -> MultiSourceWeatherResponse {
        var endpoint = "/api/weather/multi-source?latitude=\(latitude)&longitude=\(longitude)"
        if let s = sources { endpoint += "&sources=\(s)" }
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    func getUVIndex(latitude: Double, longitude: Double) async throws -> UVIndexData {
        let endpoint = "/api/weather/uv?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    func getMarineWeather(latitude: Double, longitude: Double) async throws -> MarineWeatherData {
        let endpoint = "/api/weather/marine?latitude=\(latitude)&longitude=\(longitude)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    func getHistoricalWeather(latitude: Double, longitude: Double, startDate: String, endDate: String) async throws -> HistoricalWeatherData {
        let endpoint = "/api/weather/historical?latitude=\(latitude)&longitude=\(longitude)&start_date=\(startDate)&end_date=\(endDate)"
        let request = try createRequest(endpoint: endpoint)
        return try await performRequest(request)
    }
    
    // MARK: - Travel Risk (Premium)
    
    func getTravelRisk(latitude: Double, longitude: Double, destination: String = "your destination") async throws -> TravelRiskAnalysis {
        let endpoint = "/api/travel-risk?latitude=\(latitude)&longitude=\(longitude)&destination=\(destination.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? destination)"
        let request = try createRequest(endpoint: endpoint, requiresAuth: true)
        return try await performRequest(request)
    }
}

/// Type-erasing box so the generic verbs above can take `Encodable` bodies.
/// `Encodable` alone cannot be passed to `JSONEncoder.encode`, which requires a
/// concrete conforming type.
private struct AnyEncodable: Encodable {
    private let encodeTo: (Encoder) throws -> Void

    init(_ wrapped: Encodable) {
        self.encodeTo = wrapped.encode
    }

    func encode(to encoder: Encoder) throws {
        try encodeTo(encoder)
    }
}
