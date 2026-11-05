import CryptoKit
import Foundation

private func setupChecked(_ action: (UnsafeMutablePointer<CChar>, Int) -> Int32) throws {
    var buffer = [CChar](repeating: 0, count: 512)
    let code = buffer.withUnsafeMutableBufferPointer { action($0.baseAddress!, $0.count) }
    if code != 0 { throw ProbeFailure(message: String(cString: buffer)) }
}

private func pairingPINCallback(_ pin: UnsafePointer<CChar>?, _ context: UnsafeMutableRawPointer?) {
    guard let pin, let context else { return }
    Unmanaged<NativePairingJob>.fromOpaque(context).takeUnretainedValue().onPIN(String(cString: pin))
}

/// The worker retains this object until accept returns; cancellation never frees a live handle.
final class NativePairingJob: @unchecked Sendable {
    private let handle: OpaquePointer
    private let queue = DispatchQueue(label: "net.routeforge.setup.pairing", qos: .userInitiated)
    let onPIN: (String) -> Void
    let serviceName: String
    let port: UInt16
    let txtRecord: Data

    init(onPIN: @escaping (String) -> Void) throws {
        var value: OpaquePointer?
        try setupChecked { rf_pair_host_create(&value, $0, $1) }
        guard let value else { throw ProbeFailure(message: "无法创建配对会话。") }
        self.handle = value
        self.onPIN = onPIN
        guard let name = rf_pair_host_service_name(value) else {
            rf_pair_host_free(value)
            throw ProbeFailure(message: "配对服务信息缺失。")
        }
        serviceName = String(cString: name)
        port = rf_pair_host_port(value)
        var length = 0
        guard let bytes = rf_pair_host_txt(value, &length), length > 0, length < 65_536 else {
            rf_pair_host_free(value)
            throw ProbeFailure(message: "配对广播信息缺失。")
        }
        let dictionary: [String: Any]
        do {
            guard let decoded = try PropertyListSerialization.propertyList(
                from: Data(bytes: bytes, count: length), options: [], format: nil) as? [String: Any] else {
                throw ProbeFailure(message: "无法读取配对广播信息。")
            }
            dictionary = decoded
            var records: [String: Data] = [:]
            for (key, entry) in dictionary {
                if let data = entry as? Data { records[key] = data }
                else if let text = entry as? String { records[key] = Data(text.utf8) }
                else { throw ProbeFailure(message: "配对广播格式不受支持。") }
            }
            txtRecord = NetService.data(fromTXTRecord: records)
        } catch {
            rf_pair_host_free(value)
            throw error
        }
    }

    deinit { rf_pair_host_free(handle) }
    func cancel() { rf_pair_host_cancel(handle) }
    func accept(into url: URL) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            queue.async { [self] in
                do {
                    try url.path.withCString { path in
                        try setupChecked { rf_pair_host_accept(handle, path, pairingPINCallback,
                            Unmanaged.passUnretained(self).toOpaque(), $0, $1) }
                    }
                    continuation.resume()
                } catch { continuation.resume(throwing: error) }
            }
        }
    }
}

enum BundledSetupAssets {
    private struct Lock: Decodable { let build: String; let files: [File] }
    private struct File: Decodable { let name: String; let bytes: Int; let sha256: String }
    static func validatedRestoreURL() throws -> URL {
        guard let lockURL = Bundle.main.url(forResource: "DeviceSupportLock", withExtension: "json"),
              let resources = Bundle.main.resourceURL else {
            throw ProbeFailure(message: "安装包缺少初始化资料，请重新安装完整版本。")
        }
        let lock = try JSONDecoder().decode(Lock.self, from: Data(contentsOf: lockURL))
        let expected: Set<String> = ["BuildManifest.plist", "Image.dmg", "Image.dmg.trustcache",
                                     "Image.dmg.cryptex_info", "Image.dmg.root_hash"]
        guard lock.files.count == expected.count, Set(lock.files.map(\.name)) == expected else {
            throw ProbeFailure(message: "初始化文件清单无效。")
        }
        let restore = resources.appendingPathComponent("DeviceSupport/Restore", isDirectory: true)
        for file in lock.files {
            guard (1...64_000_000).contains(file.bytes), file.sha256.count == 64 else {
                throw ProbeFailure(message: "初始化文件清单无效。")
            }
            let data = try Data(contentsOf: restore.appendingPathComponent(file.name), options: .mappedIfSafe)
            let digest = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
            guard data.count == file.bytes, digest == file.sha256 else {
                throw ProbeFailure(message: "初始化文件校验未通过，请重新安装完整版本。")
            }
        }
        let manifest = try PropertyListSerialization.propertyList(
            from: Data(contentsOf: restore.appendingPathComponent("BuildManifest.plist")), options: [], format: nil) as? [String: Any]
        guard manifest?["ProductBuildVersion"] as? String == lock.build else {
            throw ProbeFailure(message: "初始化文件版本不一致。")
        }
        return restore
    }
}

final class NativeDeviceSetupClient: @unchecked Sendable {
    private let queue = DispatchQueue(label: "net.routeforge.setup.services", qos: .userInitiated)
    private func perform<T>(_ action: @escaping () throws -> T) async throws -> T {
        try await withCheckedThrowingContinuation { continuation in
            queue.async {
                do { continuation.resume(returning: try action()) }
                catch { continuation.resume(throwing: error) }
            }
        }
    }
    private func probe(_ configuration: ConnectionConfiguration) throws -> Bool {
        var ready: Int32 = 0
        try configuration.pairingURL.path.withCString { path in
            try setupChecked { rf_setup_probe(configuration.port, path, &ready, $0, $1) }
        }
        return ready != 0
    }
    func check(_ configuration: ConnectionConfiguration) async throws -> Bool {
        try await perform { try self.probe(configuration) }
    }
    func initialize(_ configuration: ConnectionConfiguration) async throws {
        try await perform {
            let restore = try BundledSetupAssets.validatedRestoreURL()
            try configuration.pairingURL.path.withCString { pairing in
                try restore.path.withCString { path in
                    try setupChecked { rf_setup_initialize(configuration.port, pairing, path, $0, $1) }
                }
            }
            // Open a fresh tunnel: the service list from before installation is stale.
            for attempt in 0..<4 {
                if try self.probe(configuration) { return }
                if attempt < 3 { Thread.sleep(forTimeInterval: 1) }
            }
            throw ProbeFailure(message: "初始化已完成，定位服务暂未就绪。请保持 Wi-Fi 和 VPN 连接，稍后再检查。")
        }
    }
}
