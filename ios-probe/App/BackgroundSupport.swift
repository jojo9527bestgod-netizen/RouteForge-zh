import AVFoundation
import CoreLocation
import UIKit

@MainActor
final class BackgroundSupport: NSObject, CLLocationManagerDelegate {
    private let location = CLLocationManager()
    private var player: AVAudioPlayer?
    private var active = false
    private var interruptionObserver: NSObjectProtocol?

    override init() {
        super.init()
        location.delegate = self
        location.desiredAccuracy = kCLLocationAccuracyThreeKilometers
        location.distanceFilter = CLLocationDistanceMax
        location.allowsBackgroundLocationUpdates = true
        location.pausesLocationUpdatesAutomatically = false
        interruptionObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification, object: nil, queue: .main
        ) { [weak self] notification in
            guard let type = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  type == AVAudioSession.InterruptionType.ended.rawValue else { return }
            Task { @MainActor in
                guard let self, self.active else { return }
                try? AVAudioSession.sharedInstance().setActive(true)
                self.player?.play()
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
        case .notDetermined: location.requestAlwaysAuthorization()
        case .authorizedAlways, .authorizedWhenInUse: location.startUpdatingLocation()
        default: warning = "后台定位权限未开启，当前使用音频维持会话。"
        }
        return warning
    }

    func stop() {
        active = false
        player?.stop()
        player = nil
        location.stopUpdatingLocation()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        UIApplication.shared.isIdleTimerDisabled = false
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard active else { return }
        if manager.authorizationStatus == .authorizedAlways || manager.authorizationStatus == .authorizedWhenInUse {
            manager.startUpdatingLocation()
        }
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
