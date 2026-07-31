//
//  ThemeManager.swift
//  ClimaAI
//
//  Theme selection and customization
//

import SwiftUI

/// Available app themes
enum AppTheme: String, CaseIterable, Identifiable {
    case system = "System"
    case light = "Light"
    case dark = "Dark"
    case ocean = "Ocean"
    case sunset = "Sunset"
    
    var id: String { rawValue }
    
    /// Icon for theme picker
    var icon: String {
        switch self {
        case .system: return "gear"
        case .light: return "sun.max.fill"
        case .dark: return "moon.fill"
        case .ocean: return "water.waves"
        case .sunset: return "sunset.fill"
        }
    }
    
    /// Color scheme for theme
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark, .ocean: return .dark
        case .sunset: return .light
        }
    }
    
    /// Primary accent color
    var accentColor: Color {
        switch self {
        case .system, .light, .dark: return .blue
        case .ocean: return Color(red: 0.0, green: 0.8, blue: 0.9)  // Cyan
        case .sunset: return Color(red: 1.0, green: 0.5, blue: 0.3)  // Orange-red
        }
    }
    
    /// Secondary accent color for gradients
    var secondaryColor: Color {
        switch self {
        case .system, .light, .dark: return .cyan
        case .ocean: return Color(red: 0.0, green: 0.4, blue: 0.7)  // Deep blue
        case .sunset: return Color(red: 0.9, green: 0.3, blue: 0.5)  // Pink
        }
    }
    
    /// Background gradient colors
    var backgroundGradient: [Color] {
        switch self {
        case .system, .light: return [Color(white: 0.95), Color(white: 0.9)]
        case .dark: return [Color(white: 0.1), Color(white: 0.15)]
        case .ocean: return [
            Color(red: 0.0, green: 0.2, blue: 0.4),
            Color(red: 0.0, green: 0.1, blue: 0.3)
        ]
        case .sunset: return [
            Color(red: 1.0, green: 0.85, blue: 0.7),
            Color(red: 1.0, green: 0.7, blue: 0.6)
        ]
        }
    }
}

/// Theme manager for app-wide theme state
@MainActor
class ThemeManager: ObservableObject {
    static let shared = ThemeManager()
    
    /// Currently selected theme, persisted to UserDefaults
    @AppStorage("selectedTheme") private var selectedThemeRaw: String = AppTheme.system.rawValue
    
    /// Published selected theme for UI binding
    @Published var selectedTheme: AppTheme = .system {
        didSet {
            selectedThemeRaw = selectedTheme.rawValue
        }
    }
    
    private init() {
        // Load theme from storage
        if let theme = AppTheme(rawValue: selectedThemeRaw) {
            selectedTheme = theme
        }
    }
    
    /// Set theme with animation
    func setTheme(_ theme: AppTheme) {
        withAnimation(.easeInOut(duration: 0.3)) {
            selectedTheme = theme
        }
    }
    
    /// Current color scheme (nil = system)
    var colorScheme: ColorScheme? {
        selectedTheme.colorScheme
    }
    
    /// Current accent color
    var accentColor: Color {
        selectedTheme.accentColor
    }
}

// MARK: - View Modifier

struct ThemedViewModifier: ViewModifier {
    @ObservedObject var themeManager = ThemeManager.shared
    
    func body(content: Content) -> some View {
        content
            .preferredColorScheme(themeManager.colorScheme)
            .tint(themeManager.accentColor)
    }
}

extension View {
    func themed() -> some View {
        modifier(ThemedViewModifier())
    }
}

// MARK: - Theme Picker View

struct ThemePickerView: View {
    @ObservedObject var themeManager = ThemeManager.shared
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Appearance")
                .font(.headline)
                .foregroundColor(.secondary)
            
            ForEach(AppTheme.allCases) { theme in
                ThemeOptionRow(
                    theme: theme,
                    isSelected: themeManager.selectedTheme == theme
                ) {
                    themeManager.setTheme(theme)
                }
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(.ultraThinMaterial)
        )
    }
}

struct ThemeOptionRow: View {
    let theme: AppTheme
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack {
                Image(systemName: theme.icon)
                    .foregroundStyle(
                        .linearGradient(
                            colors: [theme.accentColor, theme.secondaryColor],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .font(.title2)
                    .frame(width: 32)
                
                Text(theme.rawValue)
                    .font(.body)
                    .foregroundColor(.primary)
                
                Spacer()
                
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(theme.accentColor)
                }
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    ThemePickerView()
        .padding()
}
