import Foundation

public enum CoordinateQuery {
    public static func parse(_ query: String) throws -> GeoPoint? {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "，", with: ",")
            .replacingOccurrences(of: "（", with: "(")
            .replacingOccurrences(of: "）", with: ")")
        let parenthesized = text.hasPrefix("(") && text.hasSuffix(")")
        let body = parenthesized ? String(text.dropFirst().dropLast()) : text
        let parts = body.split(separator: ",", omittingEmptySubsequences: false)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        guard parts.count == 2, let first = Double(parts[0]), let second = Double(parts[1]) else { return nil }
        if parenthesized, let lon = Int64(parts[0]), let lat = Int64(parts[1]) {
            return try GeoPoint(latitude: Double(lat) / 100_000, longitude: Double(lon) / 100_000)
        }
        return try GeoPoint(latitude: first, longitude: second)
    }
}
