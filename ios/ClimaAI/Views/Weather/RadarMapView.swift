//
//  RadarMapView.swift
//  ClimaAI
//
//  Animated weather radar map using RainViewer tiles
//

import SwiftUI
import MapKit

struct RadarMapView: View {
    @StateObject private var radarManager = RadarManager()
    @State private var region: MKCoordinateRegion
    @State private var isPlaying = true
    @State private var currentFrame = 0
    
    init(latitude: Double = 37.7749, longitude: Double = -122.4194) {
        _region = State(initialValue: MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            span: MKCoordinateSpan(latitudeDelta: 2.0, longitudeDelta: 2.0)
        ))
    }
    
    var body: some View {
        ZStack {
            // Map background with Radar Overlay
            RadarMapViewRepresentable(
                region: $region,
                timestamp: radarManager.currentTimestamp
            )
            .ignoresSafeArea()
            
            // Controls overlay
            VStack {
                Spacer()
                controlBar
            }
        }
        .navigationTitle("Radar")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            radarManager.fetchRadarTimestamps()
        }
        .onDisappear {
            radarManager.stopAnimation()
        }
    }
    
    // MARK: - Control Bar
    
    private var controlBar: some View {
        VStack(spacing: 12) {
            // Timeline slider
            HStack(spacing: 16) {
                Text(radarManager.formattedTime)
                    .font(.caption)
                    .monospacedDigit()
                
                Slider(
                    value: Binding(
                        get: { Double(currentFrame) },
                        set: { newValue in
                            currentFrame = Int(newValue)
                            radarManager.setFrame(currentFrame)
                        }
                    ),
                    in: 0...Double(max(0, radarManager.timestamps.count - 1)),
                    step: 1
                )
                .disabled(radarManager.timestamps.isEmpty)
                
                Text(radarManager.timeOffset)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            // Playback controls
            HStack(spacing: 24) {
                Button {
                    radarManager.previousFrame()
                    currentFrame = radarManager.currentFrameIndex
                } label: {
                    Image(systemName: "backward.frame.fill")
                        .font(.title2)
                }
                
                Button {
                    if isPlaying {
                        radarManager.stopAnimation()
                    } else {
                        radarManager.startAnimation()
                    }
                    isPlaying.toggle()
                } label: {
                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                        .font(.title)
                }
                
                Button {
                    radarManager.nextFrame()
                    currentFrame = radarManager.currentFrameIndex
                } label: {
                    Image(systemName: "forward.frame.fill")
                        .font(.title2)
                }
            }
            
            // Legend
            legendBar
        }
        .padding()
        .background(.ultraThinMaterial)
        .cornerRadius(16)
        .padding()
    }
    
    // MARK: - Legend
    
    private var legendBar: some View {
        HStack {
            ForEach(radarLegend, id: \.label) { item in
                HStack(spacing: 4) {
                    Circle()
                        .fill(item.color)
                        .frame(width: 8, height: 8)
                    Text(item.label)
                        .font(.caption2)
                }
            }
        }
        .padding(.top, 8)
    }
    
    private var radarLegend: [(label: String, color: Color)] {
        [
            ("Light", .green.opacity(0.7)),
            ("Moderate", .yellow),
            ("Heavy", .orange),
            ("Intense", .red)
        ]
    }
}

// MARK: - Radar Manager

class RadarManager: ObservableObject {
    @Published var timestamps: [Int] = []
    @Published var currentFrameIndex = 0
    @Published var isLoading = true
    
    private var animationTimer: Timer?
    private let rainViewerAPI = "https://api.rainviewer.com/public/weather-maps.json"
    
    var currentTimestamp: Int? {
        guard currentFrameIndex < timestamps.count else { return nil }
        return timestamps[currentFrameIndex]
    }
    
    var formattedTime: String {
        guard let ts = currentTimestamp else { return "--:--" }
        let date = Date(timeIntervalSince1970: TimeInterval(ts))
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }
    
    var timeOffset: String {
        guard let ts = currentTimestamp else { return "" }
        let now = Date()
        let radarTime = Date(timeIntervalSince1970: TimeInterval(ts))
        let diff = Int(now.timeIntervalSince(radarTime) / 60)
        
        if diff < 0 {
            return "+\(abs(diff))m"
        } else if diff == 0 {
            return "Now"
        } else {
            return "-\(diff)m"
        }
    }
    
