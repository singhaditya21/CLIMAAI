//
//  WeatherViewModel.swift
//  ClimaAI
//
//  Weather ViewModel - MVVM Pattern
//

import Foundation
import SwiftUI
import Combine
import CoreLocation
import WidgetKit

@MainActor
class WeatherViewModel: ObservableObject {
    // MARK: - Published Properties
    @Published var currentWeather: CurrentWeather?
    @Published var hourlyForecast: [HourlyWeather] = []
    @Published var dailyForecast: [DailyWeather] = []
    @Published var airQuality: AirQuality?
    @Published var precipitationNowcast: PrecipitationNowcast?
    @Published var pollen: PollenResponse?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var currentLocation: CLLocation?
    // "Loading...", matching LocationManager's own placeholder (and what the
    // tests assert): until reverse geocoding answers there is no real name to
    // show, and "Current Location" would present a guess as a fact.
    @Published var locationName: String = "Loading..."
    @Published var favoriteLocations: [FavoriteLocation] = []
    
    // MARK: - New API Published Properties
    @Published var alerts: [AlertData] = []
    @Published var fluRisk: FluRiskResponse?
    @Published var migraineRisk: MigraineRiskResponse?
    @Published var activityForecasts: [ActivityForecastItem] = []
    @Published var notificationPrefs: NotificationPreferences?
    @Published var nwsAlerts: [NWSAlert] = []
    @Published var multiSourceWeather: MultiSourceWeatherResponse?
    @Published var uvIndex: UVIndexData?
    @Published var marineWeather: MarineWeatherData?
    @Published var precipNowcastDetailed: PrecipitationNowcastResponse?
    
    // MARK: - Private Properties
    private let apiClient: APIClient
    private let locationManager: LocationManager
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Initialization
    init(apiClient: APIClient = .shared, locationManager: LocationManager = .shared) {
        self.apiClient = apiClient
        self.locationManager = locationManager
        setupLocationObserver()
    }
    
    // MARK: - Location Handling
    
    private func setupLocationObserver() {
        locationManager.$location
            .compactMap { $0 }
            .sink { [weak self] location in
                self?.currentLocation = location
                Task {
                    await self?.fetchWeatherForCurrentLocation()
                }
            }
            .store(in: &cancellables)
    }
    
    func requestLocationPermission() {
        locationManager.requestPermission()
    }
    
    // MARK: - Weather Data Fetching
    
    /// Fetch weather for current location
    func fetchWeatherForCurrentLocation() async {
        guard let location = currentLocation ?? locationManager.location else {
            errorMessage = "Location unavailable"
            return
        }
        
        await fetchWeather(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude
        )
        
        // Reverse geocode for location name
        await fetchLocationName(for: location)
    }
    
