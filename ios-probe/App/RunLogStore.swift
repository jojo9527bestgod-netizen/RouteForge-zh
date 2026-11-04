import Foundation

/// Private diagnostic text contains only app events and simulated distances.
final class RunLogStore: @unchecked Sendable {
    private let queue = DispatchQueue(label: "net.routeforge.run-log", qos: .utility)
    private let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("RunLogs", isDirectory: true)

    init() {
        let directory = directory
        queue.async {
            let current = directory.appendingPathComponent("current.txt")
            let previous = directory.appendingPathComponent("previous.txt")
            if let contents = try? Data(contentsOf: current) {
                try? contents.write(to: previous, options: .atomic)
            }
        }
    }
    func write(_ text: String) {
        let directory = directory
        queue.async {
            do {
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                var privateDirectory = directory
                var values = URLResourceValues(); values.isExcludedFromBackup = true
                try privateDirectory.setResourceValues(values)
                let file = directory.appendingPathComponent("current.txt")
                try Data(text.utf8).write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            } catch {
                // Diagnostic persistence must never interrupt location cleanup.
            }
        }
    }
}
