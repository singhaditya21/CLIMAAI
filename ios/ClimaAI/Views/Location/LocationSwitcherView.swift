//
//  LocationSwitcherView.swift
//  ClimaAI
//
//  Location management and search
//

import SwiftUI

struct LocationSwitcherView: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var weatherViewModel: WeatherViewModel
    @State private var searchText = ""
    @State private var searchResults: [LocationSearchResult] = []
    @State private var isSearching = false
    @State private var showingAddConfirmation = false
    @State private var selectedLocation: LocationSearchResult?
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Search bar
                searchBar
                
                List {
                    // Current location section
                    Section {
                        currentLocationRow
                    } header: {
                        Text("Current Location")
                    }
                    
                    // Search results
                    if !searchResults.isEmpty {
                        Section {
                            ForEach(searchResults) { result in
                                searchResultRow(result)
                            }
                        } header: {
                            Text("Search Results")
                        }
                    }
                    
                    // Saved locations
                    if !weatherViewModel.favoriteLocations.isEmpty {
                        Section {
                            ForEach(weatherViewModel.favoriteLocations) { location in
                                savedLocationRow(location)
                            }
                            .onDelete(perform: deleteLocation)
                        } header: {
                            Text("Saved Locations")
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
            .navigationTitle("Locations")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .alert("Add to Saved Locations?", isPresented: $showingAddConfirmation) {
                Button("Cancel", role: .cancel) { }
                Button("Add") {
                    if let location = selectedLocation {
                        Task {
                            await addLocation(location)
                        }
                    }
                }
            } message: {
                if let location = selectedLocation {
                    Text("Add \(location.name) to your saved locations?")
                }
            }
        }
        .task {
            await weatherViewModel.fetchFavoriteLocations()
        }
    }
    
    // MARK: - Subviews
    
    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.secondary)
            
            TextField("Search cities...", text: $searchText)
                .textFieldStyle(.plain)
                .autocorrectionDisabled()
                .onChange(of: searchText) { _, newValue in
                    Task {
                        await performSearch(query: newValue)
                    }
                }
            
            if !searchText.isEmpty {
                Button {
                    searchText = ""
                    searchResults = []
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
            }
            
            if isSearching {
                ProgressView()
                    .scaleEffect(0.8)
            }
        }
        .padding(12)
        .background(Color(.systemGray6))
        .cornerRadius(10)
        .padding()
    }
    
    private var currentLocationRow: some View {
        Button {
            Task {
                weatherViewModel.requestLocationPermission()
                await weatherViewModel.fetchWeatherForCurrentLocation()
                dismiss()
            }
        } label: {
            HStack {
                Image(systemName: "location.fill")
                    .foregroundColor(.blue)
                    .frame(width: 30)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text("Current Location")
                        .font(.body)
                    Text(weatherViewModel.locationName)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                if weatherViewModel.currentLocation != nil {
                    Image(systemName: "checkmark")
                        .foregroundColor(.blue)
                }
            }
        }
        .buttonStyle(.plain)
    }
    
    private func searchResultRow(_ result: LocationSearchResult) -> some View {
        Button {
            selectSearchResult(result)
        } label: {
            HStack {
                Image(systemName: "mappin.circle.fill")
                    .foregroundColor(.orange)
                    .frame(width: 30)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(result.name)
                        .font(.body)
                    Text(result.displayName)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
                
                Spacer()
                
                Button {
                    selectedLocation = result
                    showingAddConfirmation = true
                } label: {
                    Image(systemName: "plus.circle")
                        .foregroundColor(.green)
                }
            }
        }
        .buttonStyle(.plain)
    }
    
    private func savedLocationRow(_ location: FavoriteLocation) -> some View {
        Button {
            selectSavedLocation(location)
        } label: {
            HStack {
                Image(systemName: location.isDefault ? "star.fill" : "mappin.circle")
                    .foregroundColor(location.isDefault ? .yellow : .gray)
                    .frame(width: 30)
                
                Text(location.name)
                    .font(.body)
                
                Spacer()
            }
        }
        .buttonStyle(.plain)
    }
    
    // MARK: - Actions
    
    private func performSearch(query: String) async {
        guard query.count >= 2 else {
            searchResults = []
            return
        }
        
        isSearching = true
        
        // Debounce
        try? await Task.sleep(nanoseconds: 300_000_000)
        
        guard !Task.isCancelled else { return }
        
        searchResults = await weatherViewModel.searchLocations(query: query)
        isSearching = false
    }
    
    private func selectSearchResult(_ result: LocationSearchResult) {
        Task {
            await weatherViewModel.fetchWeather(
                latitude: result.latitude,
                longitude: result.longitude
            )
            dismiss()
        }
    }
    
    private func selectSavedLocation(_ location: FavoriteLocation) {
        Task {
            await weatherViewModel.fetchWeather(
                latitude: location.latitude,
                longitude: location.longitude
            )
            dismiss()
        }
    }
    
    private func addLocation(_ result: LocationSearchResult) async {
        _ = await weatherViewModel.addFavorite(
            name: result.name,
            latitude: result.latitude,
            longitude: result.longitude
        )
    }
    
    private func deleteLocation(at offsets: IndexSet) {
        for index in offsets {
            let location = weatherViewModel.favoriteLocations[index]
            Task {
                _ = await weatherViewModel.removeFavorite(locationId: location.id)
            }
        }
    }
}

#Preview {
    LocationSwitcherView()
        .environmentObject(WeatherViewModel())
}
