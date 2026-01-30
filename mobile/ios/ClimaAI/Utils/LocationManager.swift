import CoreLocation

class LocationManager: NSObject, ObservableObject {
    private let locationManager = CLLocationManager()
    
    @Published var location: CLLocation?
    @Published var locationName: String = "Loading..."
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published var error: Error?
    
    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyKilometer
    }
    
    func requestPermission() {
        locationManager.requestWhenInUseAuthorization()
    }
    
    func requestLocation() {
        locationManager.requestLocation()
    }
    
    func startUpdating() {
        locationManager.startUpdatingLocation()
    }
    
    func stopUpdating() {
        locationManager.stopUpdatingLocation()
    }
    
    private func reverseGeocode(_ location: CLLocation) {
        let geocoder = CLGeocoder()
        geocoder.reverseGeocodeLocation(location) { [weak self] placemarks, error in
            guard let self = self else { return }
            
            if let error = error {
                print("Reverse geocoding error: \(error.localizedDescription)")
                self.locationName = "Unknown Location"
                return
            }
            
            if let placemark = placemarks?.first {
                if let city = placemark.locality, let country = placemark.country {
                    self.locationName = "\(city), \(country)"
                } else if let city = placemark.locality {
                    self.locationName = city
                } else if let country = placemark.country {
                    self.locationName = country
                } else {
                    self.locationName = "Unknown Location"
                }
            }
        }
    }
}

extension LocationManager: CLLocationManagerDelegate {
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
        
        switch authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            requestLocation()
        case .denied, .restricted:
            error = NSError(
                domain: "LocationManager",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Location access denied"]
            )
        default:
            break
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        self.location = location
        reverseGeocode(location)
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        self.error = error
        print("Location error: \(error.localizedDescription)")
    }
}
