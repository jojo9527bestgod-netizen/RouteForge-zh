import Combine
import Foundation
import RouteCore
import UIKit

enum ImportKind { case pairing, route }
enum ProbeState: Equatable { case idle, connecting, ready, fixed, route, stopping, awaitingRecovery, recoveryFailed }

@MainActor
final class ProbeModel: ObservableObject {
    @Published var latitudeText = "48.85837"
    @Published var longitudeText = "2.29448"
    @Published var portText = UserDefaults.standard.string(forKey: "remotePairingPort") ?? "49152"
    @Published var speedText = "25"
    @Published var durationMinutes = 10
    @Published var errorMessage: String?
    @Published private(set) var state: ProbeState
    @Published private(set) var pairingPresent: Bool
    @Published private(set) var route: RoutePath?
    @Published private(set) var routeName = "未载入路线"
    @Published private(set) var importing = false
    @Published private(set) var needsRecovery: Bool
    @Published private(set) var updateCount = 0
    @Published private(set) var elapsedSeconds = 0
    @Published private(set) var lastCoordinate = "尚未更新"
    @Published private(set) var backgroundWarning: String?
    @Published private(set) var events: [String] = []
    private let client = NativeLocationClient()
    private let pairing = PairingStore()
    private let background = BackgroundSupport()
    private var operation: Task<Void, Never>?
    private var generation = UUID()
    private var foreground = true

    init() {
        let pending = UserDefaults.standard.bool(forKey: "needsLocationRecovery")
        needsRecovery = pending
        pairingPresent = pairing.exists
        state = pending ? .recoveryFailed : .idle
        if pending { record("上次会话未确认恢复，请先清除模拟位置并核对高德。") }
    }
    var running: Bool { state == .fixed || state == .route }
    var busy: Bool { state == .connecting || state == .stopping || importing }
    var canStart: Bool { pairingPresent && !busy && !running && !needsRecovery }
    var canStop: Bool { pairingPresent && state != .stopping && !importing }
    var status: String {
        switch state {
        case .idle: return "等待连接"
        case .connecting: return "正在连接本机…"
        case .ready: return "定位通道已连接"
        case .fixed: return "正在模拟定点"
        case .route: return "正在播放路线"
        case .stopping: return "正在清除模拟位置…"
        case .awaitingRecovery: return "清除命令已完成，请确认真实定位"
        case .recoveryFailed: return "需要清除或确认真实定位"
        }
    }
    var logText: String { (["RouteForge iOS Probe 0.1.0", "日志不包含配对文件或真实定位。"] + events).joined(separator: "\n") }

    private func record(_ message: String) {
        events.append("\(ISO8601DateFormatter().string(from: Date())) \(message)")
        if events.count > 200 { events.removeFirst(events.count - 200) }
    }
    private func setState(_ next: ProbeState) {
        state = next
        UIApplication.shared.isIdleTimerDisabled = running && foreground
    }
    func setForeground(_ active: Bool) {
        foreground = active
        UIApplication.shared.isIdleTimerDisabled = running && active
    }
    private func setRecovery(_ value: Bool) {
        needsRecovery = value
        UserDefaults.standard.set(value, forKey: "needsLocationRecovery")
    }
    private func configuration() throws -> ConnectionConfiguration {
        guard pairing.exists else { throw ProbeFailure(message: "请先导入本机配对文件。") }
        guard let port = UInt16(portText), port > 0 else { throw ProbeFailure(message: "端口应为 1～65535。") }
        UserDefaults.standard.set(portText, forKey: "remotePairingPort")
        return ConnectionConfiguration(port: port, pairingURL: pairing.url)
    }

    func importFile(_ source: URL, kind: ImportKind) {
        guard !busy, !running else { return }
        importing = true
        Task {
            defer { importing = false }
            do {
                switch kind {
                case .pairing:
                    try await pairing.importFile(source, client: client)
                    pairingPresent = true
                    record("本机配对文件已验证并存入私有目录。")
                case .route:
                    let accessed = source.startAccessingSecurityScopedResource()
                    defer { if accessed { source.stopAccessingSecurityScopedResource() } }
                    let size = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                    guard size <= 5_000_000 else { throw ProbeFailure(message: "路线文件应小于 5 MB。") }
                    let data = try Data(contentsOf: source)
                    route = try RouteImport.parse(data)
                    routeName = source.deletingPathExtension().lastPathComponent
                    record("路线导入成功，\(route!.points.count) 点。")
                }
            } catch { errorMessage = error.localizedDescription }
        }
    }
    func loadDemo() {
        guard !busy, !running else { return }
        do {
            guard let url = Bundle.main.url(forResource: "Stability-Long", withExtension: "xml") else {
                throw ProbeFailure(message: "安装包缺少示例路线。")
            }
            route = try RouteImport.parse(Data(contentsOf: url))
            routeName = "巴黎测试环线（合成轨迹）"
            record("已载入示例路线。")
        } catch { errorMessage = error.localizedDescription }
    }

