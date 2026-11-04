import RouteCore
import SwiftUI

@MainActor
struct RouteLibraryScreen: View {
    @ObservedObject var model: ProbeModel
    @ObservedObject var planner: RoutePlannerModel
    @ObservedObject var library: RouteLibrary
    let onOpen: () -> Void
    let onImport: () -> Void
    @State private var deleting: SavedRoute?
    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button(action: onImport) { Label("导入 GPX / XML / 坐标文本", systemImage: "square.and.arrow.down") }
                        .disabled(!planner.editable)
                } footer: { Text("导入后可在地图预览、播放并收藏。文件上限 5 MB、50,000 点。") }
                Section("本机收藏") {
                    if library.routes.isEmpty {
                        ContentUnavailableView("还没有收藏路线", systemImage: "star", description: Text("在地图生成路线后，点星标保存。"))
                    }
                    ForEach(library.routes) { route in
                        Button {
                            planner.load(route); onOpen()
                        } label: {
                            VStack(alignment: .leading, spacing: 5) {
                                Text(route.name).font(.headline).foregroundStyle(.primary)
                                Text("\(route.mode.title) · \(route.geometry.count) 点 · \(route.createdAt.formatted(date: .abbreviated, time: .omitted))")
                                    .font(.caption).foregroundStyle(.secondary)
                            }.padding(.vertical, 4)
                        }.disabled(!planner.editable)
                        .swipeActions { Button("删除", role: .destructive) { deleting = route } }
                    }
                }
                if let warning = library.readWarning { Text(warning).font(.caption).foregroundStyle(.orange) }
                Section("示例") {
                    Button("载入巴黎测试环线") {
                        model.loadDemo(); planner.adoptImportedRoute(); onOpen()
                    }.disabled(!planner.editable)
                }
            }
            .navigationTitle("我的路线")
            .alert("删除这条收藏？", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
                Button("删除", role: .destructive) {
                    if let route = deleting {
                        do { try library.delete(route) } catch { planner.errorMessage = error.localizedDescription }
                    }
                    deleting = nil
                }
                Button("取消", role: .cancel) { deleting = nil }
            } message: { Text(deleting?.name ?? "") }
        }
    }
}
