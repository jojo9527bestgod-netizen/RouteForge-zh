import AVFoundation
import Combine
import Foundation
import UIKit
import UserNotifications

@MainActor
final class DeviceSetupModel: NSObject, ObservableObject, NetServiceDelegate {
    enum Phase: Equatable { case idle, publishing, pairing, cancelling, checking, initializing, ready, failed }
    @Published private(set) var phase = Phase.idle
    @Published private(set) var status = "先连接 LocalDevVPN，再检查本机连接。"
    @Published private(set) var pin: String?
    @Published private(set) var secondsRemaining = 0
    private let pairing: PairingStore
    private let client = NativeDeviceSetupClient()
    private let validator = NativeLocationClient()
    private let busyChanged: (Bool) -> Void
    private let paired: () -> Void
    private var job: NativePairingJob?
    private var service: NetService?
    private var pairingTask: Task<Void, Never>?
    private var deadlineTask: Task<Void, Never>?
    private var player: AVAudioPlayer?
    private var generation = UUID()
    private var timedOut = false
    private let notificationID = "net.routeforge.setup.pairing-code"

    init(pairing: PairingStore, busyChanged: @escaping (Bool) -> Void, paired: @escaping () -> Void) {
        self.pairing = pairing; self.busyChanged = busyChanged; self.paired = paired
        super.init()
    }
    var busy: Bool { [.publishing, .pairing, .cancelling, .checking, .initializing].contains(phase) }
    var pairingActive: Bool { phase == .publishing || phase == .pairing || phase == .cancelling }
    var canCancel: Bool { phase == .publishing || phase == .pairing }

