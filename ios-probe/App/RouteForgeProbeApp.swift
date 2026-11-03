import SwiftUI

@main
@MainActor
struct RouteForgeProbeApp: App {
    @StateObject private var model = ProbeModel()
    var body: some Scene {
        WindowGroup { ContentView(model: model) }
    }
}
