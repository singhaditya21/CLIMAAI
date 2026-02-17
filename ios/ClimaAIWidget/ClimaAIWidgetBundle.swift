//
//  ClimaAIWidgetBundle.swift
//  ClimaAIWidget
//
//  Widget extension bundle for ClimaAI weather widgets
//

import WidgetKit
import SwiftUI

@main
struct ClimaAIWidgetBundle: WidgetBundle {
    var body: some Widget {
        SmallWeatherWidget()
        MediumWeatherWidget()
        LargeWeatherWidget()
        
        // iOS 16+ Lock Screen widgets
        if #available(iOSApplicationExtension 16.0, *) {
            LockScreenWeatherWidget()
        }
    }
}
