import Foundation

public enum RoutingMode: String, CaseIterable, Codable, Hashable, Sendable {
    case walking, cycling, driving, straight
    public var title: String {
        switch self {
        case .walking: return "步行"
        case .cycling: return "骑行"
        case .driving: return "驾车"
        case .straight: return "直线"
        }
    }
}

public struct Waypoint: Identifiable, Codable, Equatable, Sendable {
    public let id: UUID
    public var name: String
    public var point: GeoPoint
    public init(id: UUID = UUID(), name: String = "地图选点", point: GeoPoint) {
        self.id = id; self.name = name; self.point = point
    }
}

public enum DraftError: LocalizedError {
    case tooManyPoints, missingStart, missingEndpoints, missingPoint
    public var errorDescription: String? {
        switch self {
        case .tooManyPoints: return "首版最多选择 8 个路线点，请先删除一个点。"
        case .missingStart: return "请先设置起点。"
        case .missingEndpoints: return "请先设置起点和终点。"
        case .missingPoint: return "这个路线点已不存在。"
        }
    }
}

public struct RouteDraft: Sendable {
    public private(set) var waypoints: [Waypoint]
    public init(waypoints: [Waypoint] = []) throws {
        guard waypoints.count <= 8 else { throw DraftError.tooManyPoints }
        self.waypoints = waypoints
    }
    public mutating func append(_ point: GeoPoint, name: String = "地图选点") throws {
        guard waypoints.count < 8 else { throw DraftError.tooManyPoints }
        waypoints.append(Waypoint(name: name, point: point))
    }
    public mutating func setStart(_ point: GeoPoint, name: String) {
        if waypoints.isEmpty { waypoints.append(Waypoint(name: name, point: point)) }
        else { waypoints[0].point = point; waypoints[0].name = name }
    }
    public mutating func setEnd(_ point: GeoPoint, name: String) throws {
        guard !waypoints.isEmpty else { throw DraftError.missingStart }
        if waypoints.count == 1 { try append(point, name: name) }
        else {
            let index = waypoints.count - 1
            waypoints[index].point = point; waypoints[index].name = name
        }
    }
    public mutating func insertVia(_ point: GeoPoint, name: String) throws {
        guard waypoints.count >= 2 else { throw DraftError.missingEndpoints }
        guard waypoints.count < 8 else { throw DraftError.tooManyPoints }
        waypoints.insert(Waypoint(name: name, point: point), at: waypoints.count - 1)
    }
    public mutating func update(id: UUID, point: GeoPoint) throws {
        guard let index = waypoints.firstIndex(where: { $0.id == id }) else { throw DraftError.missingPoint }
        waypoints[index].point = point
    }
    public mutating func remove(id: UUID) { waypoints.removeAll { $0.id == id } }
    public mutating func move(from: Int, to: Int) throws {
        guard waypoints.indices.contains(from), waypoints.indices.contains(to) else { throw DraftError.missingPoint }
        let point = waypoints.remove(at: from)
        waypoints.insert(point, at: to)
    }
    public func role(at index: Int) -> String {
        if index == 0 { return "起点" }
        if index == waypoints.count - 1 { return "终点" }
        return "途经点 \(index)"
    }
}

public struct SavedRoute: Identifiable, Codable, Sendable {
    public let id: UUID
    public var name: String
    public let createdAt: Date
    public let mode: RoutingMode
    public let waypoints: [Waypoint]
    public let geometry: [GeoPoint]
    public init(name: String, mode: RoutingMode, waypoints: [Waypoint], geometry: [GeoPoint]) throws {
        _ = try RoutePath(points: geometry)
        _ = try RouteDraft(waypoints: waypoints)
        id = UUID(); self.name = name; createdAt = Date()
        self.mode = mode; self.waypoints = waypoints; self.geometry = geometry
    }
}
