import SwiftUI
import UniformTypeIdentifiers

@MainActor
struct ContentView: View {
    @ObservedObject var model: ProbeModel
    @StateObject private var planner: RoutePlannerModel
    @StateObject private var library = RouteLibrary()
    @Environment(\.scenePhase) private var scenePhase
    @State private var tab: AppTab
    @State private var showImporter = false
    @State private var importKind = ImportKind.route
    enum AppTab: Hashable { case map, routes, settings }

    init(model: ProbeModel) {
        self.model = model
        _planner = StateObject(wrappedValue: RoutePlannerModel(playback: model))
        _tab = State(initialValue: model.pairingPresent ? .map : .settings)
    }
    var body: some View {
        TabView(selection: $tab) {
            MapScreen(model: model, planner: planner, library: library, onSetup: { tab = .settings })
                .tabItem { Label("地图", systemImage: "map") }.tag(AppTab.map)
            RouteLibraryScreen(model: model, planner: planner, library: library,
                               onOpen: { tab = .map }, onImport: { openImporter(.route) })
                .tabItem { Label("路线", systemImage: "bookmark") }.tag(AppTab.routes)
            ConnectionSettingsScreen(model: model, onImport: { openImporter(.pairing) })
                .tabItem { Label("设置", systemImage: "gearshape") }.tag(AppTab.settings)
        }
        .tint(.blue)
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.data], allowsMultipleSelection: false) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                let kind = importKind
                Task {
                    if await model.importFile(url, kind: kind), case .route = kind {
                        planner.adoptImportedRoute(); tab = .map
                    }
                }
            case .failure(let error): model.errorMessage = error.localizedDescription
            }
        }
        .alert("操作未完成", isPresented: Binding(
            get: { model.errorMessage != nil || planner.errorMessage != nil },
            set: { if !$0 { model.errorMessage = nil; planner.errorMessage = nil } }
        )) {
            Button("知道了", role: .cancel) { model.errorMessage = nil; planner.errorMessage = nil }
        } message: { Text(model.errorMessage ?? planner.errorMessage ?? "") }
        .onAppear { model.setForeground(scenePhase == .active) }
        .onChange(of: scenePhase) { _, phase in model.setForeground(phase == .active) }
        .onOpenURL { url in
            if url.scheme == "routeforge" { tab = .settings }
        }
    }
    private func openImporter(_ kind: ImportKind) { importKind = kind; showImporter = true }
}
