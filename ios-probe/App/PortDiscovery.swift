import Foundation
import Combine

@MainActor
final class PortDiscovery: NSObject, ObservableObject, NetServiceBrowserDelegate, NetServiceDelegate {
    struct Endpoint: Identifiable {
        let name: String
        let port: Int
        var id: String { "\(name):\(port)" }
    }
    @Published private(set) var endpoints: [Endpoint] = []
    @Published private(set) var searching = false
    private let browser = NetServiceBrowser()
    private var services: [NetService] = []
    private var timeout: Task<Void, Never>?

    func start() {
        stop()
        endpoints = []
        services = []
        searching = true
        browser.delegate = self
        browser.searchForServices(ofType: "_remotepairing._tcp.", inDomain: "local.")
        timeout = Task {
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            guard !Task.isCancelled else { return }
            stop()
        }
    }
    private func stop() {
        timeout?.cancel()
        browser.stop()
        services.forEach { $0.stop() }
        searching = false
    }
    func netServiceBrowser(_ browser: NetServiceBrowser, didFind service: NetService, moreComing: Bool) {
        services.append(service)
        service.delegate = self
        service.resolve(withTimeout: 3)
    }
    func netServiceDidResolveAddress(_ sender: NetService) {
        guard (1...65535).contains(sender.port) else { return }
        let endpoint = Endpoint(name: sender.name, port: sender.port)
        if !endpoints.contains(where: { $0.id == endpoint.id }) { endpoints.append(endpoint) }
    }
    func netServiceBrowser(_ browser: NetServiceBrowser, didNotSearch errorDict: [String: NSNumber]) {
        stop()
    }
}
