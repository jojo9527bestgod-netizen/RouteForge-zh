import XCTest
@testable import RouteCore

final class PlanningTests: XCTestCase {
    private func point(_ longitude: Double) throws -> GeoPoint { try GeoPoint(latitude: 31, longitude: longitude) }
    func testViaInsertionPreservesEndpointsAndLimit() throws {
        let start = try point(121), end = try point(122)
        var draft = try RouteDraft()
        XCTAssertThrowsError(try draft.setEnd(end, name: "终"))
        XCTAssertThrowsError(try draft.insertVia(end, name: "途经"))
        draft.setStart(start, name: "起")
        try draft.setEnd(end, name: "终")
        for index in 1...6 { try draft.insertVia(point(121 + Double(index) / 10), name: "途经") }
        XCTAssertEqual(draft.waypoints.first?.point, start)
        XCTAssertEqual(draft.waypoints.last?.point, end)
        XCTAssertEqual(draft.waypoints.count, 8)
        XCTAssertThrowsError(try draft.append(end))
        XCTAssertThrowsError(try draft.insertVia(end, name: "多余"))
        XCTAssertEqual(draft.waypoints.count, 8)
    }
    func testMovingAndDeletingWaypointsUpdatesOrder() throws {
        var draft = try RouteDraft()
        for index in 0...2 { try draft.append(point(Double(index))) }
        let movedID = draft.waypoints[2].id
        try draft.move(from: 2, to: 0)
        XCTAssertEqual(draft.waypoints[0].id, movedID)
        XCTAssertEqual(draft.role(at: 0), "起点")
        try draft.update(id: movedID, point: point(8))
        XCTAssertEqual(draft.waypoints[0].point.longitude, 8)
        draft.remove(id: movedID)
        XCTAssertThrowsError(try draft.update(id: movedID, point: point(9)))
        XCTAssertThrowsError(try draft.move(from: 0, to: 5))
        XCTAssertEqual(draft.waypoints.map(\.point.longitude), [0, 1])
    }
    func testCoordinateSearchFormatsAndValidation() throws {
        XCTAssertEqual(try CoordinateQuery.parse("31.2304，121.4737"), try GeoPoint(latitude: 31.2304, longitude: 121.4737))
        XCTAssertEqual(try CoordinateQuery.parse("（12147370,3123040）"), try GeoPoint(latitude: 31.2304, longitude: 121.4737))
        XCTAssertNil(try CoordinateQuery.parse("上海人民广场"))
        XCTAssertThrowsError(try CoordinateQuery.parse("91,121"))
        XCTAssertThrowsError(try CoordinateQuery.parse("nan,0"))
    }
    func testPauseDoesNotAccumulateDistanceOrCatchUp() throws {
        var cursor = PlaybackCursor()
        try cursor.advance(seconds: 10, speedKmh: 36, paused: false)
        try cursor.advance(seconds: 60, speedKmh: 72, paused: true)
        XCTAssertEqual(cursor.distance, 100, accuracy: 0.00001)
        try cursor.advance(seconds: 5, speedKmh: 72, paused: false)
        XCTAssertEqual(cursor.distance, 200, accuracy: 0.00001)
        XCTAssertEqual(cursor.activeSeconds, 15)
        XCTAssertThrowsError(try cursor.advance(seconds: .infinity, speedKmh: 10, paused: false))
    }
    func testPlaybackModesHandleMultipleLapsAndEnd() throws {
        var cursor = PlaybackCursor()
        try cursor.advance(seconds: 35, speedKmh: 36, paused: false)
        XCTAssertEqual(cursor.sample(length: 100, mode: .once).distance, 100)
        XCTAssertTrue(cursor.sample(length: 100, mode: .once).complete)
        XCTAssertEqual(cursor.sample(length: 100, mode: .loop).distance, 50)
        XCTAssertFalse(cursor.sample(length: 100, mode: .loop).complete)
        XCTAssertEqual(cursor.sample(length: 100, mode: .backAndForth).distance, 50)
        try cursor.advance(seconds: 10, speedKmh: 36, paused: false)
        XCTAssertEqual(cursor.sample(length: 100, mode: .backAndForth).distance, 50)
    }
    func testSavedRouteRoundTripAndInvalidCoordinates() throws {
        let route = try SavedRoute(name: "本机路线", mode: .walking, waypoints: [], geometry: [point(121), point(122)])
        let data = try JSONEncoder().encode(route)
        let decoded = try JSONDecoder().decode(SavedRoute.self, from: data)
        XCTAssertEqual(decoded.id, route.id)
        XCTAssertEqual(decoded.geometry, route.geometry)
        XCTAssertThrowsError(try JSONDecoder().decode(GeoPoint.self, from: Data("{\"latitude\":100,\"longitude\":0}".utf8)))
    }
}