    private func begin(_ next: Phase, message: String) throws {
        guard !busy else { throw ProbeFailure(message: "请等待当前配置完成。") }
        let audio = AVAudioSession.sharedInstance()
        try audio.setCategory(.playback, mode: .default, options: .mixWithOthers)
        try audio.setActive(true)
        let value = try AVAudioPlayer(data: BackgroundSupport.silentWave())
        value.numberOfLoops = -1
        value.prepareToPlay()
        guard value.play() else {
            try? audio.setActive(false, options: .notifyOthersOnDeactivation)
            throw ProbeFailure(message: "配置期间的后台保持未能启动，请重试。")
        }
        player = value
        generation = UUID(); timedOut = false
        phase = next; status = message; busyChanged(true)
    }
    private func finish(_ next: Phase, message: String) {
        deadlineTask?.cancel(); deadlineTask = nil
        service?.stop(); service?.delegate = nil; service = nil
        job = nil; pairingTask = nil; pin = nil; secondsRemaining = 0
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [notificationID])
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [notificationID])
        player?.stop(); player = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        phase = next; status = message; busyChanged(false)
    }
    private func configuration(_ portText: String) throws -> ConnectionConfiguration {
        guard pairing.exists else { throw ProbeFailure(message: "请先在本机配对，或导入已有的本机配对资料。") }
        guard let port = UInt16(portText), port > 0 else { throw ProbeFailure(message: "连接端口应为 1～65535。") }
        UserDefaults.standard.set(portText, forKey: "remotePairingPort")
        return ConnectionConfiguration(port: port, pairingURL: pairing.url)
    }
    func check(portText: String) {
        guard !busy else { return }
        do {
            let config = try configuration(portText)
            try begin(.checking, message: "正在检查本机连接和定位服务…")
            Task {
                do {
                    let ready = try await client.check(config)
                    finish(ready ? .ready : .idle, message: ready
                        ? "准备完成，可以返回地图开始模拟。"
                        : "本机通道已连接，请点“初始化本机服务”。")
                } catch { finish(.failed, message: error.localizedDescription) }
            }
        } catch { phase = .failed; status = error.localizedDescription }
    }
    func initialize(portText: String) {
        guard !busy else { return }
        guard #available(iOS 26.4, *) else {
            phase = .failed; status = "此初始化方式适用于 iOS 26.4 及以上；当前系统可继续使用已有的开发服务。"; return
        }
        do {
            let config = try configuration(portText)
            try begin(.initializing, message: "正在校验文件并向 Apple 申请本机初始化，请保持联网…")
            Task {
                do {
                    try await client.initialize(config)
                    finish(.ready, message: "本机初始化完成，可以返回地图开始模拟。")
                } catch { finish(.failed, message: error.localizedDescription) }
            }
        } catch { phase = .failed; status = error.localizedDescription }
    }
    func startPairing() {
        guard !busy else { return }
        guard #available(iOS 27.0, *) else {
            phase = .failed; status = "本机配对入口需要 iOS 27 及以上；可导入已有的本机配对资料。"; return
        }
        do {
            try begin(.publishing, message: "正在发布本机配对入口…")
            let id = generation
            let newJob = try NativePairingJob { [weak self] code in
                Task { @MainActor in self?.receivedPIN(code, generation: id) }
            }
            job = newJob
            let publisher = NetService(domain: "local.", type: "_remotepairing-pairable-host._tcp.",
                                       name: newJob.serviceName, port: Int32(newJob.port))
            publisher.delegate = self
            guard publisher.setTXTRecord(newJob.txtRecord) else {
                throw ProbeFailure(message: "无法发布本机配对信息。")
            }
            service = publisher
            publisher.publish()
            secondsRemaining = 180
            deadlineTask = Task { [weak self] in
                for seconds in stride(from: 179, through: 0, by: -1) {
                    do { try await Task.sleep(nanoseconds: 1_000_000_000) } catch { return }
                    guard let self, self.generation == id, self.pairingActive else { return }
                    self.secondsRemaining = seconds
                }
                guard let self, self.generation == id else { return }
                self.timedOut = true; self.cancelPairing()
            }
            Task {
                _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
            }
        } catch { finish(.failed, message: error.localizedDescription) }
    }
    func netServiceDidPublish(_ sender: NetService) {
        guard sender === service, phase == .publishing, let activeJob = job else { return }
        phase = .pairing
        status = "请到系统“设置 → 隐私与安全性 → 开发者模式”，选择与“轨迹导航”配对。"
        let id = generation
        pairingTask = Task { [activeJob] in
            var temporary: URL?
            defer { if let temporary { try? FileManager.default.removeItem(at: temporary) } }
            do {
                let url = try pairing.generatedTemporaryURL(); temporary = url
                try await activeJob.accept(into: url)
                guard generation == id, phase != .cancelling else {
                    finish(timedOut ? .failed : .idle, message: timedOut ? "配对超时，请检查 Wi-Fi 和系统配对入口后重试。" : "已取消配对。")
                    return
                }
                deadlineTask?.cancel(); deadlineTask = nil
                phase = .checking; status = "正在验证并保存本机配对资料…"
                try await pairing.importFile(url, client: validator)
                paired()
                finish(.idle, message: "本机配对完成，资料已保存在本 App。请返回后检查本机连接。")
            } catch {
                guard generation == id else { return }
                if phase == .cancelling {
                    finish(timedOut ? .failed : .idle, message: timedOut ? "配对超时，请检查 Wi-Fi 和系统配对入口后重试。" : "已取消配对。")
                } else { finish(.failed, message: error.localizedDescription) }
            }
        }
    }
    func netService(_ sender: NetService, didNotPublish errorDict: [String: NSNumber]) {
        guard sender === service else { return }
        job?.cancel()
        if pairingTask == nil { finish(.failed, message: "无法发布配对入口。请检查 Wi-Fi 和本地网络权限，再重试。") }
        else { phase = .cancelling; status = "配对连接中断，正在结束会话…" }
    }
    func cancelPairing() {
        guard canCancel else { return }
        job?.cancel(); service?.stop()
        if pairingTask == nil {
            finish(timedOut ? .failed : .idle, message: timedOut ? "配对等待超时，请重试。" : "已取消配对。")
        } else { phase = .cancelling; status = "正在结束配对会话…" }
    }
    private func receivedPIN(_ code: String, generation id: UUID) {
        guard generation == id, phase == .pairing, code.count == 6,
              code.unicodeScalars.allSatisfy({ (48...57).contains(Int($0.value)) }) else { return }
        pin = code
        status = "请在系统配对窗口输入下方配对码。配对码仅在本机显示，不写入运行记录。"
        let content = UNMutableNotificationContent()
        content.title = "轨迹导航配对码"
        content.body = "请在系统配对窗口输入：\(code)"
        content.sound = .default
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: notificationID, content: content, trigger: nil),
                                               withCompletionHandler: nil)
    }
}
