import Foundation
#if canImport(FoundationXML)
import FoundationXML
#endif

public enum RouteImport {
    public static func parse(_ data: Data) throws -> RoutePath {
        guard !data.isEmpty, data.count <= 5_000_000 else { throw RouteError.invalidFile }
        guard let text = String(data: data, encoding: .utf8) else { throw RouteError.invalidFile }
        if text.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("<") {
            let delegate = GPXReader()
            let parser = XMLParser(data: data)
            parser.shouldProcessNamespaces = true
            parser.shouldResolveExternalEntities = false
            parser.delegate = delegate
            guard parser.parse(), !delegate.invalid else { throw RouteError.invalidFile }
            return try RoutePath(points: delegate.points)
        }
        var points: [GeoPoint] = []
        for raw in text.split(whereSeparator: \.isNewline) {
            let fields = raw.split(whereSeparator: { $0 == "," || $0 == ";" || $0.isWhitespace })
            guard !fields.isEmpty else { continue }
            if points.isEmpty, fields.count == 2,
               ["lat", "latitude"].contains(fields[0].lowercased()),
               ["lon", "lng", "longitude"].contains(fields[1].lowercased()) { continue }
            guard fields.count == 2, let lat = Double(fields[0]), let lon = Double(fields[1])
            else { throw RouteError.invalidFile }
            points.append(try GeoPoint(latitude: lat, longitude: lon))
            guard points.count <= 50_000 else { throw RouteError.tooManyPoints }
        }
        return try RoutePath(points: points)
    }
}

private final class GPXReader: NSObject, XMLParserDelegate {
    var points: [GeoPoint] = []
    var invalid = false
    func parser(_ parser: XMLParser, didStartElement elementName: String,
                namespaceURI: String?, qualifiedName qName: String?,
                attributes attributesDict: [String: String]) {
        guard ["trkpt", "rtept"].contains(elementName) else { return }
        guard points.count < 50_000,
              let latText = attributesDict["lat"], let lonText = attributesDict["lon"],
              let lat = Double(latText), let lon = Double(lonText),
              let point = try? GeoPoint(latitude: lat, longitude: lon) else {
            invalid = true
            parser.abortParsing()
            return
        }
        points.append(point)
    }
}
