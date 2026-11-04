import RouteCore
import SwiftUI

@MainActor
struct PlaceSearchSheet: View {
    @ObservedObject var planner: RoutePlannerModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack {
                        TextField("城市、地点或纬度,经度", text: $query)
                            .submitLabel(.search).onSubmit { planner.search(query) }
                        Button("搜索") { planner.search(query) }.disabled(query.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                } footer: { Text("例如：上海人民广场，或 31.2304,121.4737。也可在地图上长按选点。") }
                if planner.searching { ProgressView("正在搜索…") }
                ForEach(planner.searchResults) { result in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(result.title).font(.headline)
                        Text(result.subtitle).font(.caption).foregroundStyle(.secondary)
                        HStack {
                            chooseButton("设为起点", result, .start)
                            chooseButton("设为终点", result, .end).disabled(planner.draft.waypoints.isEmpty)
                            chooseButton("加途经点", result, .via).disabled(planner.draft.waypoints.count < 2 || planner.draft.waypoints.count >= 8)
                        }.buttonStyle(.bordered).font(.caption)
                    }.padding(.vertical, 5)
                }
                if !planner.searching, planner.searchResults.isEmpty, let notice = planner.notice {
                    Text(notice).font(.callout).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("搜索地点").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("完成") { dismiss() } } }
        }
    }
    private func chooseButton(_ title: String, _ result: PlaceResult, _ role: PlaceRole) -> some View {
        Button(title) { planner.choose(result, role: role); dismiss() }
    }
}

@MainActor
struct WaypointEditorSheet: View {
    @ObservedObject var planner: RoutePlannerModel
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(Array(planner.draft.waypoints.enumerated()), id: \.element.id) { index, point in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(planner.draft.role(at: index)).font(.caption.bold()).foregroundStyle(.blue)
                                Text(point.name).lineLimit(1)
                                Spacer()
                                Button { planner.remove(id: point.id) } label: { Image(systemName: "minus.circle.fill").foregroundStyle(.red) }
                                    .accessibilityLabel("删除此点")
                            }
                            HStack {
                                Text(String(format: "%.5f, %.5f", point.point.latitude, point.point.longitude))
                                    .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                                Spacer()
                                Button { planner.move(from: index, to: index - 1) } label: { Image(systemName: "arrow.up") }.disabled(index == 0)
                                Button { planner.move(from: index, to: index + 1) } label: { Image(systemName: "arrow.down") }.disabled(index == planner.draft.waypoints.count - 1)
                            }
                        }.buttonStyle(.borderless).padding(.vertical, 4)
                    }
                } footer: { Text("按顺序经过全部点，最多 8 个。可在地图上长按标记后拖动位置。改动点位后需要重新生成路线。") }
            }
            .disabled(!planner.editable)
            .navigationTitle("路线点位").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }
    }
}

@MainActor
struct SpeedSheet: View {
    @ObservedObject var model: ProbeModel
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("\(Int(model.speedKmh)) km/h").font(.largeTitle.bold()).frame(maxWidth: .infinity)
                    Slider(value: Binding(get: { model.speedKmh }, set: model.changeSpeed), in: 1...150, step: 1)
                    HStack {
                        ForEach([5, 15, 25, 60, 100], id: \.self) { speed in
                            Button("\(speed)") { model.changeSpeed(Double(speed)) }.frame(maxWidth: .infinity)
                        }
                    }.buttonStyle(.bordered)
                } header: { Text("模拟速度") } footer: { Text("播放中也可以调整，下一次位置更新时生效。") }
            }
            .navigationTitle("速度设置").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }.presentationDetents([.medium])
    }
}
