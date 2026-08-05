import CoreLocation
import Combine
import Network

/// Production-grade Location Manager with:
/// - Adaptive accuracy based on network conditions
/// - Location caching for offline use
/// - Freshness validation
/// - Timeout handling
/// - Network state awareness
/// - Graceful degradation
final class LocationManager: NSObject, ObservableObject {

    /// Shared instance. WeatherViewModel defaults its dependency to this;
    /// it was referenced but never declared, so the app target did not build.
    static let shared = LocationManager()

    
    // MARK: - Published State
    
    @Published private(set) var location: CLLocation?
    @Published private(set) var locationName: String = "Loading..."
    @Published private(set) var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published private(set) var locationError: LocationError?
    @Published private(set) var isLocating: Bool = false
    @Published private(set) var networkState: NetworkState = .unknown
    @Published private(set) var locationQuality: LocationQuality = .unknown
    
    // MARK: - Location Quality Enum
    
    enum LocationQuality: String, Comparable {
        case gps = "GPS"            // < 10m accuracy
        case wifi = "WiFi"          // 10-100m accuracy
        case cell = "Cell Tower"    // 100-3000m accuracy
        case cached = "Cached"      // From storage
        case unknown = "Unknown"
        
        var accuracy: Double {
            switch self {
            case .gps: return 10
            case .wifi: return 100
            case .cell: return 3000
            case .cached: return 5000
            case .unknown: return 10000
            }
        }
        
        static func < (lhs: LocationQuality, rhs: LocationQuality) -> Bool {
            lhs.accuracy < rhs.accuracy
        }
        
        static func from(horizontalAccuracy: Double) -> LocationQuality {
            switch horizontalAccuracy {
            case ..<10: return .gps
            case ..<100: return .wifi
            case ..<3000: return .cell
            default: return .unknown
            }
        }
    }
    
    // MARK: - Network State
    
    enum NetworkState {
        case wifi
        case cellular
        case offline
        case unknown
    }
    
    // MARK: - Error Types
    
    enum LocationError: Error, LocalizedError {
        case permissionDenied
        case permissionRestricted
        case locationUnavailable
        case timeout
        case networkUnavailable
        case staleLocation
        
        var errorDescription: String? {
            switch self {
            case .permissionDenied: return "Location access denied"
            case .permissionRestricted: return "Location access restricted"
            case .locationUnavailable: return "Unable to determine location"
            case .timeout: return "Location request timed out"
            case .networkUnavailable: return "Network unavailable for location"
            case .staleLocation: return "Location data is outdated"
            }
        }
    }
    
    // MARK: - Configuration
    
    struct Configuration {
        /// Maximum age of cached location before refresh (seconds)
        var maxLocationAge: TimeInterval = 300  // 5 minutes
        
        /// Timeout for high-accuracy location request
        var highAccuracyTimeout: TimeInterval = 15
        
        /// Timeout for low-accuracy fallback
        var lowAccuracyTimeout: TimeInterval = 30
        
        /// Minimum accuracy required (meters)
        var minimumAccuracy: Double = 100
        
        /// Whether to use cached location when offline
        var useCachedWhenOffline: Bool = true
        
        /// Whether to degrade accuracy on poor network
        var adaptiveDegradation: Bool = true
    }
    
    var configuration = Configuration()
    
    // MARK: - Private Properties
    
    private let locationManager = CLLocationManager()
    private let geocoder = CLGeocoder()
    private let networkMonitor = NWPathMonitor()
    private let networkQueue = DispatchQueue(label: "com.climaai.network")
    
    private var locationContinuation: CheckedContinuation<CLLocation, Error>?
    private var timeoutTask: Task<Void, Never>?
    private var cancellables = Set<AnyCancellable>()
    
    // UserDefaults keys for caching
    private let cachedLatKey = "CLIMAAI_CACHED_LAT"
    private let cachedLonKey = "CLIMAAI_CACHED_LON"
    private let cachedTimestampKey = "CLIMAAI_CACHED_TIMESTAMP"
    private let cachedNameKey = "CLIMAAI_CACHED_LOCATION_NAME"
    
    // MARK: - Initialization
    
    override init() {
        super.init()
        setupLocationManager()
        setupNetworkMonitor()
        loadCachedLocation()
    }
    
    private func setupLocationManager() {
        locationManager.delegate = self
        locationManager.distanceFilter = 50 // Update every 50m of movement
        locationManager.pausesLocationUpdatesAutomatically = true
        locationManager.activityType = .other
    }
    
    private func setupNetworkMonitor() {
        networkMonitor.pathUpdateHandler = { [weak self] path in
            DispatchQueue.main.async {
                self?.updateNetworkState(path)
            }
        }
        networkMonitor.start(queue: networkQueue)
    }
    
    private func updateNetworkState(_ path: NWPath) {
        if path.status == .satisfied {
            if path.usesInterfaceType(.wifi) {
                networkState = .wifi
            } else if path.usesInterfaceType(.cellular) {
                networkState = .cellular
            } else {
                networkState = .unknown
            }
        } else {
            networkState = .offline
        }
        
        // Adapt accuracy based on network
        if configuration.adaptiveDegradation {
            adaptAccuracyToNetwork()
        }
    }
    
