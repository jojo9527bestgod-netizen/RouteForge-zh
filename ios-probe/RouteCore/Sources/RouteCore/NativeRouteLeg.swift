import Foundation

/// A monotonic leg for the system's native route player. Only a completed leg,
/// speed change or pause/resume sends another route; UI ticks never inject fixes.
public struct NativeRouteLeg: Sendable {
    public let points: [GeoPoint]
    public let boundary: Double

    public init(path: RoutePath, traveled: Double, mode: PlaybackMode) throws {
        guard traveled.isFinite, traveled >= 0 else { throw RouteError.invalidCoordinate }
        let lap = floor(traveled / path.length)
        let offset = traveled.truncatingRemainder(dividingBy: path.length)
        let reverse = mode == .backAndForth && lap.truncatingRemainder(dividingBy: 2) == 1
        let start = mode == .once ? min(traveled, path.length) : (reverse ? path.length - offset : offset)
        let end = reverse ? 0 : path.length
        self.points = path.section(from: start, to: end)
        self.boundary = mode == .once ? path.length : (lap + 1) * path.length
    }
}
