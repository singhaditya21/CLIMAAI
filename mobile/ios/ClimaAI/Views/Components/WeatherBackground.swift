//
//  WeatherBackground.swift
//  ClimaAI
//
//  Animated weather background with particles
//

import SwiftUI

struct WeatherBackground: View {
    let weatherCode: Int
    let isDay: Bool
    
    @State private var animationPhase: CGFloat = 0
    @State private var particles: [WeatherParticle] = []
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Gradient background
                backgroundGradient
                    .ignoresSafeArea()
                
                // Weather particles
                Canvas { context, size in
                    for particle in particles {
                        let position = CGPoint(
                            x: particle.x * size.width,
                            y: (particle.y + animationPhase * particle.speed).truncatingRemainder(dividingBy: 1.2) * size.height
                        )
                        
                        if weatherType == .rain {
                            drawRainDrop(context: context, at: position, opacity: particle.opacity)
                        } else if weatherType == .snow {
                            drawSnowflake(context: context, at: position, size: particle.size, opacity: particle.opacity)
                        }
                    }
                }
                .ignoresSafeArea()
                
                // Clouds overlay for cloudy weather
                if weatherType == .cloudy || weatherType == .rain || weatherType == .snow {
                    CloudsOverlay(animationPhase: animationPhase)
                }
                
                // Lightning flash for thunderstorms
                if weatherType == .thunder {
                    LightningOverlay()
                }
                
                // Sun rays for clear day
                if weatherType == .clear && isDay {
                    SunRaysOverlay(animationPhase: animationPhase)
                }
            }
            .onAppear {
                generateParticles(for: geometry.size)
                startAnimation()
            }
        }
    }
    
    // MARK: - Weather Type
    
    private enum WeatherType {
        case clear, cloudy, rain, snow, thunder, fog
    }
    
    private var weatherType: WeatherType {
        switch weatherCode {
        case 0: return .clear
        case 1, 2, 3: return .cloudy
        case 45, 48: return .fog
        case 51...67, 80...82: return .rain
        case 71...77, 85, 86: return .snow
        case 95...99: return .thunder
        default: return .cloudy
        }
    }
    
    // MARK: - Background Gradient
    
    private var backgroundGradient: LinearGradient {
        let colors: [Color]
        
        switch weatherType {
        case .clear:
            colors = isDay 
                ? [Color(red: 0.4, green: 0.7, blue: 1.0), Color(red: 0.2, green: 0.5, blue: 0.9)]
                : [Color(red: 0.1, green: 0.1, blue: 0.3), Color(red: 0.05, green: 0.05, blue: 0.15)]
        case .cloudy:
            colors = isDay
                ? [Color(red: 0.6, green: 0.7, blue: 0.8), Color(red: 0.5, green: 0.6, blue: 0.7)]
                : [Color(red: 0.2, green: 0.2, blue: 0.3), Color(red: 0.1, green: 0.1, blue: 0.2)]
        case .rain:
            colors = [Color(red: 0.3, green: 0.4, blue: 0.5), Color(red: 0.2, green: 0.3, blue: 0.4)]
        case .snow:
            colors = isDay
                ? [Color(red: 0.8, green: 0.85, blue: 0.9), Color(red: 0.7, green: 0.75, blue: 0.85)]
                : [Color(red: 0.3, green: 0.35, blue: 0.4), Color(red: 0.2, green: 0.25, blue: 0.3)]
        case .thunder:
            colors = [Color(red: 0.2, green: 0.2, blue: 0.35), Color(red: 0.1, green: 0.1, blue: 0.2)]
        case .fog:
            colors = [Color(red: 0.6, green: 0.6, blue: 0.65), Color(red: 0.5, green: 0.5, blue: 0.55)]
        }
        
        return LinearGradient(colors: colors, startPoint: .top, endPoint: .bottom)
    }
    
    // MARK: - Particle Generation
    
    private func generateParticles(for size: CGSize) {
        let count: Int
        switch weatherType {
        case .rain: count = 100
        case .snow: count = 60
        default: count = 0
        }
        
        particles = (0..<count).map { _ in
            WeatherParticle(
                x: CGFloat.random(in: 0...1),
                y: CGFloat.random(in: -0.2...1),
                speed: CGFloat.random(in: 0.3...1.0),
                size: CGFloat.random(in: 4...12),
                opacity: Double.random(in: 0.3...0.8)
            )
        }
    }
    
    private func startAnimation() {
        withAnimation(.linear(duration: 2).repeatForever(autoreverses: false)) {
            animationPhase = 1
        }
    }
    
    // MARK: - Drawing
    
    private func drawRainDrop(context: GraphicsContext, at point: CGPoint, opacity: Double) {
        var path = Path()
        path.move(to: point)
        path.addLine(to: CGPoint(x: point.x, y: point.y + 15))
        
        context.stroke(
            path,
            with: .color(.white.opacity(opacity)),
            lineWidth: 1.5
        )
    }
    
    private func drawSnowflake(context: GraphicsContext, at point: CGPoint, size: CGFloat, opacity: Double) {
        let rect = CGRect(x: point.x - size/2, y: point.y - size/2, width: size, height: size)
        context.fill(
            Circle().path(in: rect),
            with: .color(.white.opacity(opacity))
        )
    }
}

