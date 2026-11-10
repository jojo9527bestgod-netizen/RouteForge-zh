import XCTest
@testable import RouteCore

final class NativeRouteTests: XCTestCase {
    private func path() throws -> RoutePath {
        try RoutePath(points: [GeoPoint(latitude: 31, longitude: 121),
                               GeoPoint(latitude: 31.001, longitude: 121),
                               GeoPoint(latitude: 31.001, longitude: 121.001)])
    }

    func testResumePreservesStreetCornerInsteadOfShortcut() throws {
        let path = try path()
        let section = path.section(from: 20, to: path.length)
        XCTAssertEqual(section.first, path.position(at: 20))
        XCTAssertEqual(section[1], path.points[1])
        XCTAssertEqual(section.last, path.points.last)
        XCTAssertEqual(path.section(from: path.length, to: 20), Array(section.reversed()))
    }

    func testLoopRestartsAndReturnLegReversesAtExactEndpoint() throws {
        let path = try path()
        let loop = try NativeRouteLeg(path: path, traveled: path.length, mode: .loop)
        XCTAssertEqual(loop.points, path.points)
        XCTAssertEqual(loop.boundary, path.length * 2)
        let reverse = try NativeRouteLeg(path: path, traveled: path.length, mode: .backAndForth)
        XCTAssertEqual(reverse.points, Array(path.points.reversed()))
        let resumed = try NativeRouteLeg(path: path, traveled: path.length + 25, mode: .backAndForth)
        XCTAssertEqual(resumed.points.first, path.position(at: path.length - 25))
        XCTAssertEqual(resumed.points.last, path.points.first)
    }

    func testCompletedSingleRouteIsNotReplayed() throws {
        let path = try path()
        let done = try NativeRouteLeg(path: path, traveled: path.length + 100, mode: .once)
        XCTAssertEqual(done.points, [path.points.last!])
        XCTAssertEqual(done.boundary, path.length)
        XCTAssertThrowsError(try NativeRouteLeg(path: path, traveled: .infinity, mode: .loop))
        XCTAssertThrowsError(try NativeRouteLeg(path: path, traveled: -1, mode: .once))
    }
}
