import RouteCore
import SwiftUI
import UIKit

@MainActor
struct ConnectionSettingsScreen: View {
    @ObservedObject var model: ProbeModel
    let onImport: () -> Void
    @StateObject private var discovery = PortDiscovery()
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Label(model.status, systemImage: model.running ? "location.fill" : "link")
                    Button("打开并连接 LocalDevVPN") { openVPN() }
                    Text(model.pairingPresent ? "本机配对资料已保存，无需重复导入。" : "首次使用请导入这台手机的配对文件。")
                        .font(.caption).foregroundStyle(.secondary)
                    Button(model.pairingPresent ? "更换本机配对文件" : "导入本机配对文件", action: onImport)
                        .disabled(model.busy || model.running)
                    Button("连接本机", action: model.connect).disabled(!model.canStart)
                } header: { Text("定位连接") } footer: {
                    Text("保持 LocalDevVPN 连接。初次配置完成后，日常使用不必打开 StikDebug。手机重启后若开发服务未就绪，仍可能需要重新初始化。")
                }
                Section {
                    Picker("播放方式", selection: $model.playbackMode) {
                        ForEach(PlaybackMode.allCases, id: \.self) { Text($0.title).tag($0) }
                    }
                    Picker("自动结束", selection: $model.durationMinutes) {
                        Text("不限时").tag(0)
                        ForEach([5, 10, 20, 30, 60], id: \.self) { Text("\($0) 分钟").tag($0) }
                    }
                } header: { Text("路线播放") } footer: {
                    Text("单程到终点后停止并清除模拟位置。循环和往返可持续运行；暂停期间保留当前位置。运行时本 App 前台保持屏幕常亮。")
                }.disabled(model.running || model.busy)
                Section("停止与恢复") {
                    Button("停止并清除模拟位置", role: .destructive) { model.stop() }.disabled(!model.canStop)
                    if model.needsRecovery, !model.running, !model.busy {
                        Button("我已确认恢复真实位置", action: model.confirmRecovery)
                    }
                    if let warning = model.backgroundWarning { Text(warning).font(.caption).foregroundStyle(.orange) }
                }
                DisclosureGroup("高级连接设置") {
                    HStack {
                        Text("本机端口")
                        TextField("49152", text: $model.portText).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                    }
                    Button(discovery.searching ? "正在查找…" : "查找本机端口", action: discovery.start).disabled(discovery.searching)
                    ForEach(discovery.endpoints) { endpoint in
                        Button("\(endpoint.name) · \(endpoint.port)") { model.portText = String(endpoint.port) }
                    }
                }.disabled(model.running || model.busy)
                DisclosureGroup("运行记录") {
                    Text("已更新 \(model.updateCount) 次 · \(model.elapsedSeconds) 秒").font(.caption)
                    ShareLink("导出运行记录", item: model.logText)
                    ForEach(Array(model.events.suffix(12).enumerated()), id: \.offset) { _, event in Text(event).font(.caption2.monospaced()) }
                }
                Section {
                    Text("轨迹导航 \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")")
                    Text("地图、搜索和道路规划使用 Apple 地图在线服务；选点和搜索会发送至该服务。收藏路线与配对资料只保存在本机，运行记录不包含配对资料或真实定位。")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }.navigationTitle("设置")
        }
    }
    private func openVPN() {
        guard let url = URL(string: "localdevvpn://enable?scheme=routeforge") else { return }
        UIApplication.shared.open(url, options: [:]) { success in
            if !success { Task { @MainActor in model.errorMessage = "未能打开 LocalDevVPN，请确认已安装并手动打开连接。" } }
        }
    }
}
