import Foundation

@MainActor
final class PairingStore {
    private let directory: URL
    let url: URL
    var exists: Bool { FileManager.default.fileExists(atPath: url.path) }

    init() {
        directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Pairing", isDirectory: true)
        url = directory.appendingPathComponent("pairingFile.plist")
    }

    func generatedTemporaryURL() throws -> URL {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
        var privateDirectory = directory
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try privateDirectory.setResourceValues(values)
        return directory.appendingPathComponent(UUID().uuidString + ".pending.plist")
    }

    func importFile(_ source: URL, client: NativeLocationClient) async throws {
        let accessing = source.startAccessingSecurityScopedResource()
        defer { if accessing { source.stopAccessingSecurityScopedResource() } }
        let size = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size <= 1_000_000 else { throw ProbeFailure(message: "配对文件过大，请选择原始 plist 文件。") }
        let data = try Data(contentsOf: source)
        guard !data.isEmpty, data.count <= 1_000_000 else {
            throw ProbeFailure(message: "配对文件为空或过大。")
        }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var privateDirectory = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try privateDirectory.setResourceValues(values)
        let temporary = directory.appendingPathComponent(UUID().uuidString + ".plist")
        defer { try? FileManager.default.removeItem(at: temporary) }
        try data.write(to: temporary, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        try await client.validatePairing(at: temporary)
        try data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}
