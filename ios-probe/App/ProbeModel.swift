import Combine
import Foundation
import RouteCore
import UIKit

enum ImportKind { case pairing, route }
enum ProbeState: Equatable { case idle, connecting, ready, fixed, route, paused, stopping, awaitingRecovery, recoveryFailed }

@MainActor
final class ProbeModel: ObservableObject {
    @Published var latitudeText = "48.85837"
    @Published var longitudeText = "2.29448"
    @Published var portText = UserDefaults.standard.string(forKey: "remotePairingPort") ?? "49152"
    @Published private(set) var speedKmh = 25.0
    @Published var durationMinutes = UserDefaults.standard.object(forKey: "simulationDurationMinutes") as? Int ?? 10 {
        didSet { UserDefaults.standard.set(durationMinutes, forKey: "simulationDurationMinutes") }
    }
    @Published var playbackMode = PlaybackMode(rawValue: UserDefaults.standard.string(forKey: "simulationPlaybackMode") ?? "") ?? .once {
        didSet { UserDefaults.standard.set(playbackMode.rawValue, forKey: "simulationPlaybackMode") }
    }
    @Published var errorMessage: String?
    @Published private(set) var state: ProbeState
    @Published private(set) var pairingPresent: Bool
    @Published private(set) var route: RoutePath?
    @Published private(set) var routeName = "未载入路线"
    @Published private(set) var importing = false
    @Published private(set) var setupInProgress = false
    @Published private(set) var needsRecovery: Bool
    @Published private(set) var updateCount = 0
    @Published private(set) var elapsedSeconds = 0
    @Published private(set) var lastCoordinate = "尚未更新"
    @Published private(set) var livePoint: GeoPoint?
    @Published private(set) var routeProgress = 0.0
    @Published private(set) var routeRevision = 0
    @Published private(set) var backgroundWarning: String?
    @Published private(set) var systemLocationText = "尚未接收系统定位反馈"
    @Published private(set) var systemSimulatedFixCount = 0
    @Published private(set) var events: [String] = []
    private let client = NativeLocationClient()
    private let pairing = PairingStore()
    lazy var setup = DeviceSetupModel(pairing: pairing, busyChanged: { [weak self] busy in
        self?.setupInProgress = busy
    }, paired: { [weak self] in
        guard let self else { return }
        self.pairingPresent = self.pairing.exists
        self.errorMessage = nil
        self.record("本机配对完成，私有资料已保存。")
    })
    private let background = BackgroundSupport()
    private let runLog = RunLogStore()
    private var lastSystemSimulatedPoint: GeoPoint?
    private var operation: Task<Void, Never>?
    private var generation = UUID()
    private var foreground = true