    private func adaptAccuracyToNetwork() {
        switch networkState {
        case .wifi:
            // Best accuracy when on WiFi (lower battery impact)
            locationManager.desiredAccuracy = kCLLocationAccuracyBest
        case .cellular:
            // Good accuracy on cellular
            locationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        case .offline:
            // Use cached location, don't update
            if configuration.useCachedWhenOffline {
                loadCachedLocation()
            }
        case .unknown:
            // Balanced default
            locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        }
    }
    
    // MARK: - Public API
    
    /// Request location permission
    func requestPermission() {
        locationManager.requestWhenInUseAuthorization()
    }
    
    /// Request always permission (for widgets/background refresh)
    func requestAlwaysPermission() {
        locationManager.requestAlwaysAuthorization()
    }
    
    /// Request current location with async/await
    func requestLocation() async throws -> CLLocation {
        // Check network state first
        if networkState == .offline && configuration.useCachedWhenOffline {
            if let cached = getCachedLocation(), !isLocationStale(cached) {
                locationQuality = .cached
                return cached
            }
            throw LocationError.networkUnavailable
        }
        
        // Check permission
        guard authorizationStatus == .authorizedWhenInUse || 
              authorizationStatus == .authorizedAlways else {
            throw LocationError.permissionDenied
        }
        
        isLocating = true
        defer { isLocating = false }
        
        return try await withCheckedThrowingContinuation { continuation in
            self.locationContinuation = continuation
            
            // Configure for best accuracy
            locationManager.desiredAccuracy = kCLLocationAccuracyBest
            locationManager.requestLocation()
            
            // Set timeout
            let timeout = networkState == .wifi ? 
                configuration.highAccuracyTimeout : 
                configuration.lowAccuracyTimeout
            
            timeoutTask = Task { [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
                guard !Task.isCancelled else { return }
                
                await MainActor.run {
                    self?.handleLocationTimeout()
                }
            }
        }
    }
    
    /// Start continuous location updates (for navigation/real-time)
    func startContinuousUpdates() {
        guard authorizationStatus == .authorizedWhenInUse || 
              authorizationStatus == .authorizedAlways else { return }
        
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.startUpdatingLocation()
        isLocating = true
    }
    
    /// Stop continuous updates
    func stopContinuousUpdates() {
        locationManager.stopUpdatingLocation()
        isLocating = false
    }
    
    /// Get current location (sync, may be stale)
    var currentLocation: CLLocation? {
        if let loc = location, !isLocationStale(loc) {
            return loc
        }
        return getCachedLocation()
    }
    
    // MARK: - Location Validation
    
    private func isLocationStale(_ location: CLLocation) -> Bool {
        let age = Date().timeIntervalSince(location.timestamp)
        return age > configuration.maxLocationAge
    }
    
    private func isLocationAccurate(_ location: CLLocation) -> Bool {
        return location.horizontalAccuracy > 0 && 
               location.horizontalAccuracy <= configuration.minimumAccuracy
    }
    
    // MARK: - Caching
    
    private func cacheLocation(_ location: CLLocation, name: String?) {
        UserDefaults.standard.set(location.coordinate.latitude, forKey: cachedLatKey)
        UserDefaults.standard.set(location.coordinate.longitude, forKey: cachedLonKey)
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: cachedTimestampKey)
        if let name = name {
            UserDefaults.standard.set(name, forKey: cachedNameKey)
        }
    }
    
    private func loadCachedLocation() {
        guard let timestamp = UserDefaults.standard.object(forKey: cachedTimestampKey) as? Double,
              UserDefaults.standard.object(forKey: cachedLatKey) != nil else {
            return
        }
        
        let lat = UserDefaults.standard.double(forKey: cachedLatKey)
        let lon = UserDefaults.standard.double(forKey: cachedLonKey)
        let cachedDate = Date(timeIntervalSince1970: timestamp)
        
        // Only use cache if less than 1 hour old
        guard Date().timeIntervalSince(cachedDate) < 3600 else { return }
        
        let cachedLocation = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
            altitude: 0,
            horizontalAccuracy: 5000, // Mark as low accuracy
            verticalAccuracy: -1,
            timestamp: cachedDate
        )
        
        self.location = cachedLocation
        self.locationQuality = .cached
        