// MARK: - Particle Model

struct WeatherParticle: Identifiable {
    let id = UUID()
    var x: CGFloat
    var y: CGFloat
    var speed: CGFloat
    var size: CGFloat
    var opacity: Double
}

// MARK: - Clouds Overlay

struct CloudsOverlay: View {
    let animationPhase: CGFloat
    
    var body: some View {
        GeometryReader { geometry in
            ForEach(0..<5) { index in
                CloudShape()
                    .fill(.white.opacity(0.3))
                    .frame(width: 150 + CGFloat(index * 30), height: 60 + CGFloat(index * 10))
                    .offset(
                        x: CGFloat(index * 80) - 50 + animationPhase * 30,
                        y: CGFloat(index * 40) + 50
                    )
            }
        }
    }
}

struct CloudShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        
        let width = rect.width
        let height = rect.height
        
        path.addEllipse(in: CGRect(x: 0, y: height * 0.3, width: width * 0.4, height: height * 0.7))
        path.addEllipse(in: CGRect(x: width * 0.2, y: 0, width: width * 0.5, height: height * 0.8))
        path.addEllipse(in: CGRect(x: width * 0.5, y: height * 0.2, width: width * 0.5, height: height * 0.8))
        
        return path
    }
}

// MARK: - Lightning Overlay

struct LightningOverlay: View {
    @State private var flashOpacity: Double = 0
    
    var body: some View {
        Color.white
            .opacity(flashOpacity)
            .ignoresSafeArea()
            .onAppear {
                triggerLightning()
            }
    }
    
    private func triggerLightning() {
        Timer.scheduledTimer(withTimeInterval: Double.random(in: 3...8), repeats: true) { _ in
            withAnimation(.easeIn(duration: 0.1)) {
                flashOpacity = 0.8
            }
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                withAnimation(.easeOut(duration: 0.2)) {
                    flashOpacity = 0
                }
            }
        }
    }
}

// MARK: - Sun Rays Overlay

struct SunRaysOverlay: View {
    let animationPhase: CGFloat
    
    var body: some View {
        GeometryReader { geometry in
            ForEach(0..<8) { index in
                Rectangle()
                    .fill(
                        LinearGradient(
                            colors: [.yellow.opacity(0.3), .clear],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .frame(width: 4, height: geometry.size.height * 0.4)
                    .rotationEffect(.degrees(Double(index) * 45 + Double(animationPhase) * 10))
                    .position(x: 60, y: 80)
            }
        }
    }
}

// MARK: - Preview

#Preview("Clear Day") {
    WeatherBackground(weatherCode: 0, isDay: true)
}

#Preview("Rain") {
    WeatherBackground(weatherCode: 61, isDay: true)
}

#Preview("Snow") {
    WeatherBackground(weatherCode: 73, isDay: true)
}

#Preview("Thunder") {
    WeatherBackground(weatherCode: 95, isDay: false)
}