    func fetchRadarTimestamps() {
        guard let url = URL(string: rainViewerAPI) else { return }
        
        URLSession.shared.dataTask(with: url) { [weak self] data, _, error in
            guard let data = data, error == nil else {
                DispatchQueue.main.async {
                    self?.loadMockData()
                }
                return
            }
            
            do {
                let response = try JSONDecoder().decode(RainViewerResponse.self, from: data)
                
                DispatchQueue.main.async {
                    // Combine past and nowcast frames
                    let past = response.radar.past.map { $0.time }
                    let nowcast = response.radar.nowcast.map { $0.time }
                    self?.timestamps = past + nowcast
                    self?.currentFrameIndex = past.count - 1  // Start at current
                    self?.isLoading = false
                    self?.startAnimation()
                }
            } catch {
                DispatchQueue.main.async {
                    self?.loadMockData()
                }
            }
        }.resume()
    }
    
    private func loadMockData() {
        let now = Int(Date().timeIntervalSince1970)
        timestamps = (-6...2).map { now + ($0 * 600) }  // 10-min intervals
        currentFrameIndex = 6
        isLoading = false
        startAnimation()
    }
    
    func startAnimation() {
        animationTimer?.invalidate()
        animationTimer = Timer.scheduledTimer(withTimeInterval: 0.8, repeats: true) { [weak self] _ in
            self?.nextFrame()
        }
    }
    
    func stopAnimation() {
        animationTimer?.invalidate()
        animationTimer = nil
    }
    
    func nextFrame() {
        currentFrameIndex = (currentFrameIndex + 1) % max(1, timestamps.count)
    }
    
    func previousFrame() {
        currentFrameIndex = (currentFrameIndex - 1 + timestamps.count) % max(1, timestamps.count)
    }
    
    func setFrame(_ index: Int) {
        currentFrameIndex = min(max(0, index), timestamps.count - 1)
    }
}

// MARK: - RainViewer API Models

struct RainViewerResponse: Codable {
    let version: String
    let generated: Int
    let host: String
    let radar: RadarData
}

struct RadarData: Codable {
    let past: [RadarFrame]
    let nowcast: [RadarFrame]
}

struct RadarFrame: Codable {
    let time: Int
    let path: String
}

// MARK: - Radar Map View Representable

struct RadarMapViewRepresentable: UIViewRepresentable {
    @Binding var region: MKCoordinateRegion
    let timestamp: Int?
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.region = region
        mapView.showsUserLocation = true
        mapView.isRotateEnabled = false // Radar maps are usually north-up
        return mapView
    }

    func updateUIView(_ uiView: MKMapView, context: Context) {
        // Update region if changed externally
        // Only update if significantly different to avoid jitter during pan
        let currentRegion = uiView.region
        if abs(currentRegion.center.latitude - region.center.latitude) > 0.0001 ||
           abs(currentRegion.center.longitude - region.center.longitude) > 0.0001 ||
           abs(currentRegion.span.latitudeDelta - region.span.latitudeDelta) > 0.0001 {
             uiView.setRegion(region, animated: true)
        }

        // Update overlay if timestamp changed
        if let timestamp = timestamp {
            if context.coordinator.lastTimestamp != timestamp {
                // Remove existing tile overlays
                let overlaysToRemove = uiView.overlays.filter { $0 is MKTileOverlay }
                uiView.removeOverlays(overlaysToRemove)

                // Add new overlay
                let template = "https://tilecache.rainviewer.com/v2/radar/\(timestamp)/256/{z}/{x}/{y}/2/1_1.png"
                let overlay = MKTileOverlay(urlTemplate: template)
                overlay.canReplaceMapContent = false
                uiView.addOverlay(overlay)

                context.coordinator.lastTimestamp = timestamp
            }
        } else {
            // Remove all overlays if no timestamp
            let overlaysToRemove = uiView.overlays.filter { $0 is MKTileOverlay }
            uiView.removeOverlays(overlaysToRemove)
            context.coordinator.lastTimestamp = nil
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    class Coordinator: NSObject, MKMapViewDelegate {
        var parent: RadarMapViewRepresentable
        var lastTimestamp: Int?

        init(_ parent: RadarMapViewRepresentable) {
            self.parent = parent
        }

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let tileOverlay = overlay as? MKTileOverlay {
                let renderer = MKTileOverlayRenderer(tileOverlay: tileOverlay)
                renderer.alpha = 0.7
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }

        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
            DispatchQueue.main.async {
                self.parent.region = mapView.region
            }
        }
    }
}

#Preview {
    NavigationView {
        RadarMapView()
    }
}