        if let name = UserDefaults.standard.string(forKey: cachedNameKey) {
            self.locationName = name
        }
    }
    
    private func getCachedLocation() -> CLLocation? {
        guard UserDefaults.standard.object(forKey: cachedLatKey) != nil else {
            return nil
        }
        
        let lat = UserDefaults.standard.double(forKey: cachedLatKey)
        let lon = UserDefaults.standard.double(forKey: cachedLonKey)
        let timestamp = UserDefaults.standard.double(forKey: cachedTimestampKey)
        
        return CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
            altitude: 0,
            horizontalAccuracy: 5000,
            verticalAccuracy: -1,
            timestamp: Date(timeIntervalSince1970: timestamp)
        )
    }
    
    // MARK: - Reverse Geocoding with Caching

    /// Reverse-geocode a location into the display name used across the app
    /// ("City, Country"). Returns nil when no name can be determined, so
    /// callers keep whatever name they already show instead of a made-up one.
    func getLocationName(for location: CLLocation) async -> String? {
        // Offline, the cached name is the only truthful answer available.
        guard networkState != .offline else {
            return UserDefaults.standard.string(forKey: cachedNameKey)
        }

        // A dedicated geocoder: CLGeocoder rejects a second request while one
        // is in flight, and `self.geocoder` may be busy with the delegate-driven
        // reverseGeocode(_:) path.
        guard let placemark = try? await CLGeocoder().reverseGeocodeLocation(location).first,
              let name = Self.displayName(for: placemark) else {
            return nil
        }

        cacheLocation(location, name: name)
        return name
    }

    /// The naming precedence both geocoding paths share: city, else
    /// state/province, else country — always with the country appended when
    /// known.
    private static func displayName(for placemark: CLPlacemark) -> String? {
        if let city = placemark.locality {
            return placemark.country.map { "\(city), \($0)" } ?? city
        }
        if let admin = placemark.administrativeArea, let country = placemark.country {
            return "\(admin), \(country)"
        }
        return placemark.country
    }

    private func reverseGeocode(_ location: CLLocation) {
        // Check network
        guard networkState != .offline else {
            if let cached = UserDefaults.standard.string(forKey: cachedNameKey) {
                locationName = cached
            }
            return
        }
        
        geocoder.reverseGeocodeLocation(location) { [weak self] placemarks, error in
            guard let self = self else { return }
            
            if let error = error {
                print("Reverse geocoding error: \(error.localizedDescription)")
                // Fall back to cache
                if let cached = UserDefaults.standard.string(forKey: self.cachedNameKey) {
                    self.locationName = cached
                } else {
                    self.locationName = "Lat: \(String(format: "%.2f", location.coordinate.latitude)), Lon: \(String(format: "%.2f", location.coordinate.longitude))"
                }
                return
            }
            
            if let placemark = placemarks?.first {
                let name = Self.displayName(for: placemark) ?? "Unknown Location"
                self.locationName = name
                self.cacheLocation(location, name: name)
            }
        }
    }
    
    // MARK: - Timeout Handling
    
    private func handleLocationTimeout() {
        locationManager.stopUpdatingLocation()
        
        // Try to use cached location as fallback
        if let cached = getCachedLocation() {
            location = cached
            locationQuality = .cached
            locationContinuation?.resume(returning: cached)
        } else {
            locationContinuation?.resume(throwing: LocationError.timeout)
        }
        
        locationContinuation = nil
        timeoutTask?.cancel()
        timeoutTask = nil
    }
    
    deinit {
        networkMonitor.cancel()
        timeoutTask?.cancel()
    }
}

// MARK: - CLLocationManagerDelegate

extension LocationManager: CLLocationManagerDelegate {
    
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
        
        switch authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            // Trigger location request
            if location == nil || isLocationStale(location!) {
                locationManager.requestLocation()
            }
        case .denied:
            locationError = .permissionDenied
            loadCachedLocation() // Fall back to cache
        case .restricted:
            locationError = .permissionRestricted
            loadCachedLocation()
        default:
            break
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        timeoutTask?.cancel()
        timeoutTask = nil
        
        guard let newLocation = locations.last else { return }
        
        // Validate location quality
        locationQuality = LocationQuality.from(horizontalAccuracy: newLocation.horizontalAccuracy)
        
        // Accept if accurate enough or if we don't have any location
        if isLocationAccurate(newLocation) || location == nil {
            location = newLocation
            locationError = nil
            reverseGeocode(newLocation)
            
            // Resume continuation if waiting
            locationContinuation?.resume(returning: newLocation)
            locationContinuation = nil
            
            // Cache for offline use
            cacheLocation(newLocation, name: nil)
        } else if isLocationStale(location ?? newLocation) {
            // Accept less accurate if our current is stale
            location = newLocation
            reverseGeocode(newLocation)
            locationContinuation?.resume(returning: newLocation)
            locationContinuation = nil
        }
        // Otherwise, wait for better accuracy (continuous updates only)
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        timeoutTask?.cancel()
        timeoutTask = nil
        
        print("Location error: \(error.localizedDescription)")
        
        if let clError = error as? CLError {
            switch clError.code {
            case .denied:
                locationError = .permissionDenied
            case .locationUnknown:
                locationError = .locationUnavailable
            case .network:
                locationError = .networkUnavailable
                // Try cached location
                if let cached = getCachedLocation() {
                    location = cached
                    locationQuality = .cached
                    locationContinuation?.resume(returning: cached)
                    locationContinuation = nil
                    return
                }
            default:
                locationError = .locationUnavailable
            }
        }
        
        locationContinuation?.resume(throwing: locationError ?? LocationError.locationUnavailable)
        locationContinuation = nil
    }
}
