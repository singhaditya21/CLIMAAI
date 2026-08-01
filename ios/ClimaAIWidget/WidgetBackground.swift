//
//  WidgetBackground.swift
//  ClimaAIWidget
//

import SwiftUI
import WidgetKit

extension View {
    /// Applies the widget container background, falling back on iOS 16.
    ///
    /// `containerBackground(_:for:)` is iOS 17+ and is how widgets are expected
    /// to declare their background from that release onward. The widget code
    /// called it unconditionally, which does not compile against this
    /// extension's iOS 16 deployment target:
    ///
    ///     'containerBackground(_:for:)' is only available in application
    ///     extensions for iOS 17.0 or newer
    ///
    /// The alternative was to raise the extension to iOS 17, but that would stop
    /// the widget installing on iOS 16 devices — a product decision rather than a
    /// build fix. Guarding it here keeps the iOS 16 support the app already
    /// advertises, and on iOS 16 uses the plain background that was standard
    /// before `containerBackground` existed.
    @ViewBuilder
    func widgetContainerBackground() -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            self.containerBackground(.fill.tertiary, for: .widget)
        } else {
            self.background(Color(.systemBackground))
        }
    }
}
