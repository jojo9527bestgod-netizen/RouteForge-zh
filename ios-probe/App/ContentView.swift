import SwiftUI
import UniformTypeIdentifiers

@MainActor
struct ContentView: View {
    @ObservedObject var model: ProbeModel
    @StateObject private var discovery = PortDiscovery()
    @Environment(\.scenePhase) private var scenePhase
    @State private var showImporter = false
    @State private var importKind = ImportKind.route

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Label(model.status, systemImage: model.running ? "location.fill" : "location.circle")
                        .font(.headline)
                    if model.running {
                        LabeledContent("已运行", value: "\(model.elapsedSeconds / 60) 分 \(model.elapsedSeconds % 60) 秒")
                        LabeledContent("位置更新", value: "\(model.updateCount) 次")
                        Text(model.lastCoordinate).font(.system(.footnote, design: .monospaced))
                    }
                    if let warning = model.backgroundWarning {
                        Text(warning).font(.footnote).foregroundStyle(.orange)
                    }
                } header: { Text("当前状态") }

                Section {
                    LabeledContent("配对文件", value: model.pairingPresent ? "已导入" : "未导入")
                    Button("导入本机配对文件") { openImporter(.pairing) }
                        .disabled(model.busy || model.running)
                    TextField("连接端口", text: $model.portText).keyboardType(.numberPad)
                        .disabled(model.busy || model.running)
                    Button(discovery.searching ? "正在查找…" : "查找设备端口") { discovery.start() }
                        .disabled(discovery.searching || model.busy || model.running)
                    ForEach(discovery.endpoints) { endpoint in
                        Button("\(endpoint.name) · \(endpoint.port)") { model.portText = String(endpoint.port) }
                            .disabled(model.busy || model.running)
                    }
                    Button("连接本机") { model.connect() }.disabled(!model.canStart)
                } header: { Text("准备连接") } footer: {
                    Text("先连接 LocalDevVPN，并使用 Wi-Fi。首次需导入已生成的本机配对文件；配对内容只保存在手机私有目录。")
                }

                Section {
                    TextField("纬度", text: $model.latitudeText).keyboardType(.numbersAndPunctuation)
                    TextField("经度", text: $model.longitudeText).keyboardType(.numbersAndPunctuation)
                    Button("开始定点模拟") { model.startFixed() }.disabled(!model.canStart)
                } header: { Text("定点坐标（WGS-84）") }
                    .disabled(model.busy || model.running)

                Section {
                    Text(model.routeName)
                    if let route = model.route {
                        LabeledContent("路线长度", value: String(format: "%.2f km · %d 点", route.length / 1000, route.points.count))
                    }
                    Button("载入巴黎测试环线") { model.loadDemo() }
                    Button("导入 GPX / XML / 坐标文本") { openImporter(.route) }
                    TextField("速度（km/h）", text: $model.speedText).keyboardType(.decimalPad)
                    Button("播放路线") { model.startRoute() }.disabled(!model.canStart || model.route == nil)
                } header: { Text("路线模拟") } footer: {
                    Text("测试环线是合成轨迹。首版按每 5 秒更新，是否在高德生效以其蓝色当前位置为准。")
                }
                .disabled(model.busy || model.running)

                Section {
                    Stepper("本轮最长 \(model.durationMinutes) 分钟", value: $model.durationMinutes, in: 1...30)
                        .disabled(model.busy || model.running)
                    Text("达到时限或路线结束后自动请求清除。App 前台模拟时保持常亮，停止后恢复正常自动锁屏。")
                        .font(.footnote).foregroundStyle(.secondary)
                }

                Section {
                    Button("停止并清除模拟位置", role: .destructive) { model.stop() }
                        .disabled(!model.canStop)
                    if model.needsRecovery {
                        Text("请到高德点回到当前位置，核对是否恢复真实位置。若仍残留，重开高德后观察；必要时返回这里重试清除。")
                            .font(.footnote)
                        Button("我已确认恢复真实位置") { model.confirmRecovery() }
                            .disabled(model.running || model.busy)
                    }
                } header: { Text("停止与恢复") }

                Section {
                    ShareLink("导出本轮日志", item: model.logText)
                    DisclosureGroup("查看运行记录") {
                        ForEach(Array(model.events.enumerated()), id: \.offset) { _, line in
                            Text(line).font(.system(.caption2, design: .monospaced)).textSelection(.enabled)
                        }
                    }
                } footer: {
                    Text("这是定位能力测试版。切到高德后的常亮由高德侧配合；本版未验收锁屏运行。")
                }
            }
            .navigationTitle("轨迹测试")
            .fileImporter(isPresented: $showImporter, allowedContentTypes: [.data], allowsMultipleSelection: false) { result in
                switch result {
                case .success(let urls):
                    if let url = urls.first { model.importFile(url, kind: importKind) }
                case .failure(let error): model.errorMessage = error.localizedDescription
                }
            }
            .alert("操作未完成", isPresented: Binding(
                get: { model.errorMessage != nil }, set: { if !$0 { model.errorMessage = nil } }
            )) {
                Button("知道了", role: .cancel) { model.errorMessage = nil }
            } message: { Text(model.errorMessage ?? "") }
            .onAppear { model.setForeground(scenePhase == .active) }
            .onChange(of: scenePhase) { _, phase in model.setForeground(phase == .active) }
        }
    }
    private func openImporter(_ kind: ImportKind) {
        importKind = kind
        showImporter = true
    }
}
