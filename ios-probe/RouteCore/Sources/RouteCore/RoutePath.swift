import Foundation

public enum RouteError: LocalizedError {
    case invalidCoordinate, tooFewPoints, emptyRoute, invalidFile, tooManyPoints
    public var errorDescription: String? {
        switch self {
        case .invalidCoordinate: return "经纬度无效，纬度应为 -90～90，经度应为 -180～180。"
        case .tooFewPoints: return "路线至少需要两个不同的坐标。"
        case .emptyRoute: return "路线长度为零。"
        case .invalidFile: return "无法解析路线，请使用 GPX/XML 或每行一组纬度、经度的文本。"
        case .tooManyPoints: return "路线点数超过 50000，请先简化路线。"
        }
    }
}

public struct GeoPoint: Equatable, Sendable, Codable {
    public let latitude: Double
    public let longitude: Double
    public init(latitude: Double, longitude: Double) throws {
        guard latitude.isFinite, longitude.isFinite, abs(latitude) <= 90, abs(longitude) <= 180
        else { throw RouteError.invalidCoordinate }
        self.latitude = latitude
        self.longitude = longitude
    }
    private enum CodingKeys: String, CodingKey { case latitude, longitude }
    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        try self.init(latitude: values.decode(Double.self, forKey: .latitude),
                      longitude: values.decode(Double.self, forKey: .longitude))
    }
    public func distance(to other: GeoPoint) -> Double {
        let a = latitude * .pi / 180, b = other.latitude * .pi / 180
        let dlat = b - a, dlon = (other.longitude - longitude) * .pi / 180
        let h = pow(sin(dlat / 2), 2) + cos(a) * cos(b) * pow(sin(dlon / 2), 2)
        return 2 * 6_371_000 * asin(sqrt(min(1, max(0, h))))
    }
    fileprivate func interpolated(to other: GeoPoint, fraction: Double) -> GeoPoint {
        let t = min(1, max(0, fraction))
        var delta = other.longitude - longitude
        if delta > 180 { delta -= 360 }
        if delta < -180 { delta += 360 }
        var lon = longitude + delta * t
        if lon > 180 { lon -= 360 }
        if lon < -180 { lon += 360 }
        // Interpolation of validated endpoints stays within latitude bounds.
        return try! GeoPoint(latitude: latitude + (other.latitude - latitude) * t, longitude: lon)
    }
}

public struct RoutePath: Sendable {
    public let points: [GeoPoint]
    public let length: Double
    private let cumulative: [Double]
    public init(points: [GeoPoint]) throws {
        guard points.count <= 50_000 else { throw RouteError.tooManyPoints }
        var clean: [GeoPoint] = []
        for point in points where clean.last != point { clean.append(point) }
        guard clean.count > 1 else { throw RouteError.tooFewPoints }
        var distances = [0.0]
        for index in 1..<clean.count {
            distances.append(distances.last! + clean[index - 1].distance(to: clean[index]))
        }
        guard let total = distances.last, total > 0 else { throw RouteError.emptyRoute }
        self.points = clean
        self.length = total
        self.cumulative = distances
    }
    public func position(at distance: Double) -> GeoPoint {
        guard distance.isFinite, distance > 0 else { return points[0] }
        if distance >= length { return points[points.count - 1] }
        var low = 1, high = cumulative.count - 1
        while low < high {
            let mid = (low + high) / 2
            if cumulative[mid] < distance { low = mid + 1 } else { high = mid }
        }
        let start = low - 1
        let fraction = (distance - cumulative[start]) / (cumulative[low] - cumulative[start])
        return points[start].interpolated(to: points[low], fraction: fraction)
    }

    /// Keep the original bends and an interpolated starting point when resuming.
    public func section(from start: Double, to end: Double) -> [GeoPoint] {
        let low = max(0, min(length, min(start, end)))
        let high = max(0, min(length, max(start, end)))
        var result = [position(at: low)]
        for index in points.indices where cumulative[index] > low && cumulative[index] < high {
            result.append(points[index])
        }
        let finish = position(at: high)
        if result.last != finish { result.append(finish) }
        return start > end ? Array(result.reversed()) : result
    }
}
