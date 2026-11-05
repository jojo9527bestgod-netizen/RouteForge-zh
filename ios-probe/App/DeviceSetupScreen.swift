import SwiftUI

@MainActor
struct DeviceSetupScreen: View {
    @ObservedObject var model: ProbeModel
    @ObservedObject var setup: DeviceSetupModel
    let onOpenVPN: () -> Void
    var body: some View {
        Form {
            Section {
                Label(setup.status, systemImage: setup.phase == .ready ? "checkmark.circle.fill" : "iphone")
                if setup.busy { ProgressView() }
            } header: { Text("首次配置与修复") } footer: {
                Text("需要保持 Wi-Fi 和 LocalDevVPN 连接。配置在这台手机内完成，配对资料保存在本 App 私有目录。")
            }
            Section("1 · 连接本机 VPN") {
                Button("打开并连接 LocalDevVPN", action: onOpenVPN).disabled(setup.busy)
                Text("连接成功后回到这里继续。首次添加 VPN 配置需在系统提示中确认。")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Section {
                LabeledContent("本机配对资料", value: model.pairingPresent ? "已保存" : "未配置")
                if setup.pairingActive {
                    Text("等待配对 · 剩余 \(setup.secondsRemaining) 秒").font(.subheadline.monospacedDigit())
                    if let pin = setup.pin {
                        Text(pin).font(.system(size: 36, weight: .bold, design: .monospaced))
                            .frame(maxWidth: .infinity).accessibilityLabel("配对码 \(pin.map { String($0) }.joined(separator: " "))")
                    }
                    Text("在系统“设置 → 隐私与安全性 → 开发者模式”中，选择与“轨迹导航”配对，输入这里或通知中显示的六位配对码。")
                        .font(.callout)
                    Button("取消配对", role: .cancel, action: setup.cancelPairing).disabled(!setup.canCancel)
                } else {
                    Button(model.pairingPresent ? "重新在本机配对" : "在本机配对", action: setup.startPairing)
                        .disabled(model.busy || model.running)
                }
            } header: { Text("2 · 本机配对") } footer: {
                Text(model.pairingPresent
                     ? "已有资料通常无需重新配对，直接检查连接即可。重新配对失败或取消时，原资料继续保留。"
                     : "本机配对需要 iOS 27 及以上。配对时如有本地网络或通知提示，请按需要允许；配对码不会进入运行记录。")
            }
            Section {
                Button("检查本机连接") { setup.check(portText: model.portText) }
                    .disabled(model.busy || model.running || !model.pairingPresent)
                Button("初始化本机服务") { setup.initialize(portText: model.portText) }
                    .disabled(model.busy || model.running || !model.pairingPresent)
            } header: { Text("3 · 准备定位服务") } footer: {
                Text("先检查连接；服务尚未就绪时再初始化。配套文件随安装包提供，初始化需联网向 Apple 申请本机授权。此初始化方式适用于 iOS 26.4 及以上。")
            }
            if setup.phase == .ready {
                Section { Text("准备完成。返回地图后即可选点、生成路线并开始模拟。") }
            }
        }
        .navigationTitle("本机配置")
        .navigationBarBackButtonHidden(setup.busy)
        .disabled(model.running)
    }
}
