import Foundation

public enum PlaybackMode: String, CaseIterable, Codable, Hashable, Sendable {
    case once, loop, backAndForth
    public var title: String {
        switch self {
        case .once: return "单程"
        case .loop: return "循环"
        case .backAndForth: return "往返"
        }
    }
}

public struct PlaybackCursor: Sendable {
    public private(set) var distance = 0.0
    public private(set) var activeSeconds = 0.0
    public init() {}
    public mutating func advance(seconds: Double, speedKmh: Double, paused: Bool) throws {
        guard seconds.isFinite, seconds >= 0, speedKmh.isFinite, (0...150).contains(speedKmh)
        else { throw RouteError.invalidCoordinate }
        guard !paused else { return }
        let nextDistance = distance + seconds * speedKmh / 3.6
        let nextTime = activeSeconds + seconds
        guard nextDistance.isFinite, nextTime.isFinite else { throw RouteError.invalidCoordinate }
        distance = nextDistance; activeSeconds = nextTime
    }
    public func sample(length: Double, mode: PlaybackMode) -> (distance: Double, complete: Bool) {
        guard length.isFinite, length > 0 else { return (0, true) }
        switch mode {
        case .once: return (min(distance, length), distance >= length)
        case .loop: return (distance.truncatingRemainder(dividingBy: length), false)
        case .backAndForth:
            let phase = distance.truncatingRemainder(dividingBy: length * 2)
            return (phase <= length ? phase : length * 2 - phase, false)
        }
    }
}
