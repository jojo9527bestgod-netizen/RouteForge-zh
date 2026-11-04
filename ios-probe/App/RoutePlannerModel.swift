import Combine
import CoreLocation
import MapKit
import RouteCore

struct PlaceResult: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
    let point: GeoPoint
}

enum PlaceRole { case start, via, end }

@MainActor
final class RoutePlannerModel: ObservableObject {
    @Published private(set) var draft = try! RouteDraft()
    @Published var mode = RoutingMode.walking
    @Published private(set) var planning = false
    @Published private(set) var planningProgress = ""
    @Published private(set) var searchResults: [PlaceResult] = []
    @Published private(set) var searching = false
    @Published var errorMessage: String?
    @Published var notice: String?
    @Published private(set) var fitRequest = 0
    @Published private(set) var locationRequest = 0
    @Published private(set) var focusRequest = 0
    private(set) var focusedPoint: GeoPoint?
    var searchRegion = MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: 48.85837, longitude: 2.29448), span: MKCoordinateSpan(latitudeDelta: 0.04, longitudeDelta: 0.04))
    private let playback: ProbeModel
    private var planningTask: Task<Void, Never>?
    private var searchTask: Task<Void, Never>?
    private var requestID = UUID()

    init(playback: ProbeModel) { self.playback = playback }
    var editable: Bool { !playback.running && !playback.busy }
    var canGenerate: Bool { editable && !planning && draft.waypoints.count >= 2 }
    var availableModes: [RoutingMode] {
        if #available(iOS 27.0, *) { return RoutingMode.allCases }
        return [.walking, .driving, .straight]
    }
    func fitRoute() { fitRequest += 1 }
    func locate() { locationRequest += 1 }
    func changeMode(_ value: RoutingMode) {
        guard editable else { return }
        cancelPlanning(); mode = value; playback.clearPlannedRoute(); notice = nil
    }
    func focus(_ point: GeoPoint) { focusedPoint = point; focusRequest += 1 }
    func cancelPlanning() {
        requestID = UUID(); planningTask?.cancel(); planningTask = nil
        planning = false; planningProgress = ""
    }
    private func edit(_ action: () throws -> Void) {
        guard editable else { return }
        do {
            try action(); cancelPlanning(); playback.clearPlannedRoute(); notice = nil
        } catch { errorMessage = error.localizedDescription }
    }
    func add(_ point: GeoPoint) { edit { try draft.append(point) } }
    func update(id: UUID, point: GeoPoint) { edit { try draft.update(id: id, point: point) } }
    func remove(id: UUID) { edit { draft.remove(id: id) } }
    func move(from: Int, to: Int) { edit { try draft.move(from: from, to: to) } }
    func undo() { if let last = draft.waypoints.last { remove(id: last.id) } }
    func clear() { edit { draft = try RouteDraft() } }
    func choose(_ result: PlaceResult, role: PlaceRole) {
        edit {
            switch role {
            case .start: draft.setStart(result.point, name: result.title)
            case .end: try draft.setEnd(result.point, name: result.title)
            case .via: try draft.insertVia(result.point, name: result.title)
            }
        }
        focus(result.point)
    }
    func adoptImportedRoute() {
        cancelPlanning(); draft = try! RouteDraft(); fitRoute()
    }
    func load(_ saved: SavedRoute) {
        guard editable else { return }
        do {
            cancelPlanning()
            draft = try RouteDraft(waypoints: saved.waypoints)
            mode = saved.mode
            playback.useRoute(try RoutePath(points: saved.geometry), name: saved.name)
            fitRoute()
        } catch { errorMessage = error.localizedDescription }
    }
    func save(name: String, to library: RouteLibrary) {
        guard let path = playback.route else { errorMessage = "先生成或导入一条路线。"; return }
        let title = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(60))
        guard !title.isEmpty else { errorMessage = "请填写路线名称。"; return }
        do {
            try library.save(SavedRoute(name: title, mode: mode, waypoints: draft.waypoints, geometry: path.points))
            notice = "已收藏，可在“路线”中再次使用。"
        } catch { errorMessage = error.localizedDescription }
    }

    func generate() {
        guard canGenerate else { return }
        cancelPlanning()
        let points = draft.waypoints
        let selectedMode = mode
        let token = UUID(); requestID = token
        planning = true; notice = nil
        planningTask = Task {
            defer {
                if requestID == token { planning = false; planningProgress = "" }
            }
            do {
                var geometry: [GeoPoint] = []
                if selectedMode == .straight {
                    geometry = points.map(\.point)
                } else {
                    for index in 0..<(points.count - 1) {
                        try Task.checkCancellation()
                        planningProgress = "正在规划第 \(index + 1) / \(points.count - 1) 段"
                        let leg = try await roadLeg(from: points[index].point, to: points[index + 1].point, mode: selectedMode)
                        for point in leg where geometry.last != point { geometry.append(point) }
                        guard geometry.count <= 50_000 else { throw RouteError.tooManyPoints }
                    }
                }
                try Task.checkCancellation()
                guard requestID == token else { return }
                let path = try RoutePath(points: geometry)
                playback.useRoute(path, name: "\(points.first!.name) → \(points.last!.name)")
                notice = selectedMode == .straight ? "直线轨迹已生成" : "沿道路路线已生成"
                fitRoute()
            } catch is CancellationError {
            } catch {
                guard requestID == token else { return }
                errorMessage = "路线生成失败：\(error.localizedDescription)\n可以修改选点或出行方式。"
            }
        }
    }

    private func roadLeg(from: GeoPoint, to: GeoPoint, mode: RoutingMode) async throws -> [GeoPoint] {
        let request = MKDirections.Request()
        request.source = MKMapItem(placemark: MKPlacemark(coordinate: from.coordinate))
        request.destination = MKMapItem(placemark: MKPlacemark(coordinate: to.coordinate))
        request.requestsAlternateRoutes = false
        switch mode {
        case .walking: request.transportType = .walking
        case .driving: request.transportType = .automobile
        case .cycling:
            if #available(iOS 27.0, *) { request.transportType = .cycling }
            else { throw ProbeFailure(message: "此版本的骑行规划需要 iOS 27。") }
        case .straight: return [from, to]
        }
        let directions = MKDirections(request: request)
        let response = try await withTaskCancellationHandler {
            try await directions.calculate()
        } onCancel: { directions.cancel() }
        guard let route = response.routes.first, route.polyline.pointCount >= 2 else {
            throw ProbeFailure(message: "所选地点之间没有可用道路。")
        }
        guard route.polyline.pointCount <= 50_000 else { throw RouteError.tooManyPoints }
        var coordinates = [CLLocationCoordinate2D](repeating: CLLocationCoordinate2D(), count: route.polyline.pointCount)
        route.polyline.getCoordinates(&coordinates, range: NSRange(location: 0, length: coordinates.count))
        return try coordinates.map { try GeoPoint(latitude: $0.latitude, longitude: $0.longitude) }
    }

    func search(_ query: String) {
        searchTask?.cancel()
        searchResults = []; searching = true
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        searchTask = Task {
            defer { if !Task.isCancelled { searching = false } }
            do {
                try Task.checkCancellation()
                guard !text.isEmpty else { searching = false; return }
                if let point = try CoordinateQuery.parse(text) {
                    searchResults = [PlaceResult(title: "坐标位置", subtitle: String(format: "%.6f, %.6f", point.latitude, point.longitude), point: point)]
                    return
                }
                let request = MKLocalSearch.Request()
                request.naturalLanguageQuery = text; request.region = searchRegion
                let search = MKLocalSearch(request: request)
                let response = try await withTaskCancellationHandler { try await search.start() } onCancel: { search.cancel() }
                try Task.checkCancellation()
                searchResults = response.mapItems.prefix(15).compactMap { item in
                    guard let point = try? GeoPoint(latitude: item.placemark.coordinate.latitude, longitude: item.placemark.coordinate.longitude) else { return nil }
                    return PlaceResult(title: item.name ?? "地点", subtitle: item.placemark.title ?? "", point: point)
                }
                if searchResults.isEmpty { notice = "没有找到地点，可补充城市名称或直接长按地图选点。" }
            } catch is CancellationError {
            } catch { errorMessage = "搜索失败：\(error.localizedDescription)" }
        }
    }
}

extension GeoPoint {
    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: latitude, longitude: longitude) }
}
