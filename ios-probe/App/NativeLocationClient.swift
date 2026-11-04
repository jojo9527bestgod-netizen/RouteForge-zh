import Foundation
import RouteCore

struct ConnectionConfiguration: Sendable {
    let port: UInt16
    let pairingURL: URL
}

struct ProbeFailure: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// The native library is synchronous; one queue owns every handle and cleanup.
final class NativeLocationClient: @unchecked Sendable {
    private let queue = DispatchQueue(label: "net.routeforge.location", qos: .userInitiated)
    private var session: OpaquePointer?

    private func perform<T>(_ action: @escaping () throws -> T) async throws -> T {
        try await withCheckedThrowingContinuation { continuation in
            queue.async {
                do { continuation.resume(returning: try action()) }
                catch { continuation.resume(throwing: error) }
            }
        }
    }

    private func checked(_ action: (UnsafeMutablePointer<CChar>, Int) -> Int32) throws {
        var buffer = [CChar](repeating: 0, count: 512)
        let code = buffer.withUnsafeMutableBufferPointer { action($0.baseAddress!, $0.count) }
        if code != 0 { throw ProbeFailure(message: String(cString: buffer)) }
    }

    private func openOnQueue(_ configuration: ConnectionConfiguration) throws {
        rf_session_close(session)
        session = nil
        try configuration.pairingURL.path.withCString { path in
            try checked { rf_session_open(configuration.port, path, &session, $0, $1) }
        }
    }

    func validatePairing(at url: URL) async throws {
        try await perform {
            try url.path.withCString { path in
                try self.checked { rf_pairing_validate(path, $0, $1) }
            }
        }
    }

    func connect(_ configuration: ConnectionConfiguration) async throws {
        try await perform { try self.openOnQueue(configuration) }
    }

    func set(_ point: GeoPoint) async throws {
        try await perform {
            try self.checked { rf_session_set(self.session, point.latitude, point.longitude, $0, $1) }
        }
    }

    func clear(_ configuration: ConnectionConfiguration) async throws {
        try await perform {
            defer {
                rf_session_close(self.session)
                self.session = nil
            }
            if self.session == nil { try self.openOnQueue(configuration) }
            do {
                try self.checked { rf_session_clear(self.session, $0, $1) }
            } catch {
                // A failed DVT stream may be unusable. Retry clear on a fresh connection.
                try self.openOnQueue(configuration)
                try self.checked { rf_session_clear(self.session, $0, $1) }
            }
            // The stop selector is one-way. Give it time to reach the device before
            // releasing the DVT channel, as in the verified desktop cleanup path.
            Thread.sleep(forTimeInterval: 1)
        }
    }
}
