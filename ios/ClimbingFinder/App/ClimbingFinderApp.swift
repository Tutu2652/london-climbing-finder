import SwiftUI
import Combine
import SwiftData

@main
@MainActor
struct ClimbingFinderApp: App {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase
    private let minute = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .tint(.brand)
                .task { await model.refreshBankHolidays() }
                .onChange(of: scenePhase, initial: true) { _, phase in
                    if phase == .active { Task { await model.becameActive() } }
                }
                .onReceive(minute) { _ in model.followClock() }
        }
        .modelContainer(for: [ClimbSession.self, Climb.self])
    }
}

struct RootView: View {
    @AppStorage("tab") private var tab = "cheapest"
    @Environment(\.modelContext) private var context
    @State private var linkedSession: ClimbSession?

    var body: some View {
        TabView(selection: $tab) {
            stack { CheapestView() }
                .tabItem { Label("Cheapest", systemImage: "sterlingsign.circle") }.tag("cheapest")
            stack { MapTab() }
                .tabItem { Label("Map", systemImage: "map") }.tag("map")
            stack { GymsListView() }
                .tabItem { Label("Gyms", systemImage: "figure.climbing") }.tag("gyms")
            stack { LogView() }
                .tabItem { Label("Log", systemImage: "list.bullet.clipboard") }.tag("log")
            stack { MoreView() }
                .tabItem { Label("More", systemImage: "ellipsis.circle") }.tag("more")
        }
        .background(SessionActivityObserver())
        .onOpenURL { url in
            guard url.scheme == "climbfinder", url.host == "session",
                  let id = UUID(uuidString: url.lastPathComponent) else { return }
            tab = "log"
            let request = FetchDescriptor<ClimbSession>(predicate: #Predicate { $0.id == id })
            linkedSession = try? context.fetch(request).first
        }
        .sheet(item: $linkedSession) { session in
            NavigationStack {
                SessionScreen(session: session)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Close") { linkedSession = nil }
                        }
                    }
            }
        }
    }

    private func stack<V: View>(@ViewBuilder _ content: () -> V) -> some View {
        NavigationStack {
            content().navigationDestination(for: Gym.self) { GymDetailView(gym: $0) }
        }
    }
}
