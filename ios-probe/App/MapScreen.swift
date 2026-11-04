import RouteCore
import SwiftUI

@MainActor
struct MapScreen: View {
    @ObservedObject var model: ProbeModel
    @ObservedObject var planner: RoutePlannerModel
    @ObservedObject var library: RouteLibrary
    let onSetup: () -> Void
    @State private var satellite = false
    @State private var showSearch = false
    @State private var showWaypoints = false
    @State private var showSpeed = false
    @State private var showSave = false
    @State private var saveName = ""

    var body: some View {
        NavigationStack {
            RouteMapView(
                waypoints: planner.draft.waypoints, route: model.route, revision: model.routeRevision,
                livePoint: model.livePoint, editable: planner.editable, satellite: satellite,
                fitRequest: planner.fitRequest, locationRequest: planner.locationRequest,
                focusRequest: planner.focusRequest, focusedPoint: planner.focusedPoint,
                onAdd: planner.add, onMove: { planner.update(id: $0, point: $1) },
                onRegion: { planner.searchRegion = $0 }, onLocationError: { planner.errorMessage = $0 }
            )
            .overlay(alignment: .top) { searchBar }
            .overlay(alignment: .topLeading) { statusBadge.padding(.top, 76).padding(.leading, 16) }
            .overlay(alignment: .trailing) { mapTools.padding(.trailing, 14) }
            .safeAreaInset(edge: .bottom, spacing: 0) { controlCard.padding(12).background(.ultraThinMaterial) }
            .navigationTitle("轨迹导航")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { planner.undo() } label: { Image(systemName: "arrow.uturn.backward") }
                        .disabled(!planner.editable || planner.draft.waypoints.isEmpty).accessibilityLabel("撤销最后选点")
                    Button { planner.clear() } label: { Image(systemName: "trash") }
                        .disabled(!planner.editable || (planner.draft.waypoints.isEmpty && model.route == nil))
                        .accessibilityLabel("清空当前路线")
                    Button { satellite.toggle() } label: { Image(systemName: satellite ? "map" : "square.3.layers.3d") }
                        .accessibilityLabel("切换地图样式")
                }
            }
            .sheet(isPresented: $showSearch) { PlaceSearchSheet(planner: planner) }
            .sheet(isPresented: $showWaypoints) { WaypointEditorSheet(planner: planner) }
            .sheet(isPresented: $showSpeed) { SpeedSheet(model: model) }
            .alert("收藏路线", isPresented: $showSave) {
                TextField("路线名称", text: $saveName)
                Button("收藏") { planner.save(name: saveName, to: library) }
                Button("取消", role: .cancel) { }
            }
        }
    }

    private var searchBar: some View {
        Button { showSearch = true } label: {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass").foregroundStyle(.blue)
                Text("搜索地点或输入经纬度").foregroundStyle(.secondary)
                Spacer()
            }
            .font(.subheadline).padding(15)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 15))
            .shadow(color: .black.opacity(0.08), radius: 8, y: 3)
        }
        .buttonStyle(.plain).disabled(!planner.editable).padding(14)
    }
    private var statusBadge: some View {
        Button(action: onSetup) {
            Label(model.status, systemImage: model.running ? "location.fill" : "link")
                .font(.caption.weight(.medium)).padding(.horizontal, 11).padding(.vertical, 8)
                .background(.regularMaterial, in: Capsule())
        }.buttonStyle(.plain)
    }
    private var mapTools: some View {
        VStack(spacing: 12) {
            mapButton("location", label: "回到当前位置", action: planner.locate)
            mapButton("viewfinder", label: "显示完整路线", action: planner.fitRoute)
        }
    }
    private func mapButton(_ symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: symbol).font(.title3).frame(width: 44, height: 44) }
            .background(.regularMaterial, in: Circle()).shadow(color: .black.opacity(0.08), radius: 5)
            .accessibilityLabel(label)
    }
    private var controlCard: some View {
        VStack(alignment: .leading, spacing: 11) {
            if model.busy { busyControls }
            else if model.running { runningControls }
            else if model.needsRecovery { recoveryControls }
            else { planningControls }
        }
        .padding(15)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 19))
    }
    private var busyControls: some View {
        HStack {
            ProgressView(); Text(model.status).font(.subheadline); Spacer()
            if model.state == .connecting { Button("取消") { model.stop() } }
        }
    }
    private var recoveryControls: some View {
        VStack(alignment: .leading, spacing: 9) {
            Label("上次会话需要确认恢复", systemImage: "exclamationmark.circle").font(.headline)
            Text("若当前位置仍被模拟，请重试清除；若已在高德确认真实位置，可直接确认。")
                .font(.caption).foregroundStyle(.secondary)
            HStack {
                Button("重试清除") { model.stop() }.buttonStyle(.borderedProminent).disabled(!model.canStop)
                Button("已恢复真实位置") { model.confirmRecovery() }.buttonStyle(.bordered)
            }
        }
    }
    private var planningControls: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(model.route == nil ? "长按地图添加路线点" : model.routeName).font(.headline).lineLimit(1)
                    if let path = model.route {
                        Text("\(distanceText(path.length)) · 模拟约 \(Int(ceil(path.length / (model.speedKmh / 3.6) / 60))) 分钟")
                            .font(.caption).foregroundStyle(.secondary)
                    } else {
                        Text(planner.draft.waypoints.isEmpty ? "也可搜索地点，依次设置起点和终点" : "已选 \(planner.draft.waypoints.count) 个点，可拖动标记调整位置")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 6)
                if model.route != nil {
                    Button { saveName = model.routeName; showSave = true } label: { Image(systemName: "star") }
                        .accessibilityLabel("收藏路线")
                }
                if !planner.draft.waypoints.isEmpty { Button("点位") { showWaypoints = true }.font(.subheadline) }
            }
            if planner.draft.waypoints.count >= 2 {
                Picker("路线类型", selection: Binding(get: { planner.mode }, set: planner.changeMode)) {
                    ForEach(planner.availableModes, id: \.self) { Text($0.title).tag($0) }
                }.pickerStyle(.segmented).disabled(planner.planning)
            }
            if planner.planning {
                HStack {
                    ProgressView(); Text(planner.planningProgress).font(.caption); Spacer()
                    Button("取消", action: planner.cancelPlanning)
                }
            } else { planningActions }
            if let notice = planner.notice { Text(notice).font(.caption).foregroundStyle(.secondary) }
        }
    }
    private var planningActions: some View {
        HStack(spacing: 10) {
            if planner.draft.waypoints.count >= 2 {
                Button(model.route == nil ? "生成路线" : "重新生成", action: planner.generate)
                    .buttonStyle(.bordered).disabled(!planner.canGenerate)
            }
            if model.route != nil {
                Button { if model.pairingPresent { model.startRoute() } else { onSetup() } } label: {
                    Label(model.pairingPresent ? "开始模拟" : "配置定位连接", systemImage: "play.fill").frame(maxWidth: .infinity)
                }.buttonStyle(.borderedProminent)
            } else if planner.draft.waypoints.count == 1, let first = planner.draft.waypoints.first {
                Button { if model.pairingPresent { model.startFixed(at: first.point) } else { onSetup() } } label: {
                    Label("定点模拟", systemImage: "location.fill").frame(maxWidth: .infinity)
                }.buttonStyle(.borderedProminent)
            } else if planner.draft.waypoints.isEmpty {
                Button { showSearch = true } label: { Label("搜索并选点", systemImage: "magnifyingglass").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent)
            }
            if model.route != nil { Button("\(Int(model.speedKmh)) km/h") { showSpeed = true }.font(.subheadline.monospacedDigit()) }
        }
    }
    private var runningControls: some View {
        VStack(spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text(model.paused ? "已暂停" : model.state == .fixed ? "定点模拟中" : "路线模拟中").font(.headline)
                    Text("已运行 \(model.elapsedSeconds / 60):\(String(format: "%02d", model.elapsedSeconds % 60))")
                        .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                }
                Spacer(); speedControls
            }
            if model.state != .fixed { ProgressView(value: model.routeProgress) }
            HStack(spacing: 12) {
                if model.state != .fixed {
                    Button(action: model.togglePause) {
                        Label(model.paused ? "继续" : "暂停", systemImage: model.paused ? "play.fill" : "pause.fill").frame(maxWidth: .infinity)
                    }.buttonStyle(.borderedProminent)
                }
                Button(role: .destructive) { model.stop() } label: {
                    Label("停止", systemImage: "stop.fill").frame(maxWidth: .infinity)
                }.buttonStyle(.bordered)
            }
        }
    }
    private var speedControls: some View {
        HStack(spacing: 8) {
            Button { model.changeSpeed(model.speedKmh - 1) } label: { Image(systemName: "minus") }
                .buttonStyle(.bordered).buttonBorderShape(.circle).buttonRepeatBehavior(.enabled)
            Button { showSpeed = true } label: {
                VStack(spacing: 0) { Text("\(Int(model.speedKmh))").font(.title2.bold().monospacedDigit()); Text("km/h").font(.caption2) }
            }.buttonStyle(.plain)
            Button { model.changeSpeed(model.speedKmh + 1) } label: { Image(systemName: "plus") }
                .buttonStyle(.bordered).buttonBorderShape(.circle).buttonRepeatBehavior(.enabled)
        }
    }
    private func distanceText(_ meters: Double) -> String {
        meters < 1000 ? "\(Int(meters)) 米" : String(format: "%.2f 公里", meters / 1000)
    }
}