    /// Fetch weather for specific coordinates
    func fetchWeather(latitude: Double, longitude: Double) async {
        isLoading = true
        errorMessage = nil
        
        do {
            // Fetch current weather (includes hourly and daily)
            let response: WeatherResponse = try await apiClient.get(
                "/api/v1/weather/current",
                queryItems: [
                    URLQueryItem(name: "latitude", value: String(latitude)),
                    URLQueryItem(name: "longitude", value: String(longitude))
                ]
            )
            
            currentWeather = response.current
            hourlyForecast = response.hourly
            dailyForecast = response.daily
            
            // Sync data to widgets
            updateWidgets()
            
            // Fetch air quality
            await fetchAirQuality(latitude: latitude, longitude: longitude)
            
            // Fetch precipitation nowcast
            await fetchPrecipitationNowcast(latitude: latitude, longitude: longitude)
            
            // Fetch pollen data
            await fetchPollen(latitude: latitude, longitude: longitude)
            
            // Fetch all additional data from new API endpoints
            await fetchAdditionalData(latitude: latitude, longitude: longitude)

            isLoading = false
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }
    
    // MARK: - Widget Integration
    
    /// Update widget data via App Groups
    private func updateWidgets() {
        guard let weather = currentWeather else { return }
        
        let today = dailyForecast.first
        let hourlyData = hourlyForecast.prefix(6).map { hour in
            WidgetHourlyData(
                hour: formatHour(hour.time),
                temperature: hour.temperature,
                weatherCode: hour.weatherCode
            )
        }
        
        let widgetData = WidgetWeatherData(
            temperature: weather.temperature,
            feelsLike: weather.feelsLike,
            weatherCode: weather.weatherCode,
            weatherDescription: weather.weatherDescription,
            humidity: weather.humidity,
            windSpeed: weather.windSpeed,
            locationName: locationName,
            sunrise: today?.sunrise,
            sunset: today?.sunset,
            hourlyForecast: Array(hourlyData),
            lastUpdated: Date()
        )
        
        WidgetDataManager.saveWeatherData(widgetData)
        WidgetCenter.shared.reloadAllTimelines()
    }
    
    private func formatHour(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "ha"
        return formatter.string(from: date)
    }
    
    /// Fetch air quality data
    func fetchAirQuality(latitude: Double, longitude: Double) async {
        do {
            let response: AirQualityResponse = try await apiClient.get(
                "/api/v1/weather/air-quality",
                queryItems: [
                    URLQueryItem(name: "latitude", value: String(latitude)),
                    URLQueryItem(name: "longitude", value: String(longitude))
                ]
            )
            
            airQuality = response.airQuality
        } catch {
            print("Error fetching air quality: \(error)")
        }
    }
    
    /// Fetch precipitation nowcast data
    func fetchPrecipitationNowcast(latitude: Double, longitude: Double) async {
        do {
            let response: PrecipitationNowcast = try await apiClient.get(
                "/api/v1/weather/nowcast",
                queryItems: [
                    URLQueryItem(name: "latitude", value: String(latitude)),
                    URLQueryItem(name: "longitude", value: String(longitude))
                ]
            )
            
            precipitationNowcast = response
        } catch {
            print("Error fetching precipitation nowcast: \(error)")
        }
    }
    
    /// Fetch pollen data
    func fetchPollen(latitude: Double, longitude: Double) async {
        do {
            let response: PollenResponse = try await apiClient.get(
                "/api/v1/health/pollen/today",
                queryItems: [
                    URLQueryItem(name: "latitude", value: String(latitude)),
                    URLQueryItem(name: "longitude", value: String(longitude))
                ]
            )

            pollen = response
        } catch {
            print("Error fetching pollen: \(error)")
        }
    }

    /// Fetch location name from coordinates
    private func fetchLocationName(for location: CLLocation) async {
        if let name = await locationManager.getLocationName(for: location) {
            locationName = name
        }
    }
    
    // MARK: - Additional API Data (Non-blocking)
    
    /// Fetch all additional data from the new API endpoints
    private func fetchAdditionalData(latitude: Double, longitude: Double) async {
        async let alertsTask: () = fetchAlerts(latitude: latitude, longitude: longitude)
        async let fluTask: () = fetchFluRisk(latitude: latitude, longitude: longitude)
        async let migraineTask: () = fetchMigraineRisk(latitude: latitude, longitude: longitude)
        async let activitiesTask: () = fetchActivities(latitude: latitude, longitude: longitude)
        async let nwsTask: () = fetchNWSAlerts(latitude: latitude, longitude: longitude)
        async let multiTask: () = fetchMultiSourceWeather(latitude: latitude, longitude: longitude)
        async let uvTask: () = fetchUVIndex(latitude: latitude, longitude: longitude)
        async let marineTask: () = fetchMarineWeather(latitude: latitude, longitude: longitude)
        async let precipTask: () = fetchPrecipNowcastDetailed(latitude: latitude, longitude: longitude)
        
        // Await all concurrently (graceful failure — individual errors logged, not thrown)
        _ = await (alertsTask, fluTask, migraineTask, activitiesTask, nwsTask, multiTask, uvTask, marineTask, precipTask)
    }
    
    private func fetchAlerts(latitude: Double, longitude: Double) async {
        do {
            let response = try await apiClient.getAlerts(latitude: latitude, longitude: longitude)
            self.alerts = response.alerts
        } catch {
            print("Alerts unavailable: \(error.localizedDescription)")
        }
    }
    
    private func fetchFluRisk(latitude: Double, longitude: Double) async {
        do {
            let response = try await apiClient.getFluRisk(latitude: latitude, longitude: longitude)
            self.fluRisk = response
        } catch {
            print("Flu risk unavailable: \(error.localizedDescription)")
        }
    }
    
    private func fetchMigraineRisk(latitude: Double, longitude: Double) async {
        do {
            let response = try await apiClient.getMigraineRisk(latitude: latitude, longitude: longitude)
            self.migraineRisk = response
        } catch {
            print("Migraine risk unavailable: \(error.localizedDescription)")
        }
    }
    
    private func fetchActivities(latitude: Double, longitude: Double) async {
        do {
            let response = try await apiClient.getAllActivities(latitude: latitude, longitude: longitude)
            self.activityForecasts = response.activities
        } catch {
            print("Activities unavailable: \(error.localizedDescription)")
        }
    }
    
    private func fetchNWSAlerts(latitude: Double, longitude: Double) async {
        do {
            let response = try await apiClient.getWeatherAlertsNWS(latitude: latitude, longitude: longitude)
            self.nwsAlerts = response.alerts
        } catch {
            print("NWS alerts unavailable: \(error.localizedDescription)")
        }
    }
    
    private func fetchMultiSourceWeather(latitude: Double, longitude: Double) async {
        do {
            let response = try await apiClient.getMultiSourceWeather(latitude: latitude, longitude: longitude)
            self.multiSourceWeather = response
        } catch {
            print("Multi-source weather unavailable: \(error.localizedDescription)")
        }
    }
    
    private func fetchUVIndex(latitude: Double, longitude: Double) async {
        do {
            let response = try await apiClient.getUVIndex(latitude: latitude, longitude: longitude)
            self.uvIndex = response
        } catch {
            print("UV index unavailable: \(error.localizedDescription)")
        }
    }
    
    private func fetchMarineWeather(latitude: Double, longitude: Double) async {
        do {
            let response = try await apiClient.getMarineWeather(latitude: latitude, longitude: longitude)
            self.marineWeather = response
        } catch {
            print("Marine weather unavailable: \(error.localizedDescription)")
        }
    }
    
    private func fetchPrecipNowcastDetailed(latitude: Double, longitude: Double) async {
        do {
            let response = try await apiClient.getPrecipitationNowcast(latitude: latitude, longitude: longitude)
            self.precipNowcastDetailed = response
        } catch {
            print("Precip nowcast detail unavailable: \(error.localizedDescription)")
        }
    }
    
    // MARK: - Notification Preferences
    
    func loadNotificationPreferences() async {
        do {
            let response = try await apiClient.getNotificationPreferences()
            self.notificationPrefs = response.preferences
        } catch {
            print("Notification preferences unavailable: \(error.localizedDescription)")
        }
    }
    
    func updateNotificationPreferences(weatherAlerts: Bool? = nil, dailySummary: Bool? = nil, severeWeather: Bool? = nil) async {
        do {
            let response = try await apiClient.updateNotificationPreferences(
                weatherAlerts: weatherAlerts,
                dailySummary: dailySummary,
                severeWeather: severeWeather
            )
            self.notificationPrefs = response.preferences
        } catch {
            print("Update notification preferences failed: \(error.localizedDescription)")
        }
    }
    
    // MARK: - Personalization
    
    func trackEvent(eventType: String, eventData: [String: Any]) async {
        do {
            _ = try await apiClient.trackEvent(eventType: eventType, eventData: eventData)
        } catch {
            print("Track event failed: \(error.localizedDescription)")
        }
    }
    
    // MARK: - Favorite Locations
    
    /// Fetch user's favorite locations
    func fetchFavoriteLocations() async {
        do {
            let response: FavoriteLocationsResponse = try await apiClient.get("/api/v1/locations/favorites")
            favoriteLocations = response.favorites
        } catch {
            print("Error fetching favorites: \(error)")
        }
    }
    
    /// Add location to favorites
    func addFavorite(name: String, latitude: Double, longitude: Double, isDefault: Bool = false) async -> Bool {
        do {
            // The backend takes these as query parameters even on POST, and
            // post() has no queryItems parameter — build the query string here,
            // with URLComponents doing the percent-encoding for names like
            // "São Paulo".
            var components = URLComponents()
            components.queryItems = [
                URLQueryItem(name: "name", value: name),
                URLQueryItem(name: "latitude", value: String(latitude)),
                URLQueryItem(name: "longitude", value: String(longitude)),
                URLQueryItem(name: "is_default", value: String(isDefault))
            ]
            let query = components.percentEncodedQuery ?? ""
            let _: AddFavoriteResponse = try await apiClient.post(
                "/api/v1/locations/favorites?\(query)"
            )

            await fetchFavoriteLocations()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }
    
    /// Remove location from favorites
    func removeFavorite(locationId: Int) async -> Bool {
        do {
            let _: MessageResponse = try await apiClient.delete("/api/v1/locations/favorites/\(locationId)")
            await fetchFavoriteLocations()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }
    
    /// Search locations
    func searchLocations(query: String) async -> [LocationSearchResult] {
        guard !query.isEmpty else { return [] }
        
        do {
            let response: LocationSearchResponse = try await apiClient.get(
                "/api/v1/locations/search",
                queryItems: [URLQueryItem(name: "query", value: query)]
            )
            return response.results
        } catch {
            print("Error searching locations: \(error)")
            return []
        }
    }
    
    // MARK: - Dismiss Alert
    
    func dismissAlert(alertId: Int) async {
        do {
            _ = try await apiClient.dismissAlert(alertId: alertId)
        } catch {
            print("Dismiss alert failed: \(error.localizedDescription)")
        }
    }
    
    // MARK: - Refresh
    
    func refresh() async {
        await fetchWeatherForCurrentLocation()
        await fetchFavoriteLocations()
    }
}

// MARK: - Supporting Types

struct FavoriteLocationsResponse: Codable {
    let count: Int
    let favorites: [FavoriteLocation]
}

struct FavoriteLocation: Codable, Identifiable {
    let id: Int
    let name: String
    let latitude: Double
    let longitude: Double
    let isDefault: Bool
    let createdAt: String?
    
    enum CodingKeys: String, CodingKey {
        case id, name, latitude, longitude
        case isDefault = "is_default"
        case createdAt = "created_at"
    }
}

struct AddFavoriteResponse: Codable {
    let message: String
    let location: FavoriteLocation
}

struct LocationSearchResponse: Codable {
    let query: String
    let count: Int
    let results: [LocationSearchResult]
}

struct LocationSearchResult: Codable, Identifiable {
    var id: String { "\(latitude)-\(longitude)" }
    let name: String
    let latitude: Double
    let longitude: Double
    let country: String?
    let admin1: String?
    let timezone: String?
    let displayName: String
    
    enum CodingKeys: String, CodingKey {
        case name, latitude, longitude, country, admin1, timezone
        case displayName = "display_name"
    }
}

struct AirQualityResponse: Codable {
    let airQuality: AirQuality
    
    enum CodingKeys: String, CodingKey {
        case airQuality = "air_quality"
    }
}

// PrecipitationNowcast is declared in Views/Components/PrecipitationBanner.swift.
// A second two-field copy lived here and collided with it; the backend returns
// all seven fields (has_precipitation, precipitation_in_minutes,
// precipitation_ends_in_minutes, intensity, precipitation_type, probability,
// summary), so the fuller declaration is the correct one.
