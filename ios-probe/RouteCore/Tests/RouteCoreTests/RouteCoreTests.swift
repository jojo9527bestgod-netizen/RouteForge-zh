import XCTest
@testable import RouteCore

final class RouteCoreTests: XCTestCase {
    func testRejectsNonFiniteAndOutOfRangeCoordinates() {
        XCTAssertThrowsError(try GeoPoint(latitude: .nan, longitude: 0))
        XCTAssertThrowsError(try GeoPoint(latitude: 0, longitude: .infinity))
        XCTAssertThrowsError(try GeoPoint(latitude: 90.01, longitude: 0))
        XCTAssertThrowsError(try GeoPoint(latitude: 0, longitude: -180.01))
    }
    func testDistanceAlongEquatorAndEndpointClamping() throws {
        let start = try GeoPoint(latitude: 0, longitude: 0)
        let end = try GeoPoint(latitude: 0, longitude: 1)
        let path = try RoutePath(points: [start, end])
        XCTAssertEqual(path.length, 111_195, accuracy: 2)
        XCTAssertEqual(path.position(at: -5), start)
        XCTAssertEqual(path.position(at: path.length + 50), end)
        let halfway = path.position(at: path.length / 2)
        XCTAssertEqual(halfway.longitude, 0.5, accuracy: 0.000001)
    }
    func testInterpolationTakesShortPathAcrossDateLine() throws {
        let path = try RoutePath(points: [
            GeoPoint(latitude: 0, longitude: 179), GeoPoint(latitude: 0, longitude: -179)
        ])
        XCTAssertEqual(path.length, 222_390, accuracy: 3)
        XCTAssertEqual(abs(path.position(at: path.length / 2).longitude), 180, accuracy: 0.00001)
    }
    func testConsecutiveDuplicatesRemovedButLoopsPreserved() throws {
        let a = try GeoPoint(latitude: 48, longitude: 2)
        let b = try GeoPoint(latitude: 48.01, longitude: 2)
        let path = try RoutePath(points: [a, a, b, a, b, b, a])
        XCTAssertEqual(path.points, [a, b, a, b, a])
        XCTAssertEqual(path.length, a.distance(to: b) * 4, accuracy: 0.01)
    }
    func testRejectsZeroLengthRoute() throws {
        let a = try GeoPoint(latitude: 0, longitude: 0)
        XCTAssertThrowsError(try RoutePath(points: [a, a]))
    }
    func testGPXNamespaceAndInvalidPoint() throws {
        let valid = "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg><trkpt lat=\"48\" lon=\"2\"/><trkpt lat=\"48.1\" lon=\"2\"/></trkseg></trk></gpx>"
        XCTAssertEqual(try RouteImport.parse(Data(valid.utf8)).points.count, 2)
        let invalid = valid.replacingOccurrences(of: "48.1", with: "200")
        XCTAssertThrowsError(try RouteImport.parse(Data(invalid.utf8)))
        XCTAssertThrowsError(try RouteImport.parse(Data("<gpx><trkpt".utf8)))
    }
    func testCoordinateTextIsStrictAndSupportsHeader() throws {
        let text = "latitude,longitude\n48,2\n48.1,2\n"
        XCTAssertEqual(try RouteImport.parse(Data(text.utf8)).points.count, 2)
        XCTAssertThrowsError(try RouteImport.parse(Data("48,2\nnot-a-point\n48.1,2".utf8)))
    }
}