    init() {
        let pending = UserDefaults.standard.bool(forKey: "needsLocationRecovery")
        needsRecovery = pending
        pairingPresent = pairing.exists
        state = pending ? .recoveryFailed : .idle
        let savedSpeed = UserDefaults.standard.double(forKey: "simulationSpeedKmh")
        if savedSpeed.isFinite, (1...150).contains(savedSpeed) { speedKmh = savedSpeed }
        background.onFeedback = { [weak self] feedback in self?.receive(feedback) }
        background.onDiagnostic = { [weak self] message in self?.record(message) }
        record("App 已启动。")
        if pending { record("上次会话未确认恢复，请先清除模拟位置并核对高德。") }
    }
    var running: Bool { state == .fixed || state == .route || state == .paused }
    var paused: Bool { state == .paused }
    var busy: Bool { state == .connecting || state == .stopping || importing || setupInProgress }
    var canStart: Bool { pairingPresent && !busy && !running && !needsRecovery }
    var canStop: Bool { pairingPresent && state != .stopping && !importing && !setupInProgress }
    var status: String {
        if setupInProgress { return "正在配置本机…" }
        switch state {
        case .idle: return "等待连接"
        case .connecting: return "正在连接本机…"
        case .ready: return "定位通道已连接"
        case .fixed: return "正在模拟定点"
        case .route: return "正在播放路线"
        case .paused: return "已暂停，保持当前位置"
        case .stopping: return "正在清除模拟位置…"
        case .awaitingRecovery: return "已发送停止请求，请核对真实定位"
        case .recoveryFailed: return "需要清除或确认真实定位"
        }
    }
    var logText: String { (["RouteForge iOS \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")", "日志不包含配对文件或真实定位。"] + events).joined(separator: "\n") }

    private func record(_ message: String) {
        events.append("\(ISO8601DateFormatter().string(from: Date())) \(message)")
        if events.count > 200 { events.removeFirst(events.count - 200) }
        runLog.write(logText)
    }
    private func receive(_ feedback: SystemLocationFeedback) {
        switch feedback.source {
        case .simulated:
            guard let point = feedback.simulatedPoint else { return }
            let delta = lastSystemSimulatedPoint.map { $0.distance(to: point) }
            lastSystemSimulatedPoint = point
            systemSimulatedFixCount += 1
            systemLocationText = "系统已返回模拟位置 · \(systemSimulatedFixCount) 次反馈"
            if let delta, delta > 0.5 {
                record(String(format: "系统模拟位置移动 %.1f 米。", delta))
            } else if systemSimulatedFixCount == 1 { record("系统已返回首个模拟位置。") }
        case .device:
            lastSystemSimulatedPoint = nil
            systemLocationText = "系统当前返回非模拟位置"
        case .unknown:
            systemLocationText = "系统已返回位置，来源尚未确认"
        }
    }
    private func setState(_ next: ProbeState) {
        state = next
        UIApplication.shared.isIdleTimerDisabled = running && foreground
    }
    func setForeground(_ active: Bool) {
        if foreground != active, running || state == .stopping {
            record("\(active ? "App 回到前台" : "App 离开前台")；\(background.diagnosticStatus)")
        }
        foreground = active
        UIApplication.shared.isIdleTimerDisabled = running && active
        if active, running { background.resumeIfNeeded() }
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

    func importFile(_ source: URL, kind: ImportKind) async -> Bool {
        guard !busy, !running else { return false }
        importing = true
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
                routeRevision += 1; routeProgress = 0
                record("路线导入成功，\(route!.points.count) 点。")
            }
            return true
        } catch { errorMessage = error.localizedDescription; return false }
    }
    func loadDemo() {
        guard !busy, !running else { return }
        do {
            guard let url = Bundle.main.url(forResource: "Stability-Long", withExtension: "xml") else {
                throw ProbeFailure(message: "安装包缺少示例路线。")
            }
            route = try RouteImport.parse(Data(contentsOf: url))
            routeName = "巴黎测试环线（合成轨迹）"
            routeRevision += 1
            record("已载入示例路线。")
        } catch { errorMessage = error.localizedDescription }
    }

    func useRoute(_ path: RoutePath, name: String) {
        guard !running, !busy else { return }
        route = path; routeName = name; routeRevision += 1; routeProgress = 0
        record("路线已就绪，共 \(path.points.count) 个坐标点。")
    }
    func clearPlannedRoute() {
        guard !running, !busy else { return }
        route = nil; routeName = "未生成路线"; routeRevision += 1; routeProgress = 0
    }
    func changeSpeed(_ value: Double) {
        guard value.isFinite else { return }
        speedKmh = min(150, max(1, value))
        UserDefaults.standard.set(speedKmh, forKey: "simulationSpeedKmh")
    }
    func togglePause() {
        if state == .route { setState(.paused); record("正在暂停系统路线，保持当前位置。") }
        else if state == .paused { setState(.route); record("正在继续系统路线。") }
    }
    func startFixed(at point: GeoPoint) {
        latitudeText = String(point.latitude); longitudeText = String(point.longitude)
        startFixed()
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
            if let path {
                fixed = path.points[0]
            } else {
                guard let latitude = Double(latitudeText), let longitude = Double(longitudeText) else {
                    throw RouteError.invalidCoordinate
                }
                fixed = try GeoPoint(latitude: latitude, longitude: longitude)
            }
            let limit = Double(durationMinutes * 60)
            let mode = playbackMode
            let id = UUID(); generation = id
            updateCount = 0; elapsedSeconds = 0; routeProgress = 0
            systemSimulatedFixCount = 0; lastSystemSimulatedPoint = nil
            systemLocationText = "等待系统返回模拟位置"
            setState(.connecting)
            operation = Task {
                do {
                    try await client.connect(config)
                    guard generation == id, !Task.isCancelled else { return }
                    setRecovery(true)
                    backgroundWarning = background.start()
                    setState(path == nil ? .fixed : .route)
                    record("会话开始；CoreDevice 系统路线，间隔 1 秒；\(limit == 0 ? "不限时" : "最长 \(Int(limit / 60)) 分钟")；\(mode.title)；速度 \(speedKmh) km/h；\(background.diagnosticStatus)。")
                    let started = ProcessInfo.processInfo.systemUptime
                    var previousTick = started
                    var cursor = PlaybackCursor()
                    var sent = false
                    var legBoundary = 0.0
                    var activeSpeed = speedKmh
                    var activePaused = false
                    var nextLog = 0
                    while generation == id, !Task.isCancelled {
                        let now = ProcessInfo.processInfo.systemUptime
                        let elapsed = now - started
                        if limit > 0, elapsed >= limit { stop(reason: "达到定时时限，自动停止。"); return }
                        // Account for the command that was actually running during
                        // this interval, before applying a newly requested control.
                        try cursor.advance(seconds: sent ? max(0, now - previousTick) : 0,
                                           speedKmh: activeSpeed, paused: activePaused)
                        previousTick = now
                        let sample = path.map { cursor.sample(length: $0.length, mode: mode) }
                        let point = path?.position(at: sample?.distance ?? 0) ?? fixed
                        if sample?.complete == true {
                            stop(reason: "路线播放完成，自动停止。"); return
                        }
                        let requestedPause = paused
                        let requestedSpeed = speedKmh
                        if let path {
                            if !sent || requestedPause != activePaused ||
                                (!requestedPause && (requestedSpeed != activeSpeed || cursor.distance >= legBoundary)) {
                                if requestedPause {
                                    try await client.set(point)
                                    record("系统已确认暂停，保持当前位置。")
                                } else {
                                    let leg = try NativeRouteLeg(path: path, traveled: cursor.distance, mode: mode)
                                    try await client.play(leg.points, speedKmh: requestedSpeed)
                                    legBoundary = leg.boundary
                                    record("系统已接收路线段，共 \(leg.points.count) 点，速度 \(requestedSpeed) km/h。")
                                }
                                updateCount += 1
                            }
                        } else if !sent {
                            try await client.set(point)
                            updateCount += 1
                            record("系统已接收定点位置。")
                        }
                        guard generation == id, !Task.isCancelled else { return }
                        sent = true; activeSpeed = requestedSpeed; activePaused = requestedPause
                        elapsedSeconds = Int(elapsed)
                        livePoint = point
                        if let path, let sample { routeProgress = min(1, max(0, sample.distance / path.length)) }
                        lastCoordinate = String(format: "%.6f, %.6f", point.latitude, point.longitude)
                        if elapsedSeconds >= nextLog {
                            record("播放进度，已运行 \(elapsedSeconds) 秒，预计累计推进 \(Int(cursor.distance)) 米，速度 \(activeSpeed) km/h，\(activePaused ? "暂停" : "播放")，\(foreground ? "前台" : "后台")；系统指令 \(updateCount) 次。")
                            nextLog = elapsedSeconds + 5
                        }
                        try await Task.sleep(nanoseconds: 1_000_000_000)
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
                    livePoint = nil
                    setState(.awaitingRecovery)
                    systemLocationText = "系统已接受清除请求，请核对真实位置"
                    record("系统已接受清除请求，本机会话已关闭；真实位置恢复尚待核对。地图保留旧位置时，关闭并重开地图再定位。")
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
        livePoint = nil
        setState(.idle)
        record("用户确认高德已恢复真实位置。")
    }
}
