import AVFoundation
import CoreLocation
import RouteCore
import UIKit

struct SystemLocationFeedback {
    enum Source { case simulated, device, unknown }
    let source: Source
    let simulatedPoint: GeoPoint?
    let timestamp: Date
}

@MainActor
final class BackgroundSupport: NSObject, CLLocationManagerDelegate {
    private let location = CLLocationManager()
    private var player: AVAudioPlayer?
    private var active = false
    private var interruptionObserver: NSObjectProtocol?
    private var activitySession: CLBackgroundActivitySession?
    var onFeedback: ((SystemLocationFeedback) -> Void)?
    var onDiagnostic: ((String) -> Void)?

    override init() {
        super.init()
        location.delegate = self
        location.desiredAccuracy = kCLLocationAccuracyHundredMeters
        location.distanceFilter = kCLDistanceFilterNone
        location.allowsBackgroundLocationUpdates = true
        location.pausesLocationUpdatesAutomatically = false
        location.showsBackgroundLocationIndicator = true
        location.activityType = .otherNavigation
        interruptionObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification, object: nil, queue: .main
        ) { [weak self] notification in
            guard let type = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  type == AVAudioSession.InterruptionType.ended.rawValue else { return }
            Task { @MainActor in
                guard let self, self.active else { return }
                try? AVAudioSession.sharedInstance().setActive(true)
                self.player?.play()
                self.onDiagnostic?("音频中断结束；\(self.diagnosticStatus)")
            }
        }
    }

    func start() -> String? {
        active = true
        var warning: String?
        do {
            let audio = AVAudioSession.sharedInstance()
            try audio.setCategory(.playback, mode: .default, options: .mixWithOthers)
            try audio.setActive(true)
            let player = try AVAudioPlayer(data: Self.silentWave())
            player.numberOfLoops = -1
            player.prepareToPlay()
            guard player.play() else { throw ProbeFailure(message: "后台音频未启动") }
            self.player = player
        } catch { warning = "后台音频未启动，切到高德后需注意移动是否继续。" }
        switch location.authorizationStatus {
        case .notDetermined: location.requestWhenInUseAuthorization()
        case .authorizedAlways, .authorizedWhenInUse: startLocationUpdates()
        default: warning = "后台定位权限未开启，当前使用音频维持会话。"
        }
        return warning
    }

    var diagnosticStatus: String {
        "定位授权 \(location.authorizationStatus.rawValue)，后台定位会话 \(activitySession != nil ? "开启" : "未开启")，音频 \(player?.isPlaying == true ? "播放中" : "未播放")"
    }

    private func startLocationUpdates() {
        guard active else { return }
        if activitySession == nil, UIApplication.shared.applicationState == .active {
            activitySession = CLBackgroundActivitySession()
        }
        location.startUpdatingLocation()
    }

    func resumeIfNeeded() {
        guard active else { return }
        startLocationUpdates()
        if player?.isPlaying == false {
            try? AVAudioSession.sharedInstance().setActive(true)
            player?.play()
        }
    }

    func stop() {
        active = false
        player?.stop()
        player = nil
        location.stopUpdatingLocation()
        activitySession?.invalidate()
        activitySession = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        UIApplication.shared.isIdleTimerDisabled = false
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard active else { return }
        if manager.authorizationStatus == .authorizedAlways || manager.authorizationStatus == .authorizedWhenInUse {
            startLocationUpdates()
        }
        onDiagnostic?("定位授权变化；\(diagnosticStatus)")
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard active, let fix = locations.last, fix.horizontalAccuracy >= 0 else { return }
        let source: SystemLocationFeedback.Source
        let simulatedPoint: GeoPoint?
        if let information = fix.sourceInformation {
            source = information.isSimulatedBySoftware ? .simulated : .device
            simulatedPoint = information.isSimulatedBySoftware
                ? try? GeoPoint(latitude: fix.coordinate.latitude, longitude: fix.coordinate.longitude) : nil
        } else {
            source = .unknown; simulatedPoint = nil
        }
        // Real coordinates are neither retained nor sent to the diagnostic callback.
        onFeedback?(SystemLocationFeedback(source: source, simulatedPoint: simulatedPoint, timestamp: fix.timestamp))
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard active else { return }
        let failure = error as NSError
        onDiagnostic?("系统定位反馈失败，代码 \(failure.code)。")
    }

    private static func silentWave() -> Data {
        let samples: UInt32 = 8_000
        let byteCount = samples * 2
        var data = Data("RIFF".utf8)
        func append<T: FixedWidthInteger>(_ value: T) {
            var little = value.littleEndian
            withUnsafeBytes(of: &little) { data.append(contentsOf: $0) }
        }
        append(UInt32(36) + byteCount)
        data.append(Data("WAVEfmt ".utf8))
        append(UInt32(16)); append(UInt16(1)); append(UInt16(1))
        append(samples); append(samples * 2); append(UInt16(2)); append(UInt16(16))
        data.append(Data("data".utf8)); append(byteCount)
        data.append(Data(repeating: 0, count: Int(byteCount)))
        return data
    }
}
