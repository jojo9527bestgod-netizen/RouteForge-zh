import Combine
import Foundation
import RouteCore

@MainActor
final class RouteLibrary: ObservableObject {
    @Published private(set) var routes: [SavedRoute] = []
    @Published private(set) var readWarning: String?
    private let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("SavedRoutes", isDirectory: true)
    private let encoder: JSONEncoder = {
        let value = JSONEncoder(); value.dateEncodingStrategy = .iso8601; return value
    }()
    private let decoder: JSONDecoder = {
        let value = JSONDecoder(); value.dateDecodingStrategy = .iso8601; return value
    }()

    init() { reload() }
    func reload() {
        routes = []; readWarning = nil
        guard FileManager.default.fileExists(atPath: directory.path) else { return }
        do {
            let files = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.fileSizeKey])
            for file in files where file.pathExtension == "json" {
                do {
                    guard (try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0) <= 5_000_000 else {
                        throw RouteError.invalidFile
                    }
                    let route = try decoder.decode(SavedRoute.self, from: Data(contentsOf: file))
                    _ = try RoutePath(points: route.geometry)
                    _ = try RouteDraft(waypoints: route.waypoints)
                    guard file.deletingPathExtension().lastPathComponent == route.id.uuidString else { throw RouteError.invalidFile }
                    routes.append(route)
                } catch { readWarning = "部分收藏文件无法读取，原文件已保留。" }
            }
            routes.sort { $0.createdAt > $1.createdAt }
        } catch { readWarning = "暂时无法读取收藏：\(error.localizedDescription)" }
    }
    func save(_ route: SavedRoute) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var privateDirectory = directory
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try privateDirectory.setResourceValues(values)
        let data = try encoder.encode(route)
        guard data.count <= 5_000_000 else { throw RouteError.tooManyPoints }
        try data.write(to: directory.appendingPathComponent(route.id.uuidString + ".json"), options: .atomic)
        reload()
    }
    func delete(_ route: SavedRoute) throws {
        try FileManager.default.removeItem(at: directory.appendingPathComponent(route.id.uuidString + ".json"))
        routes.removeAll { $0.id == route.id }
    }
}