    func connect() {
        guard canStart else { return }
        do {
            let config = try configuration()
            let id = UUID(); generation = id
            setState(.connecting)
            operation = Task {
                do {
                    try await client.connect(config)
                    guard generation == id else { return }
                    setState(.ready); record("本机定位通道已连接。")
                } catch {
                    guard generation == id else { return }
                    setState(.idle); errorMessage = error.localizedDescription
                    record(error.localizedDescription)
                }
            }
        } catch { errorMessage = error.localizedDescription }
    }

    func startRoute() {
        guard let route else { errorMessage = "请先载入路线。"; return }
        start(path: route)
    }
    func startFixed() { start(path: nil) }

    private func start(path: RoutePath?) {
        guard canStart else { return }
        do {
            let config = try configuration()
            let fixed: GeoPoint
            let speed: Double
            if let path {
                fixed = path.points[0]
                guard let value = Double(speedText), value.isFinite, (1...80).contains(value) else {
                    throw ProbeFailure(message: "速度应为 1～80 km/h。")
                }
                speed = value
            } else {
                guard let latitude = Double(latitudeText), let longitude = Double(longitudeText) else {
                    throw RouteError.invalidCoordinate
                }
                fixed = try GeoPoint(latitude: latitude, longitude: longitude)
                speed = 0
            }
            let speedMeters = speed / 3.6
            let limit = Double(durationMinutes * 60)
            let id = UUID(); generation = id
            updateCount = 0; elapsedSeconds = 0
            setState(.connecting)
            operation = Task {
                do {
                    try await client.connect(config)
                    guard generation == id, !Task.isCancelled else { return }
                    setRecovery(true)
                    backgroundWarning = background.start()
                    setState(path == nil ? .fixed : .route)
                    record("会话开始；每 5 秒更新；最长 \(durationMinutes) 分钟；速度 \(speed) km/h。")
                    let started = ProcessInfo.processInfo.systemUptime
                    var nextTick = started
                    while generation == id, !Task.isCancelled {
                        let elapsed = ProcessInfo.processInfo.systemUptime - started
                        if elapsed >= limit { stop(reason: "达到本轮时限，自动停止。"); return }
                        let traveled = elapsed * speedMeters
                        let point = path?.position(at: traveled) ?? fixed
                        try await client.set(point)
                        guard generation == id, !Task.isCancelled else { return }
                        updateCount += 1; elapsedSeconds = Int(elapsed)
                        lastCoordinate = String(format: "%.6f, %.6f", point.latitude, point.longitude)
                        record("位置更新 \(updateCount)，已运行 \(elapsedSeconds) 秒。")
                        if let path, traveled >= path.length {
                            stop(reason: "路线播放完成，自动停止。"); return
                        }
                        nextTick += 5
                        let now = ProcessInfo.processInfo.systemUptime
                        if nextTick < now { nextTick = now + 5 }
                        try await Task.sleep(nanoseconds: UInt64(max(0.05, nextTick - now) * 1_000_000_000))
                    }
                } catch is CancellationError {
                    // Stop owns the queued clear; a cancelled sender must not send again.
                } catch {
                    guard generation == id else { return }
                    errorMessage = error.localizedDescription
                    record(error.localizedDescription)
                    stop(reason: "位置更新失败，尝试清除模拟位置。")
                }
            }
        } catch { errorMessage = error.localizedDescription }
    }

    func stop(reason: String = "用户停止。") {
        guard state != .stopping else { return }
        do {
            let config = try configuration()
            generation = UUID()
            operation?.cancel()
            setState(.stopping)
            record(reason)
            setRecovery(true)
            operation = Task {
                defer { background.stop(); backgroundWarning = nil }
                do {
                    try await client.clear(config)
                    setState(.awaitingRecovery)
                    record("清除命令成功；等待用户在高德确认真实位置。")
                } catch {
                    setState(.recoveryFailed)
                    errorMessage = error.localizedDescription
                    record("清除未完成：\(error.localizedDescription)")
                }
            }
        } catch { errorMessage = error.localizedDescription }
    }

    func confirmRecovery() {
        guard !running, !busy else { return }
        setRecovery(false)
        setState(.idle)
        record("用户确认高德已恢复真实位置。")
    }
}
